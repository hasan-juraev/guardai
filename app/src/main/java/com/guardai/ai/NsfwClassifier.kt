package com.guardai.ai

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min

class NsfwClassifier(
    private val context: Context,
) {
    private var interpreter: Interpreter? = null
    private val inputBuffer = createInputBuffer()
    private var outputBuffer = Array(1) { Array(OUTPUT_CHANNELS) { FloatArray(OUTPUT_CANDIDATES) } }

    fun warmUp() {
        ensureInterpreter()
    }

    fun isReady(): Boolean = ensureInterpreter() != null

    fun classify(bitmap: Bitmap): DetectionSummary? {
        val activeInterpreter = ensureInterpreter() ?: return null
        val scaled = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)

        try {
            fillInputBuffer(scaled)
            outputBuffer = Array(1) { Array(OUTPUT_CHANNELS) { FloatArray(OUTPUT_CANDIDATES) } }
            activeInterpreter.run(inputBuffer, outputBuffer)
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }

        return summarizeDetections(outputBuffer[0])
    }

    private fun ensureInterpreter(): Interpreter? {
        interpreter?.let { return it }

        return try {
            val model = loadModelFile(context, MODEL_PATH)
            Interpreter(model, Interpreter.Options().apply {
                setNumThreads(4)
            }).also { created ->
                created.resizeInput(0, intArrayOf(1, INPUT_SIZE, INPUT_SIZE, NUM_CHANNELS))
                created.allocateTensors()
                interpreter = created
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun fillInputBuffer(bitmap: Bitmap) {
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        inputBuffer.rewind()
        for (pixel in pixels) {
            inputBuffer.putFloat(((pixel shr 16) and 0xFF) / 255f)
            inputBuffer.putFloat(((pixel shr 8) and 0xFF) / 255f)
            inputBuffer.putFloat((pixel and 0xFF) / 255f)
        }
    }

    private fun summarizeDetections(output: Array<FloatArray>): DetectionSummary {
        val rawDetections = mutableListOf<DetectionRecord>()

        for (candidateIndex in 0 until OUTPUT_CANDIDATES) {
            val centerX = output[0][candidateIndex]
            val centerY = output[1][candidateIndex]
            val width = output[2][candidateIndex]
            val height = output[3][candidateIndex]

            if (width <= 0f || height <= 0f) continue

            var bestLabel: DetectorLabel? = null
            var bestScore = 0f

            for (labelIndex in DetectorLabel.entries.indices) {
                val label = DetectorLabel.entries[labelIndex]
                val score = output[BOX_CHANNELS + labelIndex][candidateIndex]
                if (score > bestScore) {
                    bestScore = score
                    bestLabel = label
                }
            }

            val label = bestLabel ?: continue
            if (label !in ENFORCED_LABELS) continue
            if (bestScore < BASE_SCORE_THRESHOLD) continue

            val left = ((centerX - (width / 2f)) / INPUT_SIZE).coerceIn(0f, 1f)
            val top = ((centerY - (height / 2f)) / INPUT_SIZE).coerceIn(0f, 1f)
            val right = ((centerX + (width / 2f)) / INPUT_SIZE).coerceIn(0f, 1f)
            val bottom = ((centerY + (height / 2f)) / INPUT_SIZE).coerceIn(0f, 1f)
            val normalizedWidth = (right - left).coerceAtLeast(0f)
            val normalizedHeight = (bottom - top).coerceAtLeast(0f)
            val area = normalizedWidth * normalizedHeight

            val minScore = MIN_SCORE_BY_LABEL[label] ?: BASE_SCORE_THRESHOLD
            val minArea = MIN_AREA_BY_LABEL[label] ?: DEFAULT_MIN_AREA
            if (bestScore < minScore || area < minArea) continue

            rawDetections += DetectionRecord(
                label = label,
                score = bestScore,
                left = left,
                top = top,
                right = right,
                bottom = bottom,
                area = area,
            )
        }

        val finalDetections = applyNms(rawDetections)
        val strongest = finalDetections.maxByOrNull { it.score }
        val matchedLabels = finalDetections.map { it.label }.distinct()
        val strongestLock = finalDetections
            .filter { it.label in LOCK_LABELS }
            .maxOfOrNull { it.score } ?: 0f
        val strongestBlur = finalDetections
            .filter { it.label in BLUR_LABELS }
            .maxOfOrNull { it.score } ?: 0f

        val action = when {
            strongestLock >= LOCK_TRIGGER_THRESHOLD -> DetectionAction.LOCK
            strongestBlur >= BLUR_TRIGGER_THRESHOLD -> DetectionAction.BLUR
            else -> DetectionAction.NONE
        }

        return DetectionSummary(
            action = action,
            confidence = strongest?.score ?: 0f,
            topLabel = strongest?.label ?: DetectorLabel.FACE_FEMALE,
            matchedLabels = matchedLabels,
            detections = finalDetections,
        )
    }

    private fun applyNms(detections: List<DetectionRecord>): List<DetectionRecord> {
        if (detections.isEmpty()) return emptyList()

        val kept = mutableListOf<DetectionRecord>()
        val grouped = detections.groupBy { it.label }

        grouped.values.forEach { group ->
            val sorted = group.sortedByDescending { it.score }.toMutableList()
            while (sorted.isNotEmpty()) {
                val current = sorted.removeAt(0)
                kept += current
                sorted.removeAll { candidate -> current.iou(candidate) >= NMS_IOU_THRESHOLD }
            }
        }

        return kept.sortedByDescending { it.score }
    }

    private fun createInputBuffer(): ByteBuffer {
        return ByteBuffer.allocateDirect(
            INPUT_SIZE * INPUT_SIZE * NUM_CHANNELS * FLOAT_BYTES,
        ).apply {
            order(ByteOrder.nativeOrder())
        }
    }

    companion object {
        const val MODEL_PATH = "models/320n_float32.tflite"
        const val INPUT_SIZE = 320
        const val NUM_CHANNELS = 3
        private const val FLOAT_BYTES = 4
        private const val BOX_CHANNELS = 4
        private const val OUTPUT_CHANNELS = 22
        private const val OUTPUT_CANDIDATES = 2100
        private const val BASE_SCORE_THRESHOLD = 0.18f
        private const val DEFAULT_MIN_AREA = 0.015f
        private const val NMS_IOU_THRESHOLD = 0.45f

        const val BLUR_TRIGGER_THRESHOLD = 0.48f
        const val LOCK_TRIGGER_THRESHOLD = 0.52f

        val BLUR_LABELS = setOf(
            DetectorLabel.ARMPITS_EXPOSED,
            DetectorLabel.FEET_EXPOSED,
        )

        val LOCK_LABELS = setOf(
            DetectorLabel.BELLY_EXPOSED,
            DetectorLabel.BUTTOCKS_EXPOSED,
            DetectorLabel.FEMALE_BREAST_EXPOSED,
            DetectorLabel.FEMALE_GENITALIA_EXPOSED,
            DetectorLabel.MALE_BREAST_EXPOSED,
            DetectorLabel.MALE_GENITALIA_EXPOSED,
            DetectorLabel.ANUS_EXPOSED,
        )

        private val ENFORCED_LABELS = BLUR_LABELS + LOCK_LABELS

        private val MIN_SCORE_BY_LABEL = mapOf(
            DetectorLabel.ARMPITS_EXPOSED to 0.50f,
            DetectorLabel.FEET_EXPOSED to 0.55f,
            DetectorLabel.BELLY_EXPOSED to 0.52f,
            DetectorLabel.BUTTOCKS_EXPOSED to 0.48f,
            DetectorLabel.FEMALE_BREAST_EXPOSED to 0.46f,
            DetectorLabel.MALE_BREAST_EXPOSED to 0.58f,
            DetectorLabel.FEMALE_GENITALIA_EXPOSED to 0.44f,
            DetectorLabel.MALE_GENITALIA_EXPOSED to 0.44f,
            DetectorLabel.ANUS_EXPOSED to 0.44f,
        )

        private val MIN_AREA_BY_LABEL = mapOf(
            DetectorLabel.ARMPITS_EXPOSED to 0.02f,
            DetectorLabel.FEET_EXPOSED to 0.03f,
            DetectorLabel.BELLY_EXPOSED to 0.08f,
            DetectorLabel.BUTTOCKS_EXPOSED to 0.035f,
            DetectorLabel.FEMALE_BREAST_EXPOSED to 0.02f,
            DetectorLabel.MALE_BREAST_EXPOSED to 0.03f,
            DetectorLabel.FEMALE_GENITALIA_EXPOSED to 0.008f,
            DetectorLabel.MALE_GENITALIA_EXPOSED to 0.008f,
            DetectorLabel.ANUS_EXPOSED to 0.008f,
        )

        private fun loadModelFile(context: Context, assetPath: String): MappedByteBuffer {
            val descriptor = context.assets.openFd(assetPath)
            FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
                return channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    descriptor.startOffset,
                    descriptor.declaredLength,
                )
            }
        }
    }
}

data class DetectionSummary(
    val action: DetectionAction,
    val confidence: Float,
    val topLabel: DetectorLabel,
    val matchedLabels: List<DetectorLabel>,
    val detections: List<DetectionRecord>,
)

data class DetectionRecord(
    val label: DetectorLabel,
    val score: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val area: Float,
) {
    fun iou(other: DetectionRecord): Float {
        val interLeft = max(left, other.left)
        val interTop = max(top, other.top)
        val interRight = min(right, other.right)
        val interBottom = min(bottom, other.bottom)
        val interWidth = (interRight - interLeft).coerceAtLeast(0f)
        val interHeight = (interBottom - interTop).coerceAtLeast(0f)
        val intersection = interWidth * interHeight
        if (intersection <= 0f) return 0f
        val union = area + other.area - intersection
        return if (union <= 0f) 0f else intersection / union
    }
}

enum class DetectionAction {
    NONE,
    BLUR,
    LOCK,
}

enum class DetectorLabel {
    FEMALE_GENITALIA_COVERED,
    FACE_FEMALE,
    BUTTOCKS_EXPOSED,
    FEMALE_BREAST_EXPOSED,
    FEMALE_GENITALIA_EXPOSED,
    MALE_BREAST_EXPOSED,
    ANUS_EXPOSED,
    FEET_EXPOSED,
    BELLY_COVERED,
    FEET_COVERED,
    ARMPITS_COVERED,
    ARMPITS_EXPOSED,
    FACE_MALE,
    BELLY_EXPOSED,
    MALE_GENITALIA_EXPOSED,
    ANUS_COVERED,
    FEMALE_BREAST_COVERED,
    BUTTOCKS_COVERED,
}

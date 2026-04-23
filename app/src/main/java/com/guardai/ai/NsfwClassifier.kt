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

class NsfwClassifier(
    private val context: Context,
) {
    private var interpreter: Interpreter? = null
    private var inputBuffer = createInputBuffer()
    private var outputBuffer = Array(1) { Array(OUTPUT_CHANNELS) { FloatArray(0) } }

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
            if (scaled !== bitmap) {
                scaled.recycle()
            }
        }

        return summarizeDetections(outputBuffer[0])
    }

    private fun ensureInterpreter(): Interpreter? {
        interpreter?.let { return it }

        return try {
            val model = loadModelFile(context, MODEL_PATH)
            val created = Interpreter(model, Interpreter.Options().apply {
                setNumThreads(4)
            })

            created.resizeInput(0, intArrayOf(1, INPUT_SIZE, INPUT_SIZE, NUM_CHANNELS))
            created.allocateTensors()
            interpreter = created
            created
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
        var strongestOverall = 0f
        var strongestBlur = 0f
        var strongestLock = 0f
        var topScore = 0f
        var topLabel = DetectorLabel.FACE_FEMALE
        val matchedLabels = linkedSetOf<DetectorLabel>()

        for (candidateIndex in 0 until OUTPUT_CANDIDATES) {
            for (labelIndex in DetectorLabel.entries.indices) {
                val score = output[BOX_CHANNELS + labelIndex][candidateIndex]
                val label = DetectorLabel.entries[labelIndex]
                strongestOverall = max(strongestOverall, score)

                if (score > topScore) {
                    topScore = score
                    topLabel = label
                }

                if (label in LOCK_LABELS) {
                    strongestLock = max(strongestLock, score)
                    if (score >= LOCK_THRESHOLD) matchedLabels += label
                } else if (label in BLUR_LABELS) {
                    strongestBlur = max(strongestBlur, score)
                    if (score >= BLUR_THRESHOLD) matchedLabels += label
                }
            }
        }

        val action = when {
            strongestLock >= LOCK_THRESHOLD -> DetectionAction.LOCK
            strongestBlur >= BLUR_THRESHOLD -> DetectionAction.BLUR
            else -> DetectionAction.NONE
        }

        val confidence = when (action) {
            DetectionAction.LOCK -> strongestLock
            DetectionAction.BLUR -> strongestBlur
            DetectionAction.NONE -> strongestOverall
        }

        return DetectionSummary(
            action = action,
            confidence = confidence,
            topLabel = topLabel,
            matchedLabels = matchedLabels.toList(),
        )
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
        const val BLUR_THRESHOLD = 0.40f
        const val LOCK_THRESHOLD = 0.45f

        private val BLUR_LABELS = setOf(
            DetectorLabel.ARMPITS_EXPOSED,
            DetectorLabel.FEET_EXPOSED,
        )

        private val LOCK_LABELS = setOf(
            DetectorLabel.BELLY_EXPOSED,
            DetectorLabel.BUTTOCKS_EXPOSED,
            DetectorLabel.FEMALE_BREAST_EXPOSED,
            DetectorLabel.FEMALE_GENITALIA_EXPOSED,
            DetectorLabel.MALE_BREAST_EXPOSED,
            DetectorLabel.MALE_GENITALIA_EXPOSED,
            DetectorLabel.ANUS_EXPOSED,
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
)

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

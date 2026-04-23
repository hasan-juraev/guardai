package com.guardai.ai

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class NsfwClassifier(
    private val context: Context,
) {
    private var interpreter: Interpreter? = null
    private val inputBuffer = ByteBuffer.allocateDirect(
        INPUT_SIZE * INPUT_SIZE * NUM_CHANNELS * FLOAT_BYTES,
    ).apply {
        order(ByteOrder.nativeOrder())
    }

    fun warmUp() {
        ensureInterpreter()
    }

    fun isReady(): Boolean = ensureInterpreter() != null

    fun classify(bitmap: Bitmap): ClassificationResult? {
        val activeInterpreter = ensureInterpreter() ?: return null
        fillInputBuffer(bitmap)

        val output = Array(1) { FloatArray(NUM_CLASSES) }
        activeInterpreter.run(inputBuffer, output)

        val scores = output.first()
        val maxIndex = scores.indices.maxByOrNull { scores[it] } ?: return null
        return ClassificationResult(
            label = NsfwLabel.fromIndex(maxIndex),
            confidence = scores[maxIndex],
            rawScores = scores.copyOf(),
        )
    }

    private fun ensureInterpreter(): Interpreter? {
        interpreter?.let { return it }
        return try {
            val model = loadModelFile(context, MODEL_PATH)
            Interpreter(model, Interpreter.Options().apply {
                setNumThreads(4)
            }).also { interpreter = it }
        } catch (_: Exception) {
            null
        }
    }

    private fun fillInputBuffer(bitmap: Bitmap) {
        val scaled = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        scaled.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        inputBuffer.rewind()
        for (pixel in pixels) {
            inputBuffer.putFloat(((pixel shr 16) and 0xFF) / 255f)
            inputBuffer.putFloat(((pixel shr 8) and 0xFF) / 255f)
            inputBuffer.putFloat((pixel and 0xFF) / 255f)
        }

        if (scaled !== bitmap) {
            scaled.recycle()
        }
    }

    companion object {
        const val MODEL_PATH = "models/nudenet_int8.tflite"
        const val INPUT_SIZE = 224
        const val NUM_CHANNELS = 3
        const val NUM_CLASSES = 5
        private const val FLOAT_BYTES = 4

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

data class ClassificationResult(
    val label: NsfwLabel,
    val confidence: Float,
    val rawScores: FloatArray,
)

enum class NsfwLabel(val index: Int) {
    NEUTRAL(0),
    DRAWINGS(1),
    SEXY(2),
    PORN(3),
    HENTAI(4),
    ;

    companion object {
        fun fromIndex(index: Int): NsfwLabel {
            return entries.firstOrNull { it.index == index } ?: NEUTRAL
        }
    }
}

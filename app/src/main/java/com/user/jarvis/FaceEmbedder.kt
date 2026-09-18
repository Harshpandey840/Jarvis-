package com.user.jarvis

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.sqrt

class FaceEmbedder(context: Context) {

    private var interpreter: Interpreter? = null
    private var inputWidth = 112
    private var inputHeight = 112
    private var isDualInput = false
    var lastError: String? = null
        private set

    init {
        try {
            val assetFileDescriptor = context.assets.openFd("MobileFaceNet.tflite")
            val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

            val interp = Interpreter(modelBuffer)
            interpreter = interp

            isDualInput = interp.inputTensorCount >= 2
            val inputShape = interp.getInputTensor(0).shape()
            if (inputShape.size >= 3) {
                inputHeight = inputShape[1]
                inputWidth = inputShape[2]
            }
        } catch (e: Exception) {
            lastError = "Model load failed: ${e.message}"
        }
    }

    fun compare(referenceFace: Bitmap, probeFace: Bitmap): Float? {
        val interp = interpreter ?: return null
        return try {
            if (isDualInput) {
                compareDualInput(interp, referenceFace, probeFace)
            } else {
                val embedding1 = embed(interp, referenceFace) ?: return null
                val embedding2 = embed(interp, probeFace) ?: return null
                cosineSimilarity(embedding1, embedding2)
            }
        } catch (e: Exception) {
            lastError = "Compare failed: ${e.message}"
            null
        }
    }

    private fun embed(interp: Interpreter, bitmap: Bitmap): FloatArray? {
        val input = bitmapToByteBuffer(bitmap)
        val outputShape = interp.getOutputTensor(0).shape()
        val outputSize = if (outputShape.size >= 2) outputShape[1] else outputShape.last()
        val output = Array(1) { FloatArray(outputSize) }
        interp.run(input, output)
        return output[0]
    }

    private fun compareDualInput(interp: Interpreter, ref: Bitmap, probe: Bitmap): Float {
        val input1 = bitmapToByteBuffer(ref)
        val input2 = bitmapToByteBuffer(probe)
        val outputShape = interp.getOutputTensor(0).shape()
        val outputSize = if (outputShape.isNotEmpty()) outputShape.last() else 1
        val output = Array(1) { FloatArray(outputSize) }
        interp.runForMultipleInputsOutputs(arrayOf(input1, input2), mapOf(0 to output))
        return output[0][0]
    }

    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val resized = Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, true)
        val buffer = ByteBuffer.allocateDirect(4 * inputWidth * inputHeight * 3)
        buffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(inputWidth * inputHeight)
        resized.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)
        for (pixel in pixels) {
            val r = (pixel shr 16 and 0xFF)
            val g = (pixel shr 8 and 0xFF)
            val b = (pixel and 0xFF)
            buffer.putFloat((r - 127.5f) / 127.5f)
            buffer.putFloat((g - 127.5f) / 127.5f)
            buffer.putFloat((b - 127.5f) / 127.5f)
        }
        buffer.rewind()
        return buffer
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom == 0f) 0f else (dot / denom + 1f) / 2f
    }

    fun close() {
        interpreter?.close()
    }
}

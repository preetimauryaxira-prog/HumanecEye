package com.xira.humanec_eye_app.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.core.graphics.scale
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.channels.FileChannel
import kotlin.math.abs
import kotlin.math.sqrt
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AntiSpoofingAnalyzer(private val context: Context) {
    private var interpreter: Interpreter? = null
    private val inputWidth = 256
    private val inputHeight = 256
    private val outputSize = 8

    companion object {
        const val ROUTE_INDEX = 6
    }

    @Volatile
    private var isProcessing = false

    init {
        loadModel()
    }

    private fun loadModel() {
        try {
            val assetFileDescriptor = context.assets.openFd("FaceAntiSpoofing.tflite")
            val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = Interpreter(modelBuffer, options)
        } catch (e: Exception) {
            throw RuntimeException("Error loading anti-spoofing model", e)
        }
    }

    @Synchronized
    fun deSpoofing(bitmap: Bitmap): Float? {
        if (isProcessing) return null
        isProcessing = true
        try {
            val scaledBitmap = bitmap.scale(inputWidth, inputHeight)
            val normalizedBuffer = normalizeImage(scaledBitmap)

            val output = Array(1) { FloatArray(outputSize) }

            // Run inference
            interpreter?.run(normalizedBuffer, output)

            // Yahan hum poore 8 numbers ka array print kar rahe hain
            Log.e("AntiSpoofingCheck", "FULL ARRAY OUTPUT: ${output[0].contentToString()}")

            return output[0][ROUTE_INDEX]
        } catch (e: Exception) {
            Log.e("AntiSpoofing", "Error in deSpoofing", e)
            return null
        } finally {
            isProcessing = false
        }
    }

    private fun normalizeImage(bitmap: Bitmap): ByteBuffer {
        val h = bitmap.height // 256
        val w = bitmap.width  // 256

        // Allocate exactly 786,432 bytes: 1 batch * 256 height * 256 width * 3 channels (RGB) * 4 bytes/float
        val byteBuffer = ByteBuffer.allocateDirect(1 * h * w * 3 * 4)
        byteBuffer.order(java.nio.ByteOrder.nativeOrder())

        val imageStd = 256f
        val pixels = IntArray(h * w)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        for (pixel in pixels) {
            // Extract only the RGB channels and normalize them
            val r = ((pixel shr 16) and 0xFF) / imageStd
            val g = ((pixel shr 8) and 0xFF) / imageStd
            val b = (pixel and 0xFF) / imageStd

            // Put them in the buffer
            byteBuffer.putFloat(r)
            byteBuffer.putFloat(g)
            byteBuffer.putFloat(b)
        }

        return byteBuffer
    }

    @Synchronized
    fun isProcessing(): Boolean {
        return isProcessing
    }
}
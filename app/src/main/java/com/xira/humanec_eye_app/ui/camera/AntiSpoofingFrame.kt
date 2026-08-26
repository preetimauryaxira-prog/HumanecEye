//package com.xira.humanec_eye_app.ui.camera
//
//import android.content.Context
//import android.graphics.Bitmap
//import android.graphics.Color
//import android.util.Log
//import androidx.core.graphics.scale
//import org.tensorflow.lite.Interpreter
//import java.io.FileInputStream
//import java.nio.channels.FileChannel
//import kotlin.math.abs
//import kotlin.math.sqrt
//
//class AntiSpoofingAnalyzer(private val context: Context) {
//    private var interpreter: Interpreter? = null
//    private val inputWidth = 256
//    private val inputHeight = 256
//    private val outputSize = 8
//
//    companion object {
//        const val ROUTE_INDEX = 6
//    }
//
//    @Volatile
//    private var isProcessing = false
//
//    init {
//        loadModel()
//    }
//
//    private fun loadModel() {
//        try {
//            val assetFileDescriptor = context.assets.openFd("FaceAntiSpoofing.tflite")
//            val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
//            val fileChannel = inputStream.channel
//            val startOffset = assetFileDescriptor.startOffset
//            val declaredLength = assetFileDescriptor.declaredLength
//            val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
//
//            val options = Interpreter.Options().apply {
//                setNumThreads(4)
//            }
//            interpreter = Interpreter(modelBuffer, options)
//        } catch (e: Exception) {
//            throw RuntimeException("Error loading anti-spoofing model", e)
//        }
//    }
//
//    @Synchronized
//    fun deSpoofing(bitmap: Bitmap): Float? {
//        if (isProcessing) {
//            Log.d("AntiSpoofing", "Skipping frame - already processing")
//            return null
//        }
//
//        isProcessing = true
//        try {
//            val scaledBitmap = bitmap.scale(inputWidth, inputHeight)
//            val normalizedImage = normalizeImage(scaledBitmap)
//
//            // Prepare input and output arrays
//            val input = arrayOf(normalizedImage)
//            val output = Array(1) { FloatArray(outputSize) }
//
//            // Run inference
//            interpreter?.run(input, output)
//
//            // Return the score from the specified route index
//            return output[0][ROUTE_INDEX]
//        } catch (e: Exception) {
//            Log.e("AntiSpoofing", "Error in deSpoofing", e)
//            return null
//        } finally {
//            isProcessing = false
//        }
//    }
//
//    private fun normalizeImage(bitmap: Bitmap): Array<Array<FloatArray>> {
//        val h = bitmap.height
//        val w = bitmap.width
//
//        // 1. Pre-allocate the 3D array once
//        val floatValues = Array(h) { Array(w) { FloatArray(6) } }
//        val imageStd = 256f
//        val pixels = IntArray(h * w)
//        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
//
//        // 2. Pre-allocate the HSV array outside the loop to prevent GC thrashing
//        val hsv = FloatArray(3)
//
//        var pixelIndex = 0
//        for (i in 0 until h) {
//            for (j in 0 until w) {
//                val `val` = pixels[pixelIndex++]
//
//                // Populate the pre-allocated HSV array
//                Color.colorToHSV(`val`, hsv)
//
//                // 3. Reuse the existing inner array instead of creating a new one
//                val targetArray = floatValues[i][j]
//                targetArray[0] = hsv[0] / 360f
//                targetArray[1] = hsv[1]
//                targetArray[2] = hsv[2]
//                targetArray[3] = ((`val` shr 16) and 0xFF) / imageStd
//                targetArray[4] = ((`val` shr 8) and 0xFF) / imageStd
//                targetArray[5] = (`val` and 0xFF) / imageStd
//            }
//        }
//        return floatValues
//    }
//
//    @Synchronized
//    fun isProcessing(): Boolean {
//        return isProcessing
//    }
//}
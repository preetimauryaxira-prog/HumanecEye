package com.xira.humanec_eye_app.ui.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.xira.humanec_eye_app.ui.camera.model.FaceNetModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.graphics.createBitmap
import com.xira.humanec_eye_app.model.FaceData
import com.xira.humanec_eye_app.utils.ToastUtils
import java.io.ByteArrayOutputStream
import kotlin.math.pow
import kotlin.math.sqrt

class RegisterFrameAnalyser(
    private var context: Context,
    private var boundingBoxOverlay: BoundingBoxOverlay,
    private var model: FaceNetModel
) : ImageAnalysis.Analyzer {
//    private val antiSpoofingAnalyzer = AntiSpoofingAnalyzer(context)

    private val realTimeOpts = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setMinFaceSize(0.9f)
        .build()

    private var lastDetectedName: String? = null
    private var confirmationCount = 0
    private var unknownConfirmationCount = 0  // Track consecutive "Unknown" frames
    private val requiredConfirmations = 5
    private var isFirstCompareComplete = false  // Block registration until enough frames compared

    private val detector = FaceDetection.getClient(realTimeOpts)
    private var isProcessing = false
    private var fileAccess: FileAccess = FileAccess(context)
    private var currentFaceBitmap: Bitmap? = null
    private var t1: Long = 0L
    private val metricToBeUsed = "l2"
    private var faceList = ArrayList<Pair<String, FloatArray>>()
    private var isRegister = "Unknown"
    private val nameScoreHashmap = HashMap<String, ArrayList<Float>>()
    private val MIN_BRIGHTNESS_THRESHOLD = 80
    private val MAX_BRIGHTNESS_THRESHOLD = 200
    private var lastProcessTime = 0L
    private val minProcessInterval = 250

    init {
        loadFace(fileAccess.loadRegisteredFaces())

    }

    fun loadFace(faces: List<FaceData>) {
        for (faceData in faces) {
            val name = faceData.name
            val code = faceData.code
            val embedding = faceData.embedding
            faceList.add(Pair("$name-$code", embedding))
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(image: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastProcessTime < minProcessInterval) {
            image.close()
            return
        }
        lastProcessTime = currentTime
        if (isProcessing) {
            image.close()
            return
        } else {
            isProcessing = true

            val cameraXImage = image.image!!
            var frameBitmap = createBitmap(cameraXImage.width, cameraXImage.height)
            frameBitmap.copyPixelsFromBuffer(image.planes[0].buffer)
            frameBitmap = BitmapUtils.Companion.rotateBitmap(
                frameBitmap,
                image.imageInfo.rotationDegrees.toFloat()
            )

            if (!boundingBoxOverlay.areDimsInit) {
                boundingBoxOverlay.frameHeight = frameBitmap.height
                boundingBoxOverlay.frameWidth = frameBitmap.width
            }

            val inputImage = InputImage.fromBitmap(frameBitmap, 0)
            detector.process(inputImage)
                .addOnSuccessListener { faces ->
                    CoroutineScope(Dispatchers.Default).launch {
                        if (isUserWithinDistance(faces, frameBitmap)) {
                            if (isFaceCentered(faces, frameBitmap)) {
                                runModel(faces, frameBitmap)

                            } else {
                                withContext(Dispatchers.Main) {
                                    isRegister = "Unknown"
                                    boundingBoxOverlay.faceBoundingBoxes = ArrayList<Prediction>()
                                    boundingBoxOverlay.invalidate()
                                    isProcessing = false
                                    currentFaceBitmap = null
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                isRegister = "Unknown"
                                boundingBoxOverlay.faceBoundingBoxes = ArrayList<Prediction>()
                                boundingBoxOverlay.invalidate()
                                isProcessing = false
                                currentFaceBitmap = null
                            }
                        }
                    }
                }
                .addOnCompleteListener {
                    image.close()
                    isProcessing = false
                    currentFaceBitmap = null
                }
        }
    }

    private fun isUserWithinDistance(faces: List<Face>, frameBitmap: Bitmap): Boolean {
        if (faces.isEmpty()) return false
        val nearestFace = faces[0]
        val faceWidthPixels = nearestFace.boundingBox.width()
        val focalLengthPixels = 1000f
        val actualFaceWidthMeters = 0.15f
        val estimatedDistanceMeters = (focalLengthPixels * actualFaceWidthMeters) / faceWidthPixels
        return estimatedDistanceMeters <= 1f
    }

    private fun isFaceCentered(faces: List<Face>, frameBitmap: Bitmap): Boolean {
        if (faces.isEmpty()) return false
        val face = faces[0]
        val faceCenterX = face.boundingBox.centerX()
        val faceCenterY = face.boundingBox.centerY()
        val frameCenterX = frameBitmap.width / 2
        val frameCenterY = frameBitmap.height / 2

        // Allow some tolerance for centering
        val toleranceX = frameBitmap.width * 0.1
        val toleranceY = frameBitmap.height * 0.1

        return (faceCenterX.toDouble() in (frameCenterX - toleranceX)..(frameCenterX + toleranceX) &&
                (faceCenterY.toDouble() in (frameCenterY - toleranceY)..(frameCenterY + toleranceY)))
    }

    private suspend fun runModel(faces: List<Face>, cameraFrameBitmap: Bitmap) {
        withContext(Dispatchers.Default) {
            val nearestList = sortFacesByProximity(faces)
            if (isLightingValid(nearestList, cameraFrameBitmap)) {
                notRegisterFaceDetect(nearestList, cameraFrameBitmap)
            } else {
                withContext(Dispatchers.Main) {
                    ToastUtils.showErrorToast(
                        context,
                        "Poor lighting conditions. Please adjust lighting.",
                        true
                    )
                    isRegister = "Unknown"
                    boundingBoxOverlay.faceBoundingBoxes = ArrayList<Prediction>()
                    boundingBoxOverlay.invalidate()
                    isProcessing = false
                }
            }
        }
    }

    private fun isLightingValid(faces: List<Face>, frameBitmap: Bitmap): Boolean {
        if (faces.isEmpty()) return false
        val face = faces[0]
        val faceBitmap = BitmapUtils.Companion.cropRectFromBitmap(frameBitmap, face.boundingBox)
        val brightness = calculateBrightness(faceBitmap)
        return brightness in MIN_BRIGHTNESS_THRESHOLD..MAX_BRIGHTNESS_THRESHOLD
    }

    private fun calculateBrightness(bitmap: Bitmap): Int {
        var brightness = 0
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            brightness += (r + g + b) / 3
        }

        return brightness / (width * height)
    }

    private fun sortFacesByProximity(faces: List<Face>): List<Face> {
        val sortedFaces = faces.toMutableList()
        sortedFaces.sortWith(compareByDescending { face ->
            face.boundingBox.width() * face.boundingBox.height()
        })
        return sortedFaces
    }

    suspend fun notRegisterFaceDetect(faces: List<Face>, cameraFrameBitmap: Bitmap) {
        val predictions = ArrayList<Prediction>()
        if (faces.isEmpty()) {
            withContext(Dispatchers.Main) {
                boundingBoxOverlay.faceBoundingBoxes = predictions
                boundingBoxOverlay.invalidate()
                isProcessing = false
            }
            return
        }

        try {
            val face = faces[0]
            currentFaceBitmap =
                BitmapUtils.Companion.cropRectFromBitmap(cameraFrameBitmap, face.boundingBox)

            // Only proceed with registration if not spoof
            val subject = model.getFaceEmbedding(currentFaceBitmap!!)

            if (faceList.isNotEmpty()) {
                for (i in faceList.indices) {
                    val similarityScore = if (metricToBeUsed == "cosine") {
                        cosineSimilarity(subject, faceList[i].second)
                    } else {
                        L2Norm(subject, faceList[i].second)
                    }

                    nameScoreHashmap[faceList[i].first] = arrayListOf(similarityScore)
                }

                val avgScores = nameScoreHashmap.values.map { it.toFloatArray().average() }
                val names = nameScoreHashmap.keys.toTypedArray()
                nameScoreHashmap.clear()

                val bestScoreUserName = if (metricToBeUsed == "cosine") {
                    if (avgScores.maxOrNull()!! > model.model.cosineThreshold) {
                        names[avgScores.indexOf(avgScores.maxOrNull()!!)]
                    } else {
                        "Unknown"
                    }
                } else {
                    if (avgScores.minOrNull()!! > model.model.l2Threshold) {
                        "Unknown"
                    } else {
                        names[avgScores.indexOf(avgScores.minOrNull()!!)]
                    }
                }

                // Update confirmation logic
                if (bestScoreUserName != "Unknown") {
                    // Face potentially matches a registered face
                    unknownConfirmationCount = 0  // Reset unknown counter
                    if (lastDetectedName == bestScoreUserName) {
                        confirmationCount++
                        Log.d(
                            "Registration",
                            "Confirmation count for $bestScoreUserName: $confirmationCount"
                        )

                        if (confirmationCount >= requiredConfirmations) {
                            isRegister = bestScoreUserName
                            isFirstCompareComplete = true  // Only allow after enough confirmations
                        }
                    } else {
                        lastDetectedName = bestScoreUserName
                        confirmationCount = 1
                        isRegister = "Unknown"
                        isFirstCompareComplete = false  // Reset - new face detected
                    }
                } else {
                    // Face is unknown (not registered)
                    lastDetectedName = null
                    confirmationCount = 0
                    unknownConfirmationCount++
                    isRegister = "Unknown"
                    
                    // Only allow registration after confirming face is truly unknown
                    if (unknownConfirmationCount >= requiredConfirmations) {
                        isFirstCompareComplete = true
                    }
                }

                predictions.add(Prediction(face.boundingBox, ""))
            } else {
                // No faces in faceList - first registration ever
                unknownConfirmationCount++
                isRegister = "Unknown"
                if (unknownConfirmationCount >= requiredConfirmations) {
                    isFirstCompareComplete = true
                }
                predictions.add(Prediction(face.boundingBox, ""))
            }
        } catch (e: Exception) {
            Log.e("Model", "Exception in RecogniseFrameAnalyser: ${e.message}")
        }
        Log.e("Performance", "Inference time -> ${System.currentTimeMillis() - t1}")


        withContext(Dispatchers.Main) {
            boundingBoxOverlay.faceBoundingBoxes = predictions
            boundingBoxOverlay.invalidate()
            isProcessing = false
        }
    }

    private fun L2Norm(x1: FloatArray, x2: FloatArray): Float {
        return sqrt(x1.mapIndexed { i, xi -> (xi - x2[i]).pow(2) }.sum())
    }

    private fun cosineSimilarity(x1: FloatArray, x2: FloatArray): Float {
        val mag1 = sqrt(x1.map { it * it }.sum())
        val mag2 = sqrt(x2.map { it * it }.sum())
        val dot = x1.mapIndexed { i, xi -> xi * x2[i] }.sum()
        return dot / (mag1 * mag2)
    }

    fun getCurrentFaceEmbedding(): FloatArray? {
        // Block until first face comparison is complete
        if(!isFirstCompareComplete){
            ToastUtils.showErrorToast(context, "Please wait, loading...",true)
            return null
        }

        if(isRegister!="Unknown"){
            ToastUtils.showErrorToast(context, "Face Already Register By $isRegister",true)
            return null
        }

        if(currentFaceBitmap==null){
            ToastUtils.showErrorToast(context,"Please Ensure Face Near to Camera (1 Meter) & Center an ensure green frame Showing on Face",true)
            return null
        }
        return currentFaceBitmap?.let { model.getFaceEmbedding(it) }

    }

    fun getCurrentFaceImageAsBase64(): String? {
        return currentFaceBitmap?.let { bitmap ->
            val byteArrayOutputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)
            val byteArray = byteArrayOutputStream.toByteArray()
            Base64.encodeToString(byteArray, Base64.DEFAULT)
        }
    }

}
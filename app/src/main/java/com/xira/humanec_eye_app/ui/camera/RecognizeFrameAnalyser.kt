package com.xira.humanec_eye_app.ui.camera

import MarkAttendanceDialog
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.xira.humanec_eye_app.FirebaseLogger
import com.xira.humanec_eye_app.model.Attendance
import com.xira.humanec_eye_app.model.FaceData
import com.xira.humanec_eye_app.ui.camera.autoSync.AttendanceService
import com.xira.humanec_eye_app.ui.camera.model.FaceNetModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.pow
import kotlin.math.sqrt

class RecogniseFrameAnalyser(
    private val context: Context,
    private var boundingBoxOverlay: BoundingBoxOverlay,
    private var model: FaceNetModel
) : ImageAnalysis.Analyzer {
    private val antiSpoofingAnalyzer = AntiSpoofingAnalyzer(context)

    private val realTimeOpts = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE) // Disable contours
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE) // Disable classifications
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE) // Disable landmarks
        .setMinFaceSize(0.2f) // Reduce from 0.9f to detect smaller faces
        .build()

    private val detector = FaceDetection.getClient(realTimeOpts)

    private val nameScoreHashmap = HashMap<String, ArrayList<Float>>()
    private var subject = FloatArray(model.embeddingDim)
    private var fileAccess: FileAccess = FileAccess(context)
    private var attendanceService: AttendanceService = AttendanceService(context)

    private var isProcessing = false
    private var lastDetectedName: String? = null
    private var confirmationCount = 0
    private val requiredConfirmations = 3

    private var lastProcessTime = 0L
    private val minProcessInterval = 250
    private var t1: Long = 0L

    private val metricToBeUsed = "l2"


    private var faceList = ArrayList<Pair<String, FloatArray>>()

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

        if (checkMemoryPressure()) {
            System.gc()
            image.close()
            return
        }
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastProcessTime < minProcessInterval) {
            image.close()
            return
        }
        lastProcessTime = currentTime

        if (isProcessing) {
            image.close()
            return
        }

        try {
            isProcessing = true
            val cameraXImage = image.image ?: return image.close()


            var frameBitmap = createBitmap(cameraXImage.width, cameraXImage.height)
            frameBitmap.copyPixelsFromBuffer(image.planes[0].buffer)
            frameBitmap = BitmapUtils.Companion.rotateBitmap(frameBitmap, image.imageInfo.rotationDegrees.toFloat())


            if (!boundingBoxOverlay.areDimsInit) {
                boundingBoxOverlay.frameHeight = frameBitmap.height
                boundingBoxOverlay.frameWidth = frameBitmap.width
            }

            val inputImage = InputImage.fromBitmap(frameBitmap, 0)
            detector.process(inputImage)
                .addOnSuccessListener { faces ->
                    CoroutineScope(Dispatchers.Default).launch {
                        if (isUserWithinDistance(faces, frameBitmap)) {
                            runModel(faces, frameBitmap)

                        } else {
                            withContext(Dispatchers.Main) {
                                boundingBoxOverlay.faceBoundingBoxes = ArrayList<Prediction>()
                                boundingBoxOverlay.invalidate()
                                isProcessing = false
                                lastDetectedName = "UnKnown"
                                confirmationCount = 0  // Reset confirmation count when no face is detected
                            }
                            Log.d("FaceDetection", "User is too far (more than 5 meters)")
                        }
                    }
                }
                .addOnCompleteListener {
                    image.close()
                    isProcessing = false
                }
         } catch (e: Exception) {
        Log.e("Analyze", "Error in frame processing", e)
    } finally {
        image.close()
        isProcessing = false
    }
    }

    private fun isUserWithinDistance(faces: List<Face>, frameBitmap: Bitmap): Boolean {
        if (faces.isEmpty()) return false
        val nearestFace = faces[0]
        val faceWidthPixels = nearestFace.boundingBox.width()
        val focalLengthPixels = 1000f
        val actualFaceWidthMeters = 0.15f
        val estimatedDistanceMeters = (focalLengthPixels * actualFaceWidthMeters) / faceWidthPixels
        return estimatedDistanceMeters <= 2f
    }

    private suspend fun runModel(faces: List<Face>, cameraFrameBitmap: Bitmap) {
        withContext(Dispatchers.Default) {
            t1 = System.currentTimeMillis()
            val nearestList = sortFacesByProximity(faces)
            detectedFace(nearestList, cameraFrameBitmap)
        }
    }

    private fun sortFacesByProximity(faces: List<Face>): List<Face> {
        val sortedFaces = faces.toMutableList()
        sortedFaces.sortWith(compareByDescending { face ->
            face.boundingBox.width() * face.boundingBox.height()
        })
        return sortedFaces
    }

    suspend fun detectedFace(faces: List<Face>, cameraFrameBitmap: Bitmap) {
        val predictions = ArrayList<Prediction>()
        if (faces.isEmpty()) {
            withContext(Dispatchers.Main) {
                boundingBoxOverlay.faceBoundingBoxes = predictions
                boundingBoxOverlay.invalidate()
                lastDetectedName = null
                confirmationCount = 0
                isProcessing = false
            }
            return
        }

        val face = faces[0]
        try {
            // ---------------------------------------------------------
            // 1. ANTI-SPOOFING (LIVENESS) CHECK
            // ---------------------------------------------------------
            val bounds = face.boundingBox
            val marginX = (bounds.width() * 0.2f).toInt()
            val marginY = (bounds.height() * 0.2f).toInt()
            val expandedRect = android.graphics.Rect(
                Math.max(0, bounds.left - marginX),
                Math.max(0, bounds.top - marginY),
                Math.min(cameraFrameBitmap.width, bounds.right + marginX),
                Math.min(cameraFrameBitmap.height, bounds.bottom + marginY)
            )

            val antiSpoofBitmap = BitmapUtils.Companion.cropRectFromBitmap(cameraFrameBitmap, expandedRect)
            val spoofScore = antiSpoofingAnalyzer.deSpoofing(antiSpoofBitmap)

            if (spoofScore == null) {
                withContext(Dispatchers.Main) { isProcessing = false }
                return
            }

            // Inverted Logic: > 0.25f ko spoof maanenge.
            // Agar low-light mein false spoof aa raha ho, toh isko 0.30f kar dein.
            if (spoofScore > 0.08f) {
                withContext(Dispatchers.Main) {
                    predictions.add(Prediction(face.boundingBox, "Spoof Detected"))
                    boundingBoxOverlay.faceBoundingBoxes = predictions
                    boundingBoxOverlay.invalidate()
                    isProcessing = false
                }
                return
            }

            // ---------------------------------------------------------
            // 2. FACENET RECOGNITION (TIGHT CROP)
            // ---------------------------------------------------------
            val currentFaceBitmap = BitmapUtils.Companion.cropRectFromBitmap(
                cameraFrameBitmap,
                face.boundingBox
            ).scale(112, 112)

            subject = model.getFaceEmbedding(currentFaceBitmap)

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

                if (bestScoreUserName != "Unknown") {
                    if (lastDetectedName == bestScoreUserName) {
                        confirmationCount++
                        Log.d("Attendance", "Confirmation count for $bestScoreUserName: $confirmationCount")
                        if (confirmationCount >= requiredConfirmations) {
                            val details = bestScoreUserName.split("-")
                            markAttendance(details[1], details[0])
                            Log.d("Attendance", "Attendance marked for $bestScoreUserName")
                            confirmationCount = 0
                        }
                    } else {
                        lastDetectedName = bestScoreUserName
                        confirmationCount = 1
                    }
                } else {
                    lastDetectedName = null
                    confirmationCount = 0
                }
                predictions.add(Prediction(face.boundingBox, bestScoreUserName))
            } else {
                predictions.add(Prediction(face.boundingBox, "Unknown"))
            }
        } catch (e: Exception) {
            Log.e("Model", "Exception in RecogniseFrameAnalyser: ${e.message}")
        }

        withContext(Dispatchers.Main) {
            boundingBoxOverlay.faceBoundingBoxes = predictions
            boundingBoxOverlay.invalidate()
            isProcessing = false
        }
    }
    private fun checkMemoryPressure(): Boolean {
        val runtime = Runtime.getRuntime()
        val usedMem = runtime.totalMemory() - runtime.freeMemory()
        val maxMem = runtime.maxMemory()
        return (usedMem > maxMem * 0.7)
    }
    private fun L2Norm(x1: FloatArray, x2: FloatArray): Float {
        var sum = 0f
        for (i in x1.indices) {
            val diff = x1[i] - x2[i]
            sum += diff * diff
        }
        return sqrt(sum)
    }

    private fun cosineSimilarity(x1: FloatArray, x2: FloatArray): Float {
        var dot = 0f
        var mag1 = 0f
        var mag2 = 0f
        for (i in x1.indices) {
            dot += x1[i] * x2[i]
            mag1 += x1[i] * x1[i]
            mag2 += x2[i] * x2[i]
        }
        return dot / (sqrt(mag1) * sqrt(mag2))
    }

    private val lastAttendanceTimeMap = HashMap<String, Long>()

    private var isMarkAttendanceDialogShowing = false

    private suspend fun markAttendance(empCode: String, empName: String) {
        val currentTime = System.currentTimeMillis()
        val lastAttendanceTime = lastAttendanceTimeMap[empCode] ?: 0
        val cooldownPeriod = 5 * 60 * 1000L
        if (currentTime - lastAttendanceTime < cooldownPeriod) return

        val sharedPreferences = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val ordIdString = sharedPreferences.getString("orgID", "0") ?: "0"
        val orgName: String = sharedPreferences.getString("orgName", "0")!!
        val ordId = ordIdString.toDouble().toInt()

        FirebaseLogger.getInstance().logMessageWithTimestamp(
            "$orgName $ordId",
            "EMPLOYEE:$empName ID:$empCode"
        )

        val baseTime = System.currentTimeMillis()

//        for (i in 1..30) {
//            val empCode = "0222" + i
//            val empName = "Test User " + i
//            val timestamp = baseTime + (i * 1000) // 1 sec gap
//
//            attendanceService.addAttendance(empCode, empName, timestamp)
//        }

//        val attendance = Attendance(
//            empCode,
//            empName,
//            currentTime,
//            false // initially not synced
//        )

//        attendanceService.saveAttendance(attendance);
        attendanceService.addAttendance(empCode, empName, ordId.toString(), orgName ,currentTime)
        lastAttendanceTimeMap[empCode] = currentTime

        withContext(Dispatchers.Main) {
            isMarkAttendanceDialogShowing = true
            val markAttendanceDialog = MarkAttendanceDialog(context, empName)
            markAttendanceDialog.show()

            Handler(Looper.getMainLooper()).postDelayed({
                markAttendanceDialog.dismiss()
                isMarkAttendanceDialogShowing = false
            }, 3000)
        }

    }
}
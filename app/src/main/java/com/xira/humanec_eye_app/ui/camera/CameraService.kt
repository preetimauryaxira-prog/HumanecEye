package com.xira.humanec_eye_app.ui.camera

import android.annotation.SuppressLint
import android.content.Context
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executors

class CameraService(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val frameAnalyser: Any,
) {
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraSelector: CameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

    fun startCameraPreview() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindPreview()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindPreview() {
        cameraProvider?.unbindAll()

        val preview: Preview = Preview.Builder().build()
        preview.surfaceProvider = previewView.surfaceProvider
        val imageFrameAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(480, 640))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        imageFrameAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(),
            frameAnalyser as ImageAnalysis.Analyzer
        )

        cameraProvider?.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageFrameAnalysis)
    }

    // Function to switch between front and back camera
    @SuppressLint("RestrictedApi")
    fun switchCamera() {
        cameraProvider?.unbindAll()
        cameraSelector = if (cameraSelector.lensFacing == CameraSelector.LENS_FACING_FRONT) {
            CameraSelector.DEFAULT_BACK_CAMERA
        } else {
            CameraSelector.DEFAULT_FRONT_CAMERA
        }
        bindPreview()
    }

    fun stopCamera() {
        cameraProvider?.unbindAll() // Unbind all use cases to stop the camera
    }
}
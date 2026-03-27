package com.xira.humanec_eye_app.ui.camera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.camera.core.CameraSelector
import androidx.core.graphics.toRectF
import androidx.core.graphics.toColorInt

// Defines an overlay on which the boxes and text will be drawn.
class BoundingBoxOverlay(context: Context, attributeSet: AttributeSet) :
    SurfaceView(context, attributeSet), SurfaceHolder.Callback {

    var areDimsInit = false
    var frameHeight = 0
    var frameWidth = 0

    var cameraFacing: Int = CameraSelector.LENS_FACING_FRONT

    var faceBoundingBoxes: ArrayList<Prediction>? = null

    private var output2OverlayTransform: Matrix = Matrix()

    private val textPaint = Paint().apply {
        strokeWidth = 2.0f
        textSize = 32f
        color = Color.WHITE
        textAlign = Paint.Align.CENTER // Center-align text
    }

    // Paint for the frame
    private val framePaint = Paint().apply {
        color = "#4FBA73".toColorInt()
        style = Paint.Style.STROKE

    }

    init {
       holder.addCallback(this)
        setZOrderOnTop(true) // Ensure it draws on top
        holder.setFormat(PixelFormat.TRANSPARENT) // Enable transparency
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
       draw()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        draw()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {

    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (faceBoundingBoxes.isNullOrEmpty()) {
            // Clear canvas immediately if no faces
            canvas.drawColor(Color.TRANSPARENT)
            return
        }
        // Clear the canvas
        canvas.drawColor(Color.TRANSPARENT)

        if (faceBoundingBoxes != null) {
            if (!areDimsInit) {
                // Initialize the transformation matrix
                val viewWidth = width.toFloat()
                val viewHeight = height.toFloat()
                val xFactor: Float = viewWidth / frameWidth.toFloat()
                val yFactor: Float = viewHeight / frameHeight.toFloat()

                // Scale and mirror the coordinates (required for front lens)
                output2OverlayTransform.preScale(xFactor, yFactor)
                if (cameraFacing == CameraSelector.LENS_FACING_FRONT) {
                    output2OverlayTransform.postScale(-1f, 1f, viewWidth / 2f, viewHeight / 2f)
                }
                areDimsInit = true
            }

            // Draw each face bounding box and label
            for (face in faceBoundingBoxes!!) {
                val boundingBox = face.bbox.toRectF()
                output2OverlayTransform.mapRect(boundingBox)

                // Draw the thin rectangle around the face
                drawFrame(canvas, boundingBox)

                // Draw the name label at the top center of the frame
                canvas.drawText(
                    face.label,
                    boundingBox.centerX(),
                    boundingBox.top - 20, // Position above the frame
                    textPaint
                )


            }
        }
    }

    // Draw a thin rectangle around the face
    private fun drawFrame(canvas: Canvas, boundingBox: RectF) {
        // Set the stroke width for the rectangle (thin line)
        framePaint.strokeWidth = 4f // Adjust the thickness as needed

        // Draw the rectangle around the face
        canvas.drawRect(boundingBox, framePaint)
    }

    // Redraw the overlay
    fun draw() {
        val canvas = holder.lockCanvas()
        if (canvas != null) {
            try {
                draw(canvas)
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }
    }

}
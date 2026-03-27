package com.xira.humanec_eye_app.ui.camera.model

import com.ml.quaterion.facenetdetection.model.ModelInfo

class Models {

    companion object {

        val FACENET = ModelInfo(
            "FaceNet",
            "facenet.tflite",
            0.3f,
            8f,
            128,
            160
        )



    }

}
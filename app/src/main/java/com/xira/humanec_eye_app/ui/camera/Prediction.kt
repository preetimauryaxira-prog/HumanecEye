package com.xira.humanec_eye_app.ui.camera

import android.graphics.Rect

data class Prediction( var bbox : Rect, var label : String , var maskLabel : String = "" )
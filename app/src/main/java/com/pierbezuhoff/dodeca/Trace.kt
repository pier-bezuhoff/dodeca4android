package com.pierbezuhoff.dodeca

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint

class Trace(val factor: Float, val paint: Paint) {
    var initialized = false
    lateinit var bitmap: Bitmap
    lateinit var canvas: Canvas
    val matrix = Matrix()
    var dx: Float = 0f
    var dy: Float = 0f
}
package com.pierbezuhoff.dodeca

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.core.graphics.withTranslation

class Trace(val paint: Paint) {
    var initialized = false
    lateinit var bitmap: Bitmap
    lateinit var canvas: Canvas
    val matrix = Matrix()
    // `traceBitmap` top-left corner - screen top-left corner
    var dx: Float = 0f
    var dy: Float = 0f

    fun onCanvas(draw: (Canvas) -> Unit) {
        canvas.withTranslation(-dx, -dy) { draw(this) }
    }

    fun retrace(width: Int, height: Int) {
        bitmap = Bitmap.createBitmap(
            factor * width, factor * height,
            Bitmap.Config.ARGB_8888)
        dx = (1f - factor) * width / 2
        dy = (1f - factor) * height / 2
        canvas = Canvas(bitmap)
        matrix.reset()
        matrix.preTranslate(dx, dy)
        initialized = true
    }

    companion object {
        const val factor: Int = 2 // bitmap == (factor ^ 2) * screens
    }
}
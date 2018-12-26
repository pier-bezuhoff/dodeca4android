package com.pierbezuhoff.dodeca

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint

class Trace(val paint: Paint) {
    var initialized = false
    lateinit var bitmap: Bitmap
    lateinit var canvas: Canvas
    val translation = Matrix() // factor => pre translation
    val motion = Matrix() // visible canvas = motion . translation $ canvas = blitMatrix canvas
    // `bitmap` top-left corner - screen top-left corner
    val blitMatrix get() = Matrix(translation).apply { postConcat(motion) }

    // visible
    fun translate(dx: Float, dy: Float) {
        motion.postTranslate(dx, dy)
    }

    // visible
    fun scale(sx: Float, sy: Float, x: Float = 0f, y: Float = 0f) {
        motion.postScale(sx, sy, x, y)
    }

    fun retrace(width: Int, height: Int) {
        bitmap = Bitmap.createBitmap(
            factor * width, factor * height,
            Bitmap.Config.ARGB_8888)
        val dx = (1f - factor) * width / 2
        val dy = (1f - factor) * height / 2
        canvas = Canvas(bitmap)
        translation.reset()
        translation.preTranslate(dx, dy)
        motion.reset()
        canvas.translate(-dx, -dy)
        initialized = true
    }

    companion object {
        // TODO: add to preferences
        const val factor: Int = 1 // bitmap == (factor ^ 2) * screens
    }
}
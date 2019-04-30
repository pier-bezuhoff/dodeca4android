package com.pierbezuhoff.dodeca.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix

class Trace {
    var initialized = false
    lateinit var bitmap: Bitmap
    lateinit var canvas: Canvas
    // `bitmap` top-left corner - screen top-left corner
    private val translation = Matrix() // factor => pre translation
    val motion = Matrix() // visible canvas = motion . translation $ canvas = blitMatrix canvas
    // maybe: cache blitMatrix for performance (Sleepy)
    val blitMatrix get() = Matrix(translation).apply { postConcat(motion) }
    private val factor: Int get() = options.canvasFactor.value // bitmap == (factor ^ 2) * screens

    // visible
    fun translate(dx: Float, dy: Float) {
        motion.postTranslate(dx, dy)
    }

    // visible
    fun scale(sx: Float, sy: Float, x: Float = 0f, y: Float = 0f) {
        motion.postScale(sx, sy, x, y)
    }

    private fun initTranslation(width: Int, height: Int): Pair<Float, Float> {
        val dx = (1f - factor) * width / 2
        val dy = (1f - factor) * height / 2
        translation.reset()
        translation.preTranslate(dx, dy)
        return Pair(dx, dy)
    }

    fun retrace(width: Int, height: Int) {
        // or use Bitmap.Config.ARGB_8888
        bitmap = Bitmap.createBitmap(
            factor * width, factor * height,
            Bitmap.Config.RGB_565)
        canvas = Canvas(bitmap)
        val (dx, dy) = initTranslation(width, height)
        motion.reset()
        canvas.translate(-dx, -dy)
        initialized = true
    }

    fun clear() {
        bitmap.recycle()
        initialized = false
    }
}
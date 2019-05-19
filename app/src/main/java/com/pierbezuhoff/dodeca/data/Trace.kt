package com.pierbezuhoff.dodeca.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint

class Trace(width: Int, height: Int) {
    // NOTE: bitmap == (factor ^ 2) * screens
    private val bitmap: Bitmap
    val canvas: Canvas
    /** (bitmap top-left corner) - (screen top-left corner) */
    private val translation: Matrix
    val motion: Matrix // visible canvas = motion . translation $ canvas = blitMatrix canvas
    // maybe: cache blitMatrix for performance
    private val blitMatrix
        get() = Matrix(translation).apply { postConcat(motion) }
    var currentCanvasFactor: Int = factor

    init {
        bitmap = Bitmap.createBitmap(
            factor * width, factor * height,
            BITMAP_CONFIG
        )
        canvas = Canvas(bitmap)
        translation = Matrix()
        motion = Matrix()
        val dx: Float = (1f - factor) * width / 2
        val dy: Float = (1f - factor) * height / 2
        canvas.translate(-dx, -dy)
        translation.postTranslate(dx, dy)
    }

    fun drawOn(canvas: Canvas, paint: Paint) =
        canvas.drawBitmap(bitmap, blitMatrix, paint)

    companion object {
        private val BITMAP_CONFIG = Bitmap.Config.RGB_565
        private val factor: Int get() = values.canvasFactor
    }
}
package com.pierbezuhoff.dodeca.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.pierbezuhoff.dodeca.models.OptionsManager

class Trace(
    width: Int, height: Int,
    val optionsManager: OptionsManager // models layer in the data layer, NotLikeThis
) {
    // NOTE: bitmap == (factor ^ 2) * screens
    val bitmap: Bitmap // must not be changed from outside
    val canvas: Canvas
    /** (bitmap top-left corner) - (screen top-left corner) */
    private val translation: Matrix
    val motion: Matrix // visible canvas = motion . translation $ canvas = blitMatrix canvas
    // MAYBE: cache blitMatrix for performance
    private val blitMatrix
        get() = Matrix(translation).apply { postConcat(motion) }
    private val factor: Int
        get() = optionsManager.values.canvasFactor
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
        private val BITMAP_CONFIG = Bitmap.Config.ARGB_8888
    }
}
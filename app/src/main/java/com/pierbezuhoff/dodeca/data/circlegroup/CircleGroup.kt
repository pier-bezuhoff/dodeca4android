@file:Suppress("FunctionName")

package com.pierbezuhoff.dodeca.data.circlegroup

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.pierbezuhoff.dodeca.data.CircleFigure
import com.pierbezuhoff.dodeca.data.Shape
import com.pierbezuhoff.dodeca.models.OptionsManager

typealias Ix = Int // short for "Index"
typealias Ixs = IntArray // indices


// TODO: detach update <-&-> draw
interface ImmutableCircleGroup {
    val defaultPaint: Paint // used for creating new paints in *set()*
    val figures: List<CircleFigure>
    operator fun get(i: Ix): CircleFigure
}

interface CircleGroup : ImmutableCircleGroup {
    operator fun set(i: Ix, figure: CircleFigure)
    fun setTexture(i: Ix, bitmap: Bitmap) // TMP?
    fun changeAngularSpeed(factor: Float)
    fun update(reverse: Boolean = false)
    fun updateTimes(times: Int, reverse: Boolean = false)
    fun draw(canvas: Canvas, shape: Shape = Shape.CIRCLE,)
    fun drawTimes(
        times: Int,
        reverse: Boolean = false,
        canvas: Canvas, shape: Shape = Shape.CIRCLE
    )
    fun drawOverlay(canvas: Canvas, selected: IntArray = intArrayOf())
}

interface SuspendableCircleGroup : CircleGroup {
    suspend fun suspendableUpdateTimes(times: Int, reverse: Boolean = false)
    suspend fun suspendableDrawTimes(
        times: Int,
        reverse: Boolean = false,
        canvas: Canvas, shape: Shape = Shape.CIRCLE
    )
}

fun mkCircleGroup(
    circleFigures: List<CircleFigure>,
    optionValues: OptionsManager.Values,
    defaultPaint: Paint
): SuspendableCircleGroup =
    when(optionValues.circleGroupImplementation) {
        "planar-sequential" -> PrimitiveCircles(circleFigures, optionValues, defaultPaint)
        "planar-sequential-rough" -> RoughPrimitiveCircles(circleFigures, optionValues, defaultPaint)
        "projective" -> ProjectiveCircles(circleFigures, optionValues, defaultPaint)
        else -> throw IllegalArgumentException("Illegal implementation name: ${optionValues.circleGroupImplementation}")
    }

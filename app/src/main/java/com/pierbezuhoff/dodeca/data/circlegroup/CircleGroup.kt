@file:Suppress("FunctionName")

package com.pierbezuhoff.dodeca.data.circlegroup

import android.graphics.Canvas
import android.graphics.Paint
import com.pierbezuhoff.dodeca.data.CircleFigure
import com.pierbezuhoff.dodeca.data.Shape

typealias Ix = Int // short for "Index"
typealias Ixs = IntArray // indices


// TODO: detach update <-&-> draw
interface ImmutableCircleGroup {
    val defaultPaint: Paint
    val figures: List<CircleFigure>
    operator fun get(i: Ix): CircleFigure
}

interface CircleGroup : ImmutableCircleGroup {
    operator fun set(i: Ix, figure: CircleFigure)
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
    implName: String, projR: Float?,
    circleFigures: List<CircleFigure>, defaultPaint: Paint
): SuspendableCircleGroup =
    when(implName) {
        "planar-sequential" -> PrimitiveCircles(circleFigures, defaultPaint)
        "planar-sequential-rough" -> RoughPrimitiveCircles(circleFigures, defaultPaint)
        "projective" -> ProjectiveCircles(circleFigures, defaultPaint, projR!!.toDouble())
        else -> throw IllegalArgumentException("Illegal implementation name: $implName")
    }
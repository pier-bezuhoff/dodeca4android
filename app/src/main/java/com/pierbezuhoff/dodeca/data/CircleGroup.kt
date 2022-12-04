@file:Suppress("FunctionName")

package com.pierbezuhoff.dodeca.data

import android.graphics.Canvas
import android.graphics.Paint

// TODO: detach update <-&-> draw
interface ImmutableCircleGroup {
    val defaultPaint: Paint
    val figures: List<CircleFigure>
    operator fun get(i: Int): CircleFigure
}

interface CircleGroup : ImmutableCircleGroup {
    operator fun set(i: Int, figure: CircleFigure)
    fun update(reverse: Boolean = false)
    fun updateTimes(times: Int, reverse: Boolean = false)
    fun draw(canvas: Canvas, shape: Shape = Shape.CIRCLE,)
    fun drawTimes(
        times: Int,
        reverse: Boolean = false,
        canvas: Canvas, shape: Shape = Shape.CIRCLE)
    fun drawOverlay(canvas: Canvas, selected: IntArray = intArrayOf())
}

interface SuspendableCircleGroup : CircleGroup {
    suspend fun suspendableUpdateTimes(times: Int, reverse: Boolean = false)
    suspend fun suspendableDrawTimes(
        times: Int,
        reverse: Boolean = false,
        canvas: Canvas, shape: Shape = Shape.CIRCLE)
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
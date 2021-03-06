@file:Suppress("FunctionName")

package com.pierbezuhoff.dodeca.data

import android.graphics.Canvas
import android.graphics.Paint

interface ImmutableCircleGroup {
    val figures: List<CircleFigure>
    operator fun get(i: Int): CircleFigure
}

interface CircleGroup : ImmutableCircleGroup {
    operator fun set(i: Int, figure: CircleFigure)
    fun update(reverse: Boolean = false)
    fun updateTimes(times: Int, reverse: Boolean = false)
    fun draw(canvas: Canvas, shape: Shape = Shape.CIRCLE, showAllCircles: Boolean = false)
    fun drawTimes(
        times: Int,
        reverse: Boolean = false,
        canvas: Canvas, shape: Shape = Shape.CIRCLE, showAllCircles: Boolean = false)
}

interface SuspendableCircleGroup : CircleGroup {
    suspend fun suspendableUpdateTimes(times: Int, reverse: Boolean = false)
    suspend fun suspendableDrawTimes(
        times: Int,
        reverse: Boolean = false,
        canvas: Canvas, shape: Shape = Shape.CIRCLE, showAllCircles: Boolean = false)
}

fun CircleGroup(circleFigures: List<CircleFigure>, defaultPaint: Paint): CircleGroup =
    PrimitiveCircles(circleFigures, defaultPaint)

fun SuspendableCircleGroup(circleFigures: List<CircleFigure>, defaultPaint: Paint): SuspendableCircleGroup =
    PrimitiveCircles(circleFigures, defaultPaint)

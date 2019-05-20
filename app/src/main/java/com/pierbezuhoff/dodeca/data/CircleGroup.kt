package com.pierbezuhoff.dodeca.data

import android.graphics.Canvas

typealias CircleGroupImpl = PrimitiveCircles

interface ImmutableCircleGroup {
    val figures: List<CircleFigure>
    operator fun get(i: Int): CircleFigure
}

interface CircleGroup : ImmutableCircleGroup {
    override val figures: List<CircleFigure>
    override operator fun get(i: Int): CircleFigure
    operator fun set(i: Int, figure: CircleFigure)
    fun update(reverse: Boolean = false)
    fun updateTimes(times: Int, reverse: Boolean = false)
    fun draw(canvas: Canvas, shape: Shapes = Shapes.CIRCLE, showAllCircles: Boolean = false)
    fun drawTimes(
        times: Int,
        reverse: Boolean = false,
        canvas: Canvas, shape: Shapes = Shapes.CIRCLE, showAllCircles: Boolean = false)
}


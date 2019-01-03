package com.pierbezuhoff.dodeca

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

interface CircleGroup {
    val figures: List<CircleFigure>
    fun update(reverse: Boolean = false)
    fun draw(canvas: Canvas, shape: Shapes = Shapes.CIRCLE, showAllCircles: Boolean = false, showOutline: Boolean = false)
}

internal data class Attributes(
    val borderColor: Int = CircleFigure.defaultBorderColor,
    val fill: Boolean = CircleFigure.defaultFill,
    val rule: String? = CircleFigure.defaultRule
) {
    private val dynamic: Boolean get() = rule?.isNotBlank() ?: false // is changing over time
    private val dynamicHidden: Boolean get() = rule?.startsWith("n") ?: false
    val show: Boolean get() = dynamic && !dynamicHidden
}

/* List<Circle> is represented as 3 FloatArray */
internal class PrimitiveCircles(cs: List<CircleFigure>, paint: Paint) : CircleGroup {
    private var size: Int = cs.size
    private val xs: FloatArray = FloatArray(size) { cs[it].x.toFloat() }
    private val ys: FloatArray = FloatArray(size) { cs[it].y.toFloat() }
    private val rs: FloatArray = FloatArray(size) { cs[it].radius.toFloat() }
    private val attrs: Array<Attributes> = Array(size) { Attributes(cs[it].borderColor, cs[it].fill, cs[it].rule) }
    private val rules: Array<IntArray> = Array(size) { cs[it].sequence }
    private val shownIndices: IntArray = attrs.mapIndexed { i, attr -> i to attr }.filter { it.second.show }.map { it.first }.toIntArray()
    private val paints: Array<Paint> = attrs.map {
        Paint(paint).apply {
            color = it.borderColor
            style = if (it.fill) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
        }
    }.toTypedArray()
    private val outlinePaint = paint.apply { color = Color.BLACK; style = Paint.Style.STROKE }
    override val figures: List<CircleFigure>
        get() = (0 until size).map { i ->
            val (bc, f, r) = attrs[i]
            CircleFigure(xs[i].toDouble(), ys[i].toDouble(), rs[i].toDouble(), bc, f, r)
        }

    override fun update(reverse: Boolean) {
        // TODO: save oldCircles
        if (reverse) // ~3x slower
            for (i in 0 until size)
                for (j in rules[i].reversed())
                    invert(i, j)
        else
            for (i in 0 until size)
                for (j in rules[i])
                    invert(i, j)
    }

    override fun draw(canvas: Canvas, shape: Shapes, showAllCircles: Boolean, showOutline: Boolean) {
        // TODO: rotation
        // TODO: show centers
        when (shape) {
            Shapes.CIRCLE -> drawHelper(showAllCircles) { drawCircle(it, canvas, showOutline) }
            Shapes.SQUARE -> drawHelper(showAllCircles) { drawSquare(it, canvas, showOutline) }
            Shapes.CROSS -> drawHelper(showAllCircles) { drawCross(it, canvas) }
            Shapes.VERTICAL_BAR -> drawHelper(showAllCircles) { drawVerticalBar(it, canvas) }
            Shapes.HORIZONTAL_BAR -> drawHelper(showAllCircles) { drawHorizontalBar(it, canvas) }
        }
    }

    private inline fun drawHelper(showAllCircles: Boolean, draw: (Int) -> Unit) {
        if (showAllCircles)
            for (i in 0 until size)
                draw(i)
        else
            for (i in shownIndices)
                draw(i)
    }

    private inline fun drawCircle(i: Int, canvas: Canvas, showOutline: Boolean = false) {
        canvas.drawCircle(xs[i], ys[i], rs[i], paints[i])
        if (showOutline)
            canvas.drawCircle(xs[i], ys[i], rs[i], outlinePaint)
    }

    private inline fun drawSquare(i: Int, canvas: Canvas, showOutline: Boolean = false) {
        val x = xs[i]
        val y = ys[i]
        val r = rs[i]
        canvas.drawRect(
            x - r, y - r,
            x + r, y + r,
            paints[i]
        )
        if (showOutline)
            canvas.drawRect(x - r, y - r, x + r, y + r, outlinePaint)
    }

    private inline fun drawCross(i: Int, canvas: Canvas) {
        val x = xs[i]
        val y = ys[i]
        val r = rs[i]
        canvas.drawLines(
            floatArrayOf(
                x, y - r, x, y + r,
                x - r, y, x + r, y
            ),
            paints[i]
        )
    }

    private inline fun drawVerticalBar(i: Int, canvas: Canvas) {
        val x = xs[i]
        val y = ys[i]
        val r = rs[i]
        canvas.drawLine(x, y - r, x, y + r, paints[i])
    }

    private inline fun drawHorizontalBar(i: Int, canvas: Canvas) {
        val x = xs[i]
        val y = ys[i]
        val r = rs[i]
        canvas.drawLine(x - r, y, x + r, y, paints[i])
    }

    /* invert i-th circle with respect to j-th */
    private inline fun invert(i: Int, j: Int) {
        val r = rs[j]
        val x0 = xs[i]
        val y0 = ys[i]
        val r0 = rs[i]
        val x = xs[j]
        val y = ys[j]
        when {
            r == 0f -> {
                xs[i] = x
                ys[i] = y
                rs[i] = 0f
            }
            x0 == x && y0 == y ->
                rs[i] = r * r / r0
            else -> {
                val dx = x0 - x
                val dy = y0 - y
                val d2 = dx * dx + dy * dy
                val r2 = r * r
                val r02 = r0 * r0
                // if (d2 == r02) d2 += 1e-6f
                val scale = r2 / (d2 - r02)
                xs[i] = x + dx * scale
                ys[i] = y + dy * scale
                rs[i] = r2 * r0 / Math.abs(d2 - r02)
            }
        }
    }
}


package com.pierbezuhoff.dodeca

import android.graphics.Canvas
import android.graphics.Paint

interface CircleGroup {
    val circles: List<CircleFigure>
    fun update()
    fun draw(canvas: Canvas, paint: Paint)
}

internal data class Attributes(
    val borderColor: Int = CircleFigure.defaultBorderColor,
    val fill: Boolean = CircleFigure.defaultFill,
    val rule: String? = CircleFigure.defaultRule
) {
    val dynamic: Boolean get() = rule?.isNotBlank() ?: false // is changing over time
    val dynamicHidden: Boolean get() = rule?.startsWith("n") ?: false
    val show: Boolean get() = dynamic && !dynamicHidden
}

class PrimitiveCircles(cs: List<CircleFigure>) : CircleGroup {
    private var size: Int = cs.size
    private val xs: FloatArray = FloatArray(size) { cs[it].x.toFloat() }
    private val ys: FloatArray = FloatArray(size) { cs[it].y.toFloat() }
    private val rs: FloatArray = FloatArray(size) { cs[it].radius.toFloat() }
    private val attrs: Array<Attributes> = Array(size) { Attributes(cs[it].borderColor, cs[it].fill, cs[it].rule) }
    private val rules: Array<IntArray> = Array(size) { cs[it].sequence }
    private val shownIndices: IntArray = attrs.mapIndexed { i, attr -> i to attr }.filter { it.second.show }.map { it.first }.toIntArray()
    override val circles: List<CircleFigure>
        get() = (0 until size).map { i ->
            val (bc, f, r) = attrs[i]
            CircleFigure(xs[i].toDouble(), ys[i].toDouble(), rs[i].toDouble(), bc, f, r)
        }

    override fun update() {
        // TODO: save oldCircles
        for (i in 0 until size)
            for (j in rules[i])
                invert(i, j)
    }

    override fun draw(canvas: Canvas, paint: Paint) {
        for (i in shownIndices)
            drawCircle(i, canvas, paint)
    }

    private inline fun drawCircle(i: Int, canvas: Canvas, paint: Paint) {
        val attr = attrs[i]
        paint.color = attr.borderColor
        paint.style = if (attr.fill) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
        canvas.drawCircle(xs[i], ys[i], rs[i], paint)
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

class Circles(private val _circles: MutableList<CircleFigure>) : CircleGroup {
    override val circles get() = _circles.toList()

    override fun update() {
        val oldCircles = circles.map { it.copy(newRule = null) }
        for (circle in circles)
            for (j in circle.sequence)
                circle.invert(oldCircles[j])
    }

    override fun draw(canvas: Canvas, paint: Paint) {
        circles.filter { it.show }.forEach { drawCircle(it, canvas, paint) }
    }

    private inline fun drawCircle(circle: CircleFigure, canvas: Canvas, paint: Paint) {
        val (c, r) = circle
        paint.color = circle.borderColor
        paint.style = if (circle.fill) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
        val (x, y) = c.asFF()
        canvas.drawCircle(x, y, r.toFloat(), paint)
    }
}
package com.pierbezuhoff.dodeca

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

typealias CircleGroupImpl = PrimitiveCircles

interface CircleGroup {
    val figures: List<CircleFigure>
    fun update(reverse: Boolean = false)
    fun updateTimes(times: Int, reverse: Boolean = false)
    fun draw(canvas: Canvas, shape: Shapes = Shapes.CIRCLE, showAllCircles: Boolean = false, showOutline: Boolean = false)
    fun drawTimes(
        times: Int,
        reverse: Boolean = false,
        canvas: Canvas, shape: Shapes = Shapes.CIRCLE, showAllCircles: Boolean = false, showOutline: Boolean = false)
}

internal data class Attributes(
    val borderColor: Int = CircleFigure.DEFAULT_COLOR,
    val fill: Boolean = CircleFigure.DEFAULT_FILL,
    val rule: String? = CircleFigure.DEFAULT_RULE
) {
    private val dynamic: Boolean get() = rule?.isNotBlank() ?: false // is changing over time
    private val dynamicHidden: Boolean get() = rule?.startsWith("n") ?: false
    val show: Boolean get() = dynamic && !dynamicHidden
}

// NOTE: if FloatArray instead of DoubleArray then Triada.ddu diverges, though it's ~2 times faster
// maybe: have float old_s
/* List<Circle> is represented as 3 DoubleArray */
class PrimitiveCircles(cs: List<CircleFigure>, paint: Paint) : CircleGroup {
    private var size: Int = cs.size
    private val xs: DoubleArray = DoubleArray(size) { cs[it].x }
    private val ys: DoubleArray = DoubleArray(size) { cs[it].y }
    private val rs: DoubleArray = DoubleArray(size) { cs[it].radius }
    private var oldXs: DoubleArray = xs // old_s are used for draw and as oldCircles in update
    private var oldYs: DoubleArray = ys
    private var oldRs: DoubleArray = rs
    private val attrs: Array<Attributes> = Array(size) { Attributes(cs[it].color, cs[it].fill, cs[it].rule) }
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
            CircleFigure(xs[i], ys[i], rs[i], bc, f, r)
        }

    override fun update(reverse: Boolean) = _update(reverse)

    private inline fun _update(reverse: Boolean) {
        savingOld {
            if (reverse) // ~3x slower
                reversedUpdate()
            else
                straightUpdate()
        }
    }

    override fun updateTimes(times: Int, reverse: Boolean) {
        if (reverse) {
            savingOld { repeat(times) { reversedUpdate() } }
        } else {
            savingOld { repeat(times) { straightUpdate() } }
        }
    }

    private inline fun savingOld(action: () -> Unit) {
        oldXs = xs.clone()
        oldYs = ys.clone()
        oldRs = rs.clone()
        action()
        oldXs = xs
        oldYs = ys
        oldRs = rs
    }

    private inline fun reversedUpdate() {
        for (i in 0 until size)
            for (j in rules[i].reversed())
                invert(i, j)
    }

    private inline fun straightUpdate() {
        for (i in 0 until size)
            for (j in rules[i])
                invert(i, j)
    }

    override fun draw(canvas: Canvas, shape: Shapes, showAllCircles: Boolean, showOutline: Boolean) =
        _draw(canvas, shape, showAllCircles, showOutline)

    private inline fun _draw(canvas: Canvas, shape: Shapes, showAllCircles: Boolean, showOutline: Boolean) {
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

    override fun drawTimes(
        times: Int,
        reverse: Boolean,
        canvas: Canvas, shape: Shapes, showAllCircles: Boolean, showOutline: Boolean
    ) {
//        repeat(times) {
//            _draw(canvas, shape, showAllCircles, showOutline)
//            _update(reverse)
//        }
//        _draw(canvas, shape, showAllCircles, showOutline)
        _drawTimes(times, reverse, canvas, shape, showAllCircles, showOutline)
    }

    /* draw; update; draw; update ...; draw
    * (times + 1) x draw, times x update
    * times >= 1
    * some clever inline-magic used
    * */
    private inline fun _drawTimes(
        times: Int,
        reverse: Boolean,
        canvas: Canvas, shape: Shapes, showAllCircles: Boolean, showOutline: Boolean
    ) {
        if (reverse)
            drawTimesU(times, canvas, shape, showAllCircles, showOutline) { reversedUpdate() }
        else
            drawTimesU(times, canvas, shape, showAllCircles, showOutline) { straightUpdate() }
    }

    private inline fun drawTimesU(
        times: Int,
        canvas: Canvas, shape: Shapes, showAllCircles: Boolean, showOutline: Boolean,
        update: () -> Unit
    ) {
        when (shape) {
            Shapes.CIRCLE -> drawTimesUS(times, showAllCircles, update) { drawCircle(it, canvas, showOutline) }
            Shapes.SQUARE -> drawTimesUS(times, showAllCircles, update) { drawSquare(it, canvas, showOutline) }
            Shapes.CROSS -> drawTimesUS(times, showAllCircles, update) { drawCross(it, canvas) }
            Shapes.VERTICAL_BAR -> drawTimesUS(times, showAllCircles, update) { drawVerticalBar(it, canvas) }
            Shapes.HORIZONTAL_BAR -> drawTimesUS(times, showAllCircles, update) { drawHorizontalBar(it, canvas) }
        }
    }

    private inline fun drawTimesUS(times: Int, showAllCircles: Boolean, update: () -> Unit, draw: (Int) -> Unit) {
        if (showAllCircles)
            drawTimesUSA(times, update) {
                for (i in 0 until size)
                    draw(i)
            }
        else
            drawTimesUSA(times, update) {
                for (i in shownIndices)
                    draw(i)
            }
    }

    private inline fun drawTimesUSA(times: Int, update: () -> Unit, drawAll: () -> Unit) {
        repeat(times) {
            drawAll()
            savingOld { update() }
        }
        drawAll()
    }

    // maybe: for optimization somehow lift if(showOutline) higher
    private inline fun drawCircle(i: Int, canvas: Canvas, showOutline: Boolean = false) {
        val x = oldXs[i].toFloat()
        val y = oldYs[i].toFloat()
        val r = oldRs[i].toFloat()
        canvas.drawCircle(x, y, r, paints[i])
        if (showOutline)
            canvas.drawCircle(x, y, r, outlinePaint)
    }

    private inline fun drawSquare(i: Int, canvas: Canvas, showOutline: Boolean = false) {
        val x = oldXs[i].toFloat()
        val y = oldYs[i].toFloat()
        val r = oldRs[i].toFloat()
        canvas.drawRect(
            x - r, y - r,
            x + r, y + r,
            paints[i]
        )
        if (showOutline)
            canvas.drawRect(x - r, y - r, x + r, y + r, outlinePaint)
    }

    private inline fun drawCross(i: Int, canvas: Canvas) {
        val x = oldXs[i].toFloat()
        val y = oldYs[i].toFloat()
        val r = oldRs[i].toFloat()
        canvas.drawLines(
            floatArrayOf(
                x, y - r, x, y + r,
                x - r, y, x + r, y
            ),
            paints[i]
        )
    }

    private inline fun drawVerticalBar(i: Int, canvas: Canvas) {
        val x = oldXs[i].toFloat()
        val y = oldYs[i].toFloat()
        val r = oldRs[i].toFloat()
        canvas.drawLine(x, y - r, x, y + r, paints[i])
    }

    private inline fun drawHorizontalBar(i: Int, canvas: Canvas) {
        val x = oldXs[i].toFloat()
        val y = oldYs[i].toFloat()
        val r = oldRs[i].toFloat()
        canvas.drawLine(x - r, y, x + r, y, paints[i])
    }

    /* invert i-th circle with respect to j-th old circle */
    private inline fun invert(i: Int, j: Int) {
        val x0 = xs[i]
        val y0 = ys[i]
        val r0 = rs[i]
        val x = oldXs[j]
        val y = oldYs[j]
        val r = oldRs[j]
        when {
            r == 0.0 -> {
                xs[i] = x
                ys[i] = y
                rs[i] = 0.0
            }
            x0 == x && y0 == y ->
                rs[i] = r * r / r0
            else -> {
                val dx = x0 - x
                val dy = y0 - y
                var d2 = dx * dx + dy * dy
                val r2 = r * r
                val r02 = r0 * r0
                 if (d2 == r02)
                     d2 += 1e-6f
                val scale = r2 / (d2 - r02)
                xs[i] = x + dx * scale
                ys[i] = y + dy * scale
                rs[i] = r2 * r0 / Math.abs(d2 - r02)
            }
        }
    }
}


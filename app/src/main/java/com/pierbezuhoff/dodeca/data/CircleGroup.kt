package com.pierbezuhoff.dodeca.data

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.SparseArray

typealias CircleGroupImpl = PrimitiveCircles

interface CircleGroup {
    val figures: List<CircleFigure>
    operator fun get(i: Int): CircleFigure
    operator fun set(i: Int, figure: CircleFigure)
    fun update(reverse: Boolean = false)
    fun updateTimes(times: Int, reverse: Boolean = false)
    fun draw(canvas: Canvas, shape: Shapes = Shapes.CIRCLE, showAllCircles: Boolean = false)
    fun drawTimes(
        times: Int,
        reverse: Boolean = false,
        canvas: Canvas, shape: Shapes = Shapes.CIRCLE, showAllCircles: Boolean = false)
}

internal data class FigureAttributes(
    val color: Int = CircleFigure.DEFAULT_COLOR,
    val fill: Boolean = CircleFigure.DEFAULT_FILL,
    val rule: String? = CircleFigure.DEFAULT_RULE,
    val borderColor: Int? = CircleFigure.DEFAULT_BORDER_COLOR
) {
    private val dynamic: Boolean get() = rule?.isNotBlank() ?: false // is changing over time
    private val dynamicHidden: Boolean get() = rule?.startsWith("n") ?: false
    val show: Boolean get() = dynamic && !dynamicHidden
}

// NOTE: if FloatArray instead of DoubleArray then Triada.ddu diverges, though it's ~2 times faster
// maybe: have float old_s
/* List<Circle> is represented as 3 DoubleArray */
class PrimitiveCircles(
    cs: List<CircleFigure>,
    private val paint: Paint
) : CircleGroup {
    private var size: Int = cs.size
    private val xs: DoubleArray = DoubleArray(size) { cs[it].x }
    private val ys: DoubleArray = DoubleArray(size) { cs[it].y }
    private val rs: DoubleArray = DoubleArray(size) { cs[it].radius }
    private var oldXs: DoubleArray = xs // old_s are used for draw and as oldCircles in redraw
    private var oldYs: DoubleArray = ys
    private var oldRs: DoubleArray = rs
    private val attrs: Array<FigureAttributes> = Array(size) {
        cs[it].run {
            FigureAttributes(color, fill, rule, borderColor)
        }
    }
    private val rules: Array<IntArray> = Array(size) { cs[it].sequence }
    private var shownIndices: IntArray = attrs.mapIndexed { i, attr -> i to attr }.filter { it.second.show }.map { it.first }.toIntArray()
    private val paints: Array<Paint> = attrs.map {
        Paint(paint).apply {
            color = it.color
            style =
                if (it.fill) Paint.Style.FILL_AND_STROKE
                else Paint.Style.STROKE
        }
    }.toTypedArray()
    private val defaultBorderPaint = Paint(paint).apply { color = Color.BLACK; style = Paint.Style.STROKE }
    private val borderPaints: SparseArray<Paint> = SparseArray<Paint>().apply {
        attrs.forEachIndexed { i, attr ->
            if (attr.borderColor != null && attr.fill && attr.show)
                append(i, Paint(defaultBorderPaint).apply { color = attr.borderColor })
        }
    }
    override val figures: List<CircleFigure>
        get() = (0 until size).map { i ->
            val (color, fill, rule, borderColor) = attrs[i]
            CircleFigure(xs[i], ys[i], rs[i], color, fill, rule, borderColor)
        }

    override fun get(i: Int): CircleFigure {
        val (color, fill, rule, borderColor) = attrs[i]
        return CircleFigure(oldXs[i], oldYs[i], oldRs[i], color, fill, rule, borderColor)
    }

    override fun set(i: Int, figure: CircleFigure) {
        val wasShown = attrs[i].show
        with(figure) {
            xs[i] = x
            ys[i] = y
            rs[i] = radius
            oldXs[i] = x
            oldYs[i] = y
            oldRs[i] = radius
            attrs[i] = FigureAttributes(color, fill, rule, borderColor)
            rules[i] = sequence
            paints[i] = Paint(paint).apply {
                color = figure.color
                style = if (fill) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
            }
            if (show && borderColor != null && fill)
                borderPaints.append(i, Paint(defaultBorderPaint).apply { color = borderColor })
            else
                borderPaints.delete(i)
            if (wasShown && !show)
                shownIndices = shownIndices.toMutableSet().run { remove(i); toIntArray() }
            else if (!wasShown && show)
                shownIndices = shownIndices.toMutableSet().run { add(i); toIntArray() }
        }
    }

    override fun update(reverse: Boolean) = _update(reverse)

    private inline fun _update(reverse: Boolean) {
        savingOld {
            if (reverse) // ~3x slower (due to reversing each sequence)
                reversedUpdate()
            else
                straightUpdate()
        }
    }

    // FIX: slow and diverges
    override fun updateTimes(times: Int, reverse: Boolean) {
        if (reverse) {
            savingOld { repeat(times) { reversedUpdate() } }
        } else {
            savingOld { repeat(times) { straightUpdate() } }
        }
    }

    private inline fun savingOld(crossinline action: () -> Unit) {
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

    override fun draw(canvas: Canvas, shape: Shapes, showAllCircles: Boolean) =
        _draw(canvas, shape, showAllCircles)

    private inline fun _draw(canvas: Canvas, shape: Shapes, showAllCircles: Boolean) {
        // TODO: rotation
        // TODO: show centers
        when (shape) {
            Shapes.CIRCLE -> drawHelper(showAllCircles) { drawCircle(it, canvas) }
            Shapes.SQUARE -> drawHelper(showAllCircles) { drawSquare(it, canvas) }
            Shapes.CROSS -> drawHelper(showAllCircles) { drawCross(it, canvas) }
            Shapes.VERTICAL_BAR -> drawHelper(showAllCircles) { drawVerticalBar(it, canvas) }
            Shapes.HORIZONTAL_BAR -> drawHelper(showAllCircles) { drawHorizontalBar(it, canvas) }
        }
    }

    private inline fun drawHelper(showAllCircles: Boolean, crossinline draw: (Int) -> Unit) {
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
        canvas: Canvas, shape: Shapes, showAllCircles: Boolean
    ) {
        repeat(times) {
            _draw(canvas, shape, showAllCircles)
            _update(reverse)
        }
        _draw(canvas, shape, showAllCircles)
        // TODO: understand why following is slower and diverges
        // _drawTimes(times, reverse, canvas, shape, showAllCircles)
    }

    /* draw; redraw; draw; redraw ...; draw
    * (times + 1) x draw, times x redraw
    * times >= 1
    * some clever inline-magic used
    * */
    private inline fun _drawTimes(
        times: Int,
        reverse: Boolean,
        canvas: Canvas, shape: Shapes, showAllCircles: Boolean
    ) {
        if (reverse)
            drawTimesU(times, canvas, shape, showAllCircles) { reversedUpdate() }
        else
            drawTimesU(times, canvas, shape, showAllCircles) { straightUpdate() }
    }

    private inline fun drawTimesU(
        times: Int,
        canvas: Canvas, shape: Shapes, showAllCircles: Boolean,
        crossinline update: () -> Unit
    ) {
        when (shape) {
            Shapes.CIRCLE -> drawTimesUS(times, showAllCircles, update) { drawCircle(it, canvas) }
            Shapes.SQUARE -> drawTimesUS(times, showAllCircles, update) { drawSquare(it, canvas) }
            Shapes.CROSS -> drawTimesUS(times, showAllCircles, update) { drawCross(it, canvas) }
            Shapes.VERTICAL_BAR -> drawTimesUS(times, showAllCircles, update) { drawVerticalBar(it, canvas) }
            Shapes.HORIZONTAL_BAR -> drawTimesUS(times, showAllCircles, update) { drawHorizontalBar(it, canvas) }
        }
    }

    private inline fun drawTimesUS(
        times: Int,
        showAllCircles: Boolean,
        crossinline update: () -> Unit,
        crossinline draw: (Int) -> Unit
    ) {
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

    private inline fun drawTimesUSA(times: Int, crossinline update: () -> Unit, crossinline drawAll: () -> Unit) {
        repeat(times) {
            drawAll()
            savingOld { update() }
        }
        drawAll()
    }

    private inline fun drawCircle(i: Int, canvas: Canvas) {
        val x = oldXs[i].toFloat()
        val y = oldYs[i].toFloat()
        val r = oldRs[i].toFloat()
        canvas.drawCircle(x, y, r, paints[i])
        // maybe: store Boolean flag
        borderPaints.get(i)?.let { borderPaint ->
            canvas.drawCircle(x, y, r, borderPaint)
        }
    }

    private inline fun drawSquare(i: Int, canvas: Canvas) {
        val x = oldXs[i].toFloat()
        val y = oldYs[i].toFloat()
        val r = oldRs[i].toFloat()
        canvas.drawRect(
            x - r, y - r,
            x + r, y + r,
            paints[i]
        )
        borderPaints.get(i)?.let { borderPaint ->
            canvas.drawRect(
                x - r, y - r, x + r, y + r,
                borderPaint
            )
        }
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
                if (d2 == r02) // if result should be line
                    d2 += 1e-6f
                val scale = r2 / (d2 - r02)
                xs[i] = x + dx * scale
                ys[i] = y + dy * scale
                rs[i] = r2 * r0 / Math.abs(d2 - r02)
            }
        }
    }
}


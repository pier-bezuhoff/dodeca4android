package com.pierbezuhoff.dodeca.data

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.SweepGradient
import android.util.SparseArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

// NOTE: 32-bit Float numbers used instead, Triada.ddu diverges, though it's ~2 times faster
// MAYBE: have float only for old_s
/* List<Circle> is represented as 3 FloatArray */
@Suppress("NOTHING_TO_INLINE")
internal class RoughPrimitiveCircles(
    cs: List<CircleFigure>,
    private val paint: Paint
) : SuspendableCircleGroup {
    private val size: Int = cs.size
    private val xs: FloatArray = FloatArray(size) { cs[it].x.toFloat() }
    private val ys: FloatArray = FloatArray(size) { cs[it].y.toFloat() }
    private val rs: FloatArray = FloatArray(size) { cs[it].radius.toFloat() }
    private var oldXs: FloatArray = xs // old_s are used for draw and as oldCircles in redraw
    private var oldYs: FloatArray = ys
    private var oldRs: FloatArray = rs
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
    private val defaultBorderPaint = Paint(paint)
        .apply { color = Color.BLACK; style = Paint.Style.STROKE }
    private val borderPaints: SparseArray<Paint> = SparseArray<Paint>()
        .apply {
            attrs.forEachIndexed { i, attr ->
                if (attr.borderColor != null && attr.fill && attr.show)
                    append(i, Paint(defaultBorderPaint).apply { color = attr.borderColor })
            }
        }
    override val defaultPaint: Paint = paint
    override val figures: List<CircleFigure>
        get() = (0 until size).map { i ->
            val (color, fill, rule, borderColor) = attrs[i]
            CircleFigure(xs[i].toDouble(), ys[i].toDouble(), rs[i].toDouble(), color, fill, rule, borderColor)
        }

    override fun get(i: Int): CircleFigure {
        val (color, fill, rule, borderColor) = attrs[i]
        return CircleFigure(oldXs[i].toDouble(), oldYs[i].toDouble(), oldRs[i].toDouble(), color, fill, rule, borderColor)
    }

    override fun set(i: Int, figure: CircleFigure) {
        val wasShown = attrs[i].show
        with(figure) {
            xs[i] = x.toFloat()
            ys[i] = y.toFloat()
            rs[i] = radius.toFloat()
            oldXs[i] = x.toFloat()
            oldYs[i] = y.toFloat()
            oldRs[i] = radius.toFloat()
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

    override fun updateTimes(times: Int, reverse: Boolean) =
        _updateTimes(times, reverse)

    private inline fun _updateTimes(times: Int, reverse: Boolean) {
//        repeat(times) {
//            _update(reverse)
//        }
        if (reverse) {
            repeat(times) { savingOld { reversedUpdate() } }
        } else {
            repeat(times) { savingOld { straightUpdate() } }
        }
    }

    override suspend fun suspendableUpdateTimes(times: Int, reverse: Boolean) {
        // TODO: optimize
        // NOTE: not using _updateTimes in order to make coroutine cancellable-cooperative
        //  _updateTimes ~ 2 times faster
        // MAYBE: use batch of some fixed size
        repeat(times) {
            withContext(Dispatchers.Default) {
                _update(reverse)
            }
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

    override fun draw(canvas: Canvas, shape: Shape) =
        _draw(canvas, shape)

    private inline fun _draw(canvas: Canvas, shape: Shape) {
        // MAYBE: rotate shapes
        when (shape) {
            Shape.CIRCLE -> drawHelper { drawCircle(it, canvas) }
            Shape.SQUARE -> drawHelper { drawSquare(it, canvas) }
            Shape.CROSS -> drawHelper { drawCross(it, canvas) }
            Shape.VERTICAL_BAR -> drawHelper { drawVerticalBar(it, canvas) }
            Shape.HORIZONTAL_BAR -> drawHelper { drawHorizontalBar(it, canvas) }
        }
    }

    private inline fun drawHelper(crossinline draw: (Int) -> Unit) {
        for (i in shownIndices)
            draw(i)
    }

    override fun drawTimes(
        times: Int,
        reverse: Boolean,
        canvas: Canvas, shape: Shape
    ) {
        repeat(times) {
            _draw(canvas, shape)
            _update(reverse)
        }
        _draw(canvas, shape)
        // TODO: understand why the following is slower
//         _drawTimes(times, reverse, canvas, shape, showAllCircles)
    }

    override suspend fun suspendableDrawTimes(
        times: Int,
        reverse: Boolean,
        canvas: Canvas,
        shape: Shape,
    ) {
        // TODO: optimize
        // NOTE: not using _drawTimes in order to make coroutine cancellable-cooperative
        repeat(times) {
            withContext(Dispatchers.Default) {
                _draw(canvas, shape)
                _update(reverse)
            }
        }
        withContext(Dispatchers.Default) { _draw(canvas, shape) }
    }

    // unused: too slow
    /* draw; update; draw; update ...; draw
    * = (n + 1) x draw, n x update
    * */
    private inline fun _drawTimes(
        times: Int,
        reverse: Boolean,
        canvas: Canvas, shape: Shape
    ) {
        if (reverse)
            drawTimesU(times, canvas, shape) { reversedUpdate() }
        else
            drawTimesU(times, canvas, shape) { straightUpdate() }
    }

    private inline fun drawTimesU(
        times: Int,
        canvas: Canvas, shape: Shape,
        crossinline update: () -> Unit
    ) {
        when (shape) {
            Shape.CIRCLE -> drawTimesUD(times, update) { drawCircle(it, canvas) }
            Shape.SQUARE -> drawTimesUD(times, update) { drawSquare(it, canvas) }
            Shape.CROSS -> drawTimesUD(times, update) { drawCross(it, canvas) }
            Shape.VERTICAL_BAR -> drawTimesUD(times, update) { drawVerticalBar(it, canvas) }
            Shape.HORIZONTAL_BAR -> drawTimesUD(times, update) { drawHorizontalBar(it, canvas) }
        }
    }

    private inline fun drawTimesUD(
        times: Int,
        crossinline update: () -> Unit,
        crossinline draw: (Int) -> Unit
    ) {
        drawTimesUDA(times, update) {
            for (i in shownIndices)
                draw(i)
        }
    }

    private inline fun drawTimesUDA(times: Int, crossinline update: () -> Unit, crossinline drawAll: () -> Unit) {
        repeat(times) {
            drawAll()
            savingOld { update() }
        }
        drawAll()
    }

    private inline fun drawCircle(i: Int, canvas: Canvas) {
        val x = oldXs[i]
        val y = oldYs[i]
        val r = oldRs[i]
        canvas.drawCircle(x, y, r, paints[i])
        borderPaints.get(i)?.let { borderPaint ->
            canvas.drawCircle(x, y, r, borderPaint)
        }
    }

    private inline fun drawSquare(i: Int, canvas: Canvas) {
        val x = oldXs[i]
        val y = oldYs[i]
        val r = oldRs[i]
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
        val x = oldXs[i]
        val y = oldYs[i]
        val r = oldRs[i]
        canvas.drawLines(
            floatArrayOf(
                x, y - r, x, y + r,
                x - r, y, x + r, y
            ),
            paints[i]
        )
    }

    private inline fun drawVerticalBar(i: Int, canvas: Canvas) {
        val x = oldXs[i]
        val y = oldYs[i]
        val r = oldRs[i]
        canvas.drawLine(x, y - r, x, y + r, paints[i])
    }

    private inline fun drawHorizontalBar(i: Int, canvas: Canvas) {
        val x = oldXs[i]
        val y = oldYs[i]
        val r = oldRs[i]
        canvas.drawLine(x - r, y, x + r, y, paints[i])
    }

    override fun drawOverlay(canvas: Canvas, selected: IntArray) {
        for (i in 0 until size) {
            if (i in selected) {
                drawCircleOverlay(i, canvas, bold = true)
                drawSelectedCircleOverlay(i, canvas)
            }
            else {
                drawCircleOverlay(i, canvas)
            }
        }
    }

    private fun drawCircleOverlay(i: Int, canvas: Canvas, bold: Boolean = false) {
        val x = oldXs[i]
        val y = oldYs[i]
        val r = oldRs[i]
        val attr = attrs[i]
        val c = attr.borderColor ?: attr.color
        val paint = Paint(paints[i])
        paint.style = Paint.Style.STROKE
        if (!attr.show) {
            // NOTE: quite slow w/ hardware acceleration
            val dashEffect = DashPathEffect(floatArrayOf(15f, 5f), 0f)
            paint.pathEffect = dashEffect
        }
        paint.shader = SweepGradient(x, y, intArrayOf(c, Color.BLACK, c), null)
        if (bold) // yucky, do smth else
            paint.strokeWidth = 2f // 0 by default
        canvas.drawCircle(x, y, r, paint)
    }

    private fun drawSelectedCircleOverlay(i: Int, canvas: Canvas) {
        val x = oldXs[i]
        val y = oldYs[i]
        val r = oldRs[i]
        val p = Paint()
        p.pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
        canvas.drawLine(x, y - r, x, y + r, p) // dashed vertical diameter
        p.pathEffect = DashPathEffect(floatArrayOf(2f, 2f), 0f)
        canvas.drawLine(x, y, x + r, y, p)
        canvas.drawPoint(x + r, y, p)
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
            r == 0.0f -> {
                xs[i] = x
                ys[i] = y
                rs[i] = 0.0f
            }
            x0 == x && y0 == y ->
                rs[i] = r * r / r0
            else -> {
                val dx = x0 - x
                val dy = y0 - y
                var d2 = dx * dx + dy * dy
                val r2 = r * r
                val r02 = r0 * r0
                if (d2 == r02) // if result should be a line
                    d2 += 1e-6f
                val scale = r2 / (d2 - r02)
                xs[i] = x + dx * scale
                ys[i] = y + dy * scale
                rs[i] = r2 * r0 / abs(d2 - r02)
            }
        }
    }
}



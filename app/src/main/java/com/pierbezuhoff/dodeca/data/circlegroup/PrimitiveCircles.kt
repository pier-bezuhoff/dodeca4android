package com.pierbezuhoff.dodeca.data.circlegroup

import android.graphics.Canvas
import android.graphics.Paint
import com.pierbezuhoff.dodeca.data.CircleFigure
import com.pierbezuhoff.dodeca.data.FigureAttributes
import com.pierbezuhoff.dodeca.data.Shape
import kotlin.math.abs

// NOTE: if FloatArray instead of DoubleArray then Triada.ddu diverges, though it's ~2 times faster
// maybe: have float old_s
/* List<Circle> is represented as 3 DoubleArray */
@Suppress("NOTHING_TO_INLINE")
internal class PrimitiveCircles(
    figures: List<CircleFigure>,
    paint: Paint
) : BaseCircleGroup(figures, paint) {
    // new _*s <-- old *s
    private val _xs: DoubleArray = DoubleArray(size) { figures[it].x }
    private val _ys: DoubleArray = DoubleArray(size) { figures[it].y }
    private val _rs: DoubleArray = DoubleArray(size) { figures[it].radius }
    override var xs: DoubleArray = _xs // old_s are used for draw and as oldCircles in redraw
    override var ys: DoubleArray = _ys
    override var rs: DoubleArray = _rs
    private val rules: Array<IntArray> = Array(size) { figures[it].sequence }
    private val reversedRules by lazy { rules.map { it.reversedArray() }.toTypedArray() }
    override val figures: List<CircleFigure>
        get() = (0 until size).map { i ->
            val (color, fill, rule, borderColor) = attrs[i]
            CircleFigure(_xs[i], _ys[i], _rs[i], color, fill, rule, borderColor)
        }

    override fun get(i: Ix): CircleFigure {
        val (color, fill, rule, borderColor) = attrs[i]
        return CircleFigure(xs[i], ys[i], rs[i], color, fill, rule, borderColor)
    }

    override fun set(i: Ix, figure: CircleFigure) {
        val wasShown = attrs[i].show
        with(figure) {
            _xs[i] = x
            _ys[i] = y
            _rs[i] = radius
            xs[i] = x
            ys[i] = y
            rs[i] = radius
            attrs[i] = FigureAttributes(color, fill, rule, borderColor)
            rules[i] = sequence
            reversedRules[i] = sequence.reversedArray()
            paints[i] = Paint(defaultPaint).apply {
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

    // MAYBE: inline
    override fun _update(reverse: Boolean) {
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

    private inline fun savingOld(crossinline action: () -> Unit) {
        xs = _xs.clone()
        ys = _ys.clone()
        rs = _rs.clone()
        action()
        xs = _xs
        ys = _ys
        rs = _rs
    }

    private inline fun simpleReversedUpdate() {
        for (i in 0 until size)
            for (j in reversedRules[i])
                invert(i, j)
    }

    // NOTE: does not handle ddus w/ self-dependent rules
    private inline fun reversedUpdate() {
        if (hasCircularDependencies)
            simpleReversedUpdate()
        else
            for (i in ranked!!)
                for (j in reversedRules[i])
                    invertNow(i, j) // uses the new #j-th circle instead of the old one
    }

    private inline fun straightUpdate() {
        for (i in 0 until size)
            for (j in rules[i])
                invert(i, j)
    }

    // TODO: fix; unused bc too slow (?!)
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
        crossinline draw: (Ix) -> Unit
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

    /* invert i-th circle with respect to j-th old circle */
    private inline fun invert(i: Ix, j: Ix) {
        val x0 = _xs[i]
        val y0 = _ys[i]
        val r0 = _rs[i]
        val x = xs[j]
        val y = ys[j]
        val r = rs[j]
        when {
            r == 0.0 -> {
                _xs[i] = x
                _ys[i] = y
                _rs[i] = 0.0
            }
            x0 == x && y0 == y ->
                _rs[i] = r * r / r0
            else -> {
                val dx = x0 - x
                val dy = y0 - y
                var d2 = dx * dx + dy * dy
                val r2 = r * r
                val r02 = r0 * r0
                if (d2 == r02) // if result should be a line
                    d2 += 1e-6f
                val scale = r2 / (d2 - r02)
                _xs[i] = x + dx * scale
                _ys[i] = y + dy * scale
                _rs[i] = r2 * r0 / abs(d2 - r02)
            }
        }
    }

    /* invert i-th circle with respect to j-th circle */
    private inline fun invertNow(i: Ix, j: Ix) {
        val x0 = _xs[i]
        val y0 = _ys[i]
        val r0 = _rs[i]
        val x = _xs[j]
        val y = _ys[j]
        val r = _rs[j]
        when {
            r == 0.0 -> {
                _xs[i] = x
                _ys[i] = y
                _rs[i] = 0.0
            }
            x0 == x && y0 == y ->
                _rs[i] = r * r / r0
            else -> {
                val dx = x0 - x
                val dy = y0 - y
                var d2 = dx * dx + dy * dy
                val r2 = r * r
                val r02 = r0 * r0
                if (d2 == r02) // if result should be a line
                    d2 += 1e-6f
                val scale = r2 / (d2 - r02)
                _xs[i] = x + dx * scale
                _ys[i] = y + dy * scale
                _rs[i] = r2 * r0 / abs(d2 - r02)
            }
        }
    }
}



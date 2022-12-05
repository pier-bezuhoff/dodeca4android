package com.pierbezuhoff.dodeca.data.circlegroup

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.SweepGradient
import android.util.SparseArray
import com.pierbezuhoff.dodeca.data.CircleFigure
import com.pierbezuhoff.dodeca.data.FigureAttributes
import com.pierbezuhoff.dodeca.data.Shape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Suppress("NOTHING_TO_INLINE", "MemberVisibilityCanBePrivate", "FunctionName")
abstract class BaseCircleGroup(
    figures: List<CircleFigure>,
    protected val paint: Paint, // used for creating new paints in *set()*
) : SuspendableCircleGroup {
    protected val size: Int = figures.size
    protected abstract val xs: DoubleArray // only used for drawing position here
    protected abstract val ys: DoubleArray // MAYBE: keep them as Floats since that's how these are mostly used
    protected abstract val rs: DoubleArray
    protected val attrs: Array<FigureAttributes> = Array(size) { i ->
        figures[i].run {
            FigureAttributes(color, fill, rule, borderColor)
        }
    }
    protected var shownIndices: Ixs = attrs
        .mapIndexed { i, attr -> i to attr }
        .filter { it.second.show }
        .map { it.first }
        .toIntArray()
    protected val paints: Array<Paint> = attrs.map {
        Paint(paint).apply {
            color = it.color
            style =
                if (it.fill) Paint.Style.FILL_AND_STROKE
                else Paint.Style.STROKE
        }
    }.toTypedArray()
    protected val defaultBorderPaint = Paint(paint)
        .apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
        }
    protected val borderPaints: SparseArray<Paint> = SparseArray<Paint>()
        .apply {
            attrs.forEachIndexed { i, attr ->
                if (attr.borderColor != null && attr.fill && attr.show)
                    append(i, Paint(defaultBorderPaint).apply { color = attr.borderColor })
                // Q: ^^^ is it correct?
            }
        }
    override val defaultPaint: Paint = paint


    override fun update(reverse: Boolean) =
        _update(reverse)

    protected abstract fun _update(reverse: Boolean)

    /* -> rank#: list of circles' indices */
    protected fun computeRanks(): List<List<Ix>> {
        // factor by rules
        val allRules = attrs.map { it.rule ?: "" } // Ix: Rule
        val r2ixs = allRules // Rule: list of circles' indices
            .withIndex()
            .groupBy { (_, r) -> r }
            .mapValues { (_, v) -> v.map { (rIx, _) -> rIx } }
        val rules = allRules
            .flatMap { r -> r.map { c -> c.digitToInt() } }
            .toMutableSet()
        val ranks = mutableListOf<List<Ix>>()
        val rank0 = r2ixs[""]!!
        ranks.add(rank0)
        rules.removeAll(rank0.toSet())
        var rank = mutableListOf<Ix>()
        var maxDepth = 100 // just in case
        while (rules.isNotEmpty() && --maxDepth > 0) {
            // find those that do not depend on rules
            // if no look for circular dependencies (i in allRules[i])
            rank = mutableListOf()
            TODO()
        }
        assert(maxDepth > 0) { "yabe" }
        return ranks
    }

    override fun draw(canvas: Canvas, shape: Shape) =
        _draw(canvas, shape)

    protected inline fun _draw(canvas: Canvas, shape: Shape) {
        // MAYBE: rotate shapes
        when (shape) {
            Shape.CIRCLE -> drawHelper { drawCircle(it, canvas) }
            Shape.SQUARE -> drawHelper { drawSquare(it, canvas) }
            Shape.CROSS -> drawHelper { drawCross(it, canvas) }
            Shape.VERTICAL_BAR -> drawHelper { drawVerticalBar(it, canvas) }
            Shape.HORIZONTAL_BAR -> drawHelper { drawHorizontalBar(it, canvas) }
        }
    }

    protected inline fun drawHelper(crossinline draw: (Ix) -> Unit) {
        for (i in shownIndices)
            draw(i)
    }

    override fun drawTimes(times: Int, reverse: Boolean, canvas: Canvas, shape: Shape) {
        // TODO: understand why the following is slower
//         _drawTimes(times, reverse, canvas, shape, showAllCircles)
        repeat(times) {
            _draw(canvas, shape)
            _update(reverse)
        }
        _draw(canvas, shape)
    }

    override fun drawOverlay(canvas: Canvas, selected: Ixs) {
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

    override suspend fun suspendableDrawTimes(times: Int, reverse: Boolean, canvas: Canvas, shape: Shape) {
        // TODO: optimize
        // NOTE: not using _drawTimes in order to make coroutine cancellable-cooperative
        repeat(times) {
            withContext(Dispatchers.Default) {
                _draw(canvas, shape)
                _update(reverse)
            }
        }
        withContext(Dispatchers.Default) {
            _draw(canvas, shape)
        }
    }

    protected inline fun drawCircle(i: Ix, canvas: Canvas) {
        val x = xs[i].toFloat()
        val y = ys[i].toFloat()
        val r = rs[i].toFloat()
        canvas.drawCircle(x, y, r, paints[i])
        borderPaints.get(i)?.let { borderPaint ->
            canvas.drawCircle(x, y, r, borderPaint)
        }
    }

    protected inline fun drawSquare(i: Ix, canvas: Canvas) {
        val x = xs[i].toFloat()
        val y = ys[i].toFloat()
        val r = rs[i].toFloat()
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

    protected inline fun drawCross(i: Ix, canvas: Canvas) {
        val x = xs[i].toFloat()
        val y = ys[i].toFloat()
        val r = rs[i].toFloat()
        canvas.drawLines(
            floatArrayOf(
                x, y - r, x, y + r,
                x - r, y, x + r, y
            ),
            paints[i]
        )
    }

    protected inline fun drawVerticalBar(i: Ix, canvas: Canvas) {
        val x = xs[i].toFloat()
        val y = ys[i].toFloat()
        val r = rs[i].toFloat()
        canvas.drawLine(x, y - r, x, y + r, paints[i])
    }

    protected inline fun drawHorizontalBar(i: Ix, canvas: Canvas) {
        val x = xs[i].toFloat()
        val y = ys[i].toFloat()
        val r = rs[i].toFloat()
        canvas.drawLine(x - r, y, x + r, y, paints[i])
    }

    protected fun drawCircleOverlay(i: Ix, canvas: Canvas, bold: Boolean = false) {
        val x = xs[i].toFloat()
        val y = ys[i].toFloat()
        val r = rs[i].toFloat()
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

    protected fun drawSelectedCircleOverlay(i: Ix, canvas: Canvas) {
        val x = xs[i].toFloat()
        val y = ys[i].toFloat()
        val r = rs[i].toFloat()
        val p = Paint()
        p.pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
        canvas.drawLine(x, y - r, x, y + r, p) // dashed vertical diameter
        p.pathEffect = DashPathEffect(floatArrayOf(2f, 2f), 0f)
        canvas.drawLine(x, y, x + r, y, p)
        canvas.drawPoint(x + r, y, p)
    }
}
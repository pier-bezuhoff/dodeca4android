package com.pierbezuhoff.dodeca.data.circlegroup

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.SweepGradient
import android.util.Log
import android.util.SparseArray
import com.pierbezuhoff.dodeca.data.CircleFigure
import com.pierbezuhoff.dodeca.data.FigureAttributes
import com.pierbezuhoff.dodeca.data.Shape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil

// TODO: remove/comment texture code for now
// my attempt to detach the draw logic from updates & other structures
@Suppress("NOTHING_TO_INLINE", "MemberVisibilityCanBePrivate", "FunctionName")
abstract class DoubleBackedCircleGroup(
    figures: List<CircleFigure>,
    paint: Paint,
) : SuspendableCircleGroup {
    protected val size: Int = figures.size
    protected abstract val xs: DoubleArray // only used for drawing position here
    protected abstract val ys: DoubleArray // MAYBE: keep them as Floats since that's how these are mostly used
    protected abstract val rs: DoubleArray
    protected val attrs: Array<FigureAttributes> = Array(size) { i ->
        figures[i].run {
            FigureAttributes(color, fill, visible, rule, borderColor)
        }
    }
    protected val textures: MutableMap<Ix, Bitmap> = mutableMapOf() // MAYBE: use SparseArray instead
    protected var shownIndices: Ixs = attrs
        .mapIndexed { i, attr -> i to attr }
        .filter { it.second.visible }
        .map { it.first }
        .toIntArray()
    protected val _ranks by lazy { computeRanks() }
    /* rank=0 indices, rank=1 indices, etc. */
    protected val ranked: Ixs by lazy { _ranks.second.flatten().toIntArray() }
    protected val hasCircularDependencies by lazy { _ranks.first }
    /* pairs of circles constituting engines */
    protected val enginePairs: Set<Pair<Ix, Ix>> by lazy { computePairs() }
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
                if (attr.borderColor != null && attr.fill && attr.visible)
                    append(i, Paint(defaultBorderPaint).apply { color = attr.borderColor })
                // Q: ^^^ is it correct?
            }
        }
    override val defaultPaint: Paint = paint
    protected val maskPaint: Paint = Paint(paint).apply {
        style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    }


    override fun setTexture(i: Ix, bitmap: Bitmap) {
        textures[i] = bitmap
    }

    override fun update(reverse: Boolean) =
        _update(reverse)

    protected abstract fun _update(reverse: Boolean)

    /* -> if ddu has a circular or worse rank structure, rank#: list of circles' indices */
    protected fun computeRanks(): Pair<Boolean, List<List<Ix>>> {
        // factor by rules
        val allRules = attrs.map { it.rule } // ix => rule
        val r2ixs = allRules // rule => ixs owning it
            .withIndex()
            .groupBy { (_, r) -> r }
            .mapValues { (_, v) -> v.map { (rIx, _) -> rIx } }
        val rules = allRules // ix => rule = ixs it consists of
            .withIndex()
            .associate { (i, r) -> i to r }
            .toMutableMap()
        val ranks = mutableListOf<List<Ix>>()
        val rank0 = r2ixs[emptyList()]!!
        fun addRank(rank: Set<Ix>) {
            ranks.add(rank.toList().sorted())
            rank.forEach { rules.remove(it) }
        }
        addRank(rank0.toSet())
        var hasLoops = false
        while (rules.isNotEmpty()) {
            // find those that do not depend on rules
            val notCategorized = rules.keys
            val independent = rules.filter { (_, r) -> r.all { it !in notCategorized } }
            if (independent.isEmpty()) {
                // if no such then look for circular dependencies (i in allRules[i])
                val circular = rules.filter { (ix, r) -> ix in r }
                val circularIxs = circular.keys
                val selfSufficient = circular.all { (_, r) -> r.all { it in circularIxs || it !in notCategorized } }
                // idk there might be some more complicated cases that i have not encountered yet
                if (circular.isNotEmpty())
                    hasLoops = true
                if (circular.isNotEmpty() && selfSufficient) {
                    addRank(circularIxs)
                } else {
                    Log.w("BaseCircleGroup", "cannot fully rank the circles")
                    break
                }
            } else {
                addRank(independent.keys)
            }
        }
        return hasLoops to ranks
    }

    protected fun computePairs(): Set<Pair<Ix, Ix>> {
        val allRules: List<List<Ix>> = // ix => rule
            attrs.map { it.rule }
        val pairs = mutableMapOf<Ix, Ix>()
        fun pairUp(a: Ix, b: Ix) {
            pairs[a] = b
            pairs[b] = a
        }
        _ranks.second.forEach { ixs -> // NOTE: the traverse order is unimportant
            ixs.map { allRules[it] }.distinct().forEach { rule ->
                when (rule.size) {
                    0 -> Unit
                    2 -> pairUp(rule.first(), rule.last())
                    else -> {
                        if (rule.first() == rule.last()) {
                            var normalizedRule = rule.subList(1, rule.size-1)
                            if (normalizedRule.first() == normalizedRule.last()) {
                                while (normalizedRule.first() == normalizedRule.last())
                                    normalizedRule = normalizedRule.subList(1, normalizedRule.size-1)
                            }
                            normalizedRule.chunked(2).forEach {
                                if (it.size == 2)
                                    pairUp(it[0], it[1])
                            }
                        } else
                            rule.chunked(2).forEach {
                                if (it.size == 2)
                                    pairUp(it[0], it[1])
                            }
                    }
                }
            }
        }
        return pairs
            .asIterable()
            .filter { (a, b) -> a < b }
            .map { (a, b) -> a to b }
            .toSet()
    }

    override fun changeAngularSpeed(factor: Float) {
        for ((aIx, bIx) in enginePairs) {
            val a = figures[aIx]
            val b = figures[bIx]
            a.changeAngle(b, factor.toDouble())
            set(aIx, a)
        }
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
        if (attrs[i].fill && textures.containsKey(i))
            drawTextureInCircle(i, canvas)
        else
            canvas.drawCircle(x, y, r, paints[i])
        borderPaints.get(i)?.let { borderPaint ->
            canvas.drawCircle(x, y, r, borderPaint)
        }
    }

    protected inline fun drawTextureInCircle(i: Ix, canvas: Canvas) {
        val x = xs[i].toFloat()
        val y = ys[i].toFloat()
        val r = rs[i].toFloat()
        val size = ceil(2*r).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val tmpCanvas = Canvas(bitmap)
        val texture = textures[i]!!
        tmpCanvas.drawBitmap(texture, 0f, 0f, null)
        tmpCanvas.drawCircle(r, r, r, maskPaint)
        canvas.drawBitmap(bitmap, x-r, y-r, null)
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
        if (!attr.visible) {
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
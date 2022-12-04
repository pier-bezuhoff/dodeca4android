@file:Suppress("NOTHING_TO_INLINE")

package com.pierbezuhoff.dodeca.data

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.SweepGradient
import android.util.Log
import android.util.SparseArray
import com.pierbezuhoff.dodeca.utils.consecutiveGroupBy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.collections.set
import kotlin.math.abs
import kotlin.math.sqrt

typealias Ix = Int
typealias Ixs = IntArray
typealias IxTable = List<Ix> // Ix to Ix correspondence
typealias Rule = List<Ix>
typealias Part = List<Ix>
typealias Pole = Vector4

// NOTE: Float matrices/arrays are indeed too inaccurate for proj operations
// MAYBE: use nd4j (>200MB) or ejml (seems to be lighter and also optimized
internal class ProjectiveCircles(
    figures: List<CircleFigure>,
    private val paint: Paint,
    private val sphereRadius: Double
) : SuspendableCircleGroup {
    // static
    private val initialPoles: List<Pole> // poles of all initial circles
    private val partsOfRules: List<Ixs> // (unique) rule index: int array of parts' indices
    private val rulesForCircles: IxTable // circle index: (unique) rule index
    private val rulesForParts: IxTable // part index: (unique) rule index
    private val nRules: Int // only counting unique ones
    private val nParts: Int // nParts >= nRules
    private val size: Int = figures.size // size = nCircles >= nRules
    private val uniqueRules: List<Rule> // TMP
    // dynamic
    private val parts: Array<Matrix44> // unique parts of rules (can contain each other)
    private val rules: Array<Matrix44> // unique rules
    private val cumulativeRules: Array<Matrix44> // each update: cum. rule = rule * cum. rule
    private val poles: Array<Pole>
    private val xs: DoubleArray = DoubleArray(size)
    private val ys: DoubleArray = DoubleArray(size)
    private val rs: DoubleArray = DoubleArray(size)
    private val attrs: Array<FigureAttributes> = Array(size) { i ->
        figures[i].run {
            FigureAttributes(color, fill, rule, borderColor)
        }
    }
    private val shownIndices: MutableList<Ix> = attrs
        .mapIndexed { i, attr -> i to attr }
        .filter { it.second.show }
        .map { it.first }
        .toMutableList()
    private val paints: Array<Paint> = attrs.map {
        Paint(paint).apply {
            color = it.color
            style =
                if (it.fill) Paint.Style.FILL_AND_STROKE
                else Paint.Style.STROKE
        }
    }.toTypedArray()
    private val defaultBorderPaint = Paint(paint)
        .apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
        }
    private val borderPaints: SparseArray<Paint> = SparseArray<Paint>()
        .apply {
            attrs.forEachIndexed { i, attr ->
                if (attr.borderColor != null && attr.fill && attr.show)
                    append(i, Paint(defaultBorderPaint).apply { color = attr.borderColor })
                // Q: ^^^ is it correct?
            }
        }
    override val defaultPaint: Paint = paint
    override val figures: List<CircleFigure>
        get() {
            applyAllMatrices()
            return (0 until size).map { i ->
                val (color, fill, rule, borderColor) = attrs[i]
                CircleFigure(xs[i], ys[i], rs[i], color, fill, rule, borderColor)
            }
        }

    init {
        val symbolicRules = figures.map {
            val r = it.rule?.trimStart('n') ?: ""
            r.reversed().map { c -> c.digitToInt() }
        }
        uniqueRules = symbolicRules.distinct()
        nRules = uniqueRules.size
        rulesForCircles = symbolicRules.map { uniqueRules.indexOf(it) }
        val partsRules = mutableMapOf<Part, Rule>()
        val ruleSplits = mutableListOf<List<Part>>() // rule index: list of parts comprising it
        for (rule in uniqueRules) {
            val split = rule.consecutiveGroupBy { cIx -> symbolicRules[cIx] }
            ruleSplits.add(split.map { it.second }) // splitting into mono-rule'd parts
            for ((r, part) in split)
                partsRules[part] = r
        }
        val symbolicParts = partsRules.keys.sortedBy { it.joinToString { d -> d.toString() } }
        nParts = symbolicParts.size
        // part index: symbolic rule
        val rules4parts = symbolicParts.map { partsRules[it] }
        rulesForParts = rules4parts.map { uniqueRules.indexOf(it) }
        // rule index: list of part indices
        val ruleBlueprints = ruleSplits.map { split -> split.map { part -> symbolicParts.indexOf(part) } }
        partsOfRules = ruleBlueprints.map { it.toIntArray() }

        // TMP: traceback
        uniqueRules.forEachIndexed { i, r ->
            val cIxs = rulesForCircles.withIndex().filter { (_, rIx) -> rIx == i }.joinToString(",") { (cIx, _) -> "#$cIx" }
            Log.i(TAG, "${r.joinToString("")}: $cIxs")
        }
        val partsString = symbolicParts.joinToString { it.joinToString("") }
        Log.i(TAG, "parts: $partsString")

        // calc coordinate repr
        initialPoles = figures.map { circle2pole(it, sphereRadius) }
        poles = initialPoles.map { it.copy() }.toTypedArray()
        val pivotIndices: Set<Ix> = uniqueRules.flatten().toSet()
        val pivots = mutableMapOf<Ix, Matrix44>()
        for (i in pivotIndices) {
            val pole = circle2pole(figures[i], sphereRadius)
            pivots[i] = pole2matrix(pole, sphereRadius)
        }
        parts = symbolicParts.map { it.map { i -> pivots[i]!! }.product() }.toTypedArray() // index out of bounds error
        rules = ruleBlueprints.map { it.map { i -> parts[i] }.product() }.toTypedArray()
        cumulativeRules = rules.map { I44() }.toTypedArray()
        applyMatrices()
//        val good = figures.all { f -> // TMP
//            val (x,y,r) = pole2circle(circle2pole(f))
////            Log.i(TAG, "$f\t-> ($x, $y), r=$r")
//            listOf(f.x to x, f.y to y, f.radius to r).all { (v0, v) ->
//                abs(v - v0) < 1e-8
//            }
//        }
//        assert(good) { "!id" }
//        figures.forEachIndexed { i, f ->
//            xs[i] = f.x
//            ys[i] = f.y
//            rs[i] = f.radius
//        }
    }

    private fun testRule(cIx: Ix) {
        applyAllMatrices()
        val c = Circle(xs[cIx], ys[cIx], rs[cIx])
        val rIx = rulesForCircles[cIx]
        val r = uniqueRules[rIx]
        for (ix in r)
            // invert
        TODO()
    }

    private inline fun straightUpdate() {
        cumulativeRules.zip(rules).forEachIndexed { i, (cr, r) ->
            cumulativeRules[i] = mmult(r, cr)
        }
        parts.zip(rulesForParts).forEachIndexed { i, (p, rIx) ->
            val r = rules[rIx]
            parts[i] = mmult(r, mmult(p, r.inverse()))
        }
        partsOfRules.forEachIndexed { rIx, ps ->
            rules[rIx] = ps.map { parts[it] }.product()
        }
    }

    private fun reverseUpdate() {
        straightUpdate() // TMP
    }

    // most likely the bottleneck
    private inline fun applyMatrices() {
        shownIndices.forEach { cIx ->
            poles[cIx] = vmult(cumulativeRules[rulesForCircles[cIx]], initialPoles[cIx])
//            val (cx, cy, r) = pole2circle(pole) // inlined to escape type conversion etc.
            val (wx,wy,wz,w0) = poles[cIx]
//            Log.i(TAG, "#$cIx: ($wx\t$wy\t$wz\t$w0)")
            val w = w0 * sphereRadius
            val x = wx/w
            val y = wy/w
            val z = wz/w
            val nz = 1 - z
            xs[cIx] = x/nz * sphereRadius
            ys[cIx] = y/nz * sphereRadius
            rs[cIx] = sqrt(x*x + y*y + z*z - 1)/abs(nz) * sphereRadius
//            Log.i(TAG, "#$cIx: (${xs[cIx]}, ${ys[cIx]}), r=${rs[cIx]}")
        }
        rules.forEachIndexed { i, m ->
//            Log.i(TAG, "rule '${uniqueRules[i].joinToString("")}':\n${m.showAsM44()}")
        }
    }

    // most likely the bottleneck
    private inline fun applyAllMatrices() {
        initialPoles.forEachIndexed { cIx, pole ->
            poles[cIx] = vmult(cumulativeRules[rulesForCircles[cIx]], pole)
        }
        poles.forEachIndexed { cIx, pole ->
//            val (cx, cy, r) = pole2circle(pole) // inlined to escape type conversion etc.
            val (wx,wy,wz,w0) = pole
            val w = w0 * sphereRadius
            val x = wx/w
            val y = wy/w
            val z = wz/w
            val nz = 1 - z
            xs[cIx] = x/nz * sphereRadius
            ys[cIx] = y/nz * sphereRadius
            rs[cIx] = sqrt(x*x + y*y + z*z - 1)/abs(nz) * sphereRadius
        }
    }

    override fun get(i: Int): CircleFigure {
        applyAllMatrices()
        val (color, fill, rule, borderColor) = attrs[i]
        return CircleFigure(xs[i], ys[i], rs[i], color, fill, rule, borderColor)
    }

    override fun set(i: Int, figure: CircleFigure) {
        val wasShown = attrs[i].show
        with(figure) {
            assert(abs(xs[i] - x) + abs(ys[i] - y) + abs(rs[i] - radius) < 1e-6) {
                "cannot handle coordinate changes yet"
            }
            assert(rule == attrs[i].rule) { "cannot handle rule change yet" }
            attrs[i] = FigureAttributes(color, fill, rule, borderColor)
            paints[i] = Paint(paint).apply {
                color = figure.color
                style = if (fill) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
            }
            if (show && borderColor != null && fill)
                borderPaints.append(i, Paint(defaultBorderPaint).apply { color = borderColor })
            else
                borderPaints.delete(i)
            if (wasShown && !show)
                shownIndices.remove(i)
            else if (!wasShown && show) {
                shownIndices.add(i)
                shownIndices.sort()
            }
            Unit
        }
    }

    override fun update(reverse: Boolean) =
        _update(reverse)

    private inline fun _update(reverse: Boolean) {
        if (reverse)
            reverseUpdate()
        else
            straightUpdate()
        applyMatrices()
    }

    override fun updateTimes(times: Int, reverse: Boolean) {
        if (reverse) {
            repeat(times) { reverseUpdate() }
        } else {
            repeat(times) { straightUpdate() }
        }
        applyMatrices()
    }

    override fun draw(canvas: Canvas, shape: Shape) =
        _draw(canvas, shape)

    private inline fun _draw(canvas: Canvas, shape: Shape) {
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

    override fun drawTimes(times: Int, reverse: Boolean, canvas: Canvas, shape: Shape) {
        repeat(times) {
            _draw(canvas, shape)
            _update(reverse)
        }
        _draw(canvas, shape)
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

    override suspend fun suspendableUpdateTimes(times: Int, reverse: Boolean) {
        repeat(times) {
            withContext(Dispatchers.Default) {
                _update(reverse)
            }
        }
    }

    override suspend fun suspendableDrawTimes(times: Int, reverse: Boolean, canvas: Canvas, shape: Shape) {
        repeat(times) {
            withContext(Dispatchers.Default) {
                _draw(canvas, shape)
                _update(reverse)
            }
        }
        withContext(Dispatchers.Default) { _draw(canvas, shape) }
    }

    private inline fun drawCircle(i: Int, canvas: Canvas) {
        val x = xs[i].toFloat()
        val y = ys[i].toFloat()
        val r = rs[i].toFloat()
        canvas.drawCircle(x, y, r, paints[i])
        borderPaints.get(i)?.let { borderPaint ->
            canvas.drawCircle(x, y, r, borderPaint)
        }
    }

    private inline fun drawSquare(i: Int, canvas: Canvas) {
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

    private inline fun drawCross(i: Int, canvas: Canvas) {
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

    private inline fun drawVerticalBar(i: Int, canvas: Canvas) {
        val x = xs[i].toFloat()
        val y = ys[i].toFloat()
        val r = rs[i].toFloat()
        canvas.drawLine(x, y - r, x, y + r, paints[i])
    }

    private inline fun drawHorizontalBar(i: Int, canvas: Canvas) {
        val x = xs[i].toFloat()
        val y = ys[i].toFloat()
        val r = rs[i].toFloat()
        canvas.drawLine(x - r, y, x + r, y, paints[i])
    }

    private fun drawCircleOverlay(i: Int, canvas: Canvas, bold: Boolean = false) {
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

    private fun drawSelectedCircleOverlay(i: Int, canvas: Canvas) {
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

    companion object {
        private const val TAG = "ProjectiveCircles"
    }

}
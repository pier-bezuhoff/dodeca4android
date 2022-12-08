@file:Suppress("NOTHING_TO_INLINE")

package com.pierbezuhoff.dodeca.data.circlegroup

import android.graphics.Canvas
import android.graphics.Paint
import android.util.Log
import com.pierbezuhoff.dodeca.data.CircleFigure
import com.pierbezuhoff.dodeca.data.FigureAttributes
import com.pierbezuhoff.dodeca.data.Shape
import com.pierbezuhoff.dodeca.utils.consecutiveGroupBy
import com.pierbezuhoff.dodeca.utils.filteredIndices
import kotlin.collections.set
import kotlin.math.abs
import kotlin.math.sqrt

typealias IxTable = List<Ix> // Ix to Ix correspondence
typealias Rule = List<Ix>
typealias Part = List<Ix>
typealias Pole = Vector4

// NOTE: Float matrices/arrays are indeed too inaccurate for proj operations
// MAYBE: use nd4j (>200MB) or ejml (seems to be lighter and also optimized
// TODO: time apply matrices vs update
internal class ProjectiveCircles(
    figures: List<CircleFigure>,
    paint: Paint,
    private val sphereRadius: Double
) : BaseCircleGroup(figures, paint) {
    // static
    private val initialPoles: List<Pole> // poles of all initial circles
    private val partsOfRules: List<Ixs> // (unique) rule index: int array of parts' indices
    private val rulesForCircles: IxTable // circle index: (unique) rule index
    private val rulesForParts: IxTable // part index: (unique) rule index
    private val nRules: Int // only counting unique ones
    private val nParts: Int // nParts >= nRules
    // size = nCircles >= nRules
    private val symbolicRules: List<Rule> // unique only
    private val symbolicParts: List<Part> // unique only
    private val rankedRulesAndParts: List<Pair<List<Ix>, List<Ix>>> by lazy { computeRankedRulesAndParts() }

    // dynamic
    private val parts: Array<Matrix44> // unique parts of rules (can contain each other)
    private val rules: Array<Matrix44> // unique rules
    private val cumulativeRules: Array<Matrix44> // each update: cum. rule = rule * cum. rule
    private val poles: Array<Pole>
    override val xs: DoubleArray = DoubleArray(size)
    override val ys: DoubleArray = DoubleArray(size)
    override val rs: DoubleArray = DoubleArray(size)
    override val figures: List<CircleFigure>
        get() {
            applyAllMatrices()
            return (0 until size).map { i ->
                val (color, fill, rule, borderColor) = attrs[i]
                CircleFigure(xs[i], ys[i], rs[i], color, fill, rule, borderColor)
            }
        }

    init {
        val allSymbolicRules = figures.map {
            val r = it.rule?.trimStart('n') ?: ""
            r.reversed().map { c -> c.digitToInt() }
        }
        symbolicRules = allSymbolicRules.distinct()
        nRules = symbolicRules.size
        rulesForCircles = allSymbolicRules.map { symbolicRules.indexOf(it) }
        val partsRules = mutableMapOf<Part, Rule>()
        val ruleSplits = mutableListOf<List<Part>>() // rule index: list of parts comprising it
        for (rule in symbolicRules) {
            val split = rule.consecutiveGroupBy { cIx -> allSymbolicRules[cIx] }
            ruleSplits.add(split.map { it.second }) // splitting into mono-rule'd parts
            for ((r, part) in split)
                partsRules[part] = r
        }
        symbolicParts = partsRules.keys.sortedBy { it.joinToString { d -> d.toString() } }
        nParts = symbolicParts.size
        // part index: symbolic rule
        val rules4parts = symbolicParts.map { partsRules[it] }
        rulesForParts = rules4parts.map { symbolicRules.indexOf(it) }
        // rule index: list of part indices
        val ruleBlueprints = ruleSplits.map { split -> split.map { part -> symbolicParts.indexOf(part) } }
        partsOfRules = ruleBlueprints.map { it.toIntArray() }

        // TMP: traceback
        symbolicRules.forEachIndexed { i, r ->
            val cIxs = rulesForCircles.filteredIndices { it == i }.joinToString(",") { cIx -> "#$cIx" }
            Log.i(TAG, "${r.joinToString("")}: $cIxs")
        }
        val partsString = symbolicParts.joinToString { it.joinToString("") }
        Log.i(TAG, "parts: $partsString")

        // calc coordinate repr
        initialPoles = figures.map { circle2pole(it, sphereRadius) }
        poles = initialPoles.map { it.copy() }.toTypedArray()
        val pivotIndices: Set<Ix> = symbolicRules.flatten().toSet()
        val pivots = mutableMapOf<Ix, Matrix44>()
        for (i in pivotIndices) {
            val pole = circle2pole(figures[i], sphereRadius)
            pivots[i] = pole2matrix(pole, sphereRadius)
        }
        parts = symbolicParts.map { it.map { i -> pivots[i]!! }.product() }.toTypedArray() // index out of bounds error
        rules = ruleBlueprints.map { it.map { i -> parts[i] }.product() }.toTypedArray()
        cumulativeRules = rules.map { I44() }.toTypedArray()
        applyMatrices()
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

    private fun computeRankedRulesAndParts(): List<Pair<List<Ix>, List<Ix>>>  {
        val ranks = _ranks.second
        val ix2rank = ranks
            .mapIndexed { rank, ixs -> ixs.map { it to rank } }
            .flatten()
            .associate { it }
        val rIx2rank = symbolicRules.map { rule ->
            rule.maxOf { ix2rank[it]!! }
        }
        val pIx2rank = symbolicParts.map { part ->
            part.maxOf { ix2rank[it]!! }
        }
        return ranks.indices.map { rank ->
            val rs = rIx2rank.filteredIndices { it == rank }
            val ps = pIx2rank.filteredIndices { it == rank }
            rs to ps
        }
    }

    private fun reverseUpdate() {
        cumulativeRules.zip(rules).forEachIndexed { i, (cr, r) ->
            cumulativeRules[i] = mmult(r, cr)
        }
        rankedRulesAndParts.forEach { (rIxs, pIxs) ->
            pIxs.forEach { pIx ->
                val r = rules[rulesForParts[pIx]]
                parts[pIx] = mmult(r.inverse(), mmult(parts[pIx], r))
            }
            rIxs.forEach { rIx ->
                rules[rIx] = partsOfRules[rIx].map { parts[it] }.product()
            }
        }
        // MAYBE: if circular switch to just reverse order
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
//        rules.forEachIndexed { i, m ->
//            Log.i(TAG, "rule '${uniqueRules[i].joinToString("")}':\n${m.showAsM44()}")
//        }
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

    override fun get(i: Ix): CircleFigure {
        applyAllMatrices()
        val (color, fill, rule, borderColor) = attrs[i]
        return CircleFigure(xs[i], ys[i], rs[i], color, fill, rule, borderColor)
    }

    override fun set(i: Ix, figure: CircleFigure) {
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
                shownIndices = shownIndices.toMutableSet().run {
                    remove(i)
                    toIntArray()
                }
            else if (!wasShown && show) {
                shownIndices = shownIndices.toMutableSet().run {
                    add(i)
                    toIntArray() // removed .sort()
                }
            }
            Unit
        }
    }

    override fun update(reverse: Boolean) =
        _update(reverse)

    // MAYBE: inline
    override fun _update(reverse: Boolean) {
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

    companion object {
        private const val TAG = "ProjectiveCircles"
    }
}
package com.pierbezuhoff.dodeca.data

import android.graphics.Matrix
import android.graphics.Paint
import com.pierbezuhoff.dodeca.utils.consecutiveGroupBy

typealias Rule = List<Int>
typealias Part = List<Int>

// TMP: abstract
// NOTE: use nd4j for performance
// https://github.com/deeplearning4j/deeplearning4j/tree/master/nd4j
abstract class ProjectiveCircles(
    figures: List<CircleFigure>,
    private val paint: Paint
) : SuspendableCircleGroup {
    val parts: Array<Matrix> = emptyArray() // unique rules
    val rules: Array<Matrix> = emptyArray()
    val cumulativeRules: Array<Matrix> = emptyArray() // array of I[4x4]
    val poles: List<DoubleArray> = emptyList() // poles of all initial circles
    val partsOfRules: List<IntArray> = emptyList()
    val rulesForParts: IntArray = intArrayOf() //

    init {
        val symbolicRules = figures.map {
            val r = it.rule ?: ""
            r.reversed().map { c -> c.digitToInt() }
        }
        val uniqueRules = symbolicRules.distinct()
        val partsRules = mutableMapOf<Part, Rule>()
        val ruleSplits = mutableListOf<List<Part>>() // rule index: list of parts comprising it
        for (rule in symbolicRules) {
            val split = rule.consecutiveGroupBy { cIx -> symbolicRules[cIx] }
            ruleSplits.add(split.map { it.second })
            for ((r, part) in split)
                partsRules[part] = r
        }
        val symbolicParts = partsRules.keys.sortedBy { it.joinToString { d -> d.toString() } }
        val rules4parts = symbolicParts.map { partsRules[it] }
        val ruleBlueprints = ruleSplits.map { split -> split.map { part -> symbolicParts.indexOf(part) } }
        val pivotIndices = uniqueRules.flatten().toSet()
        // poles = mutableListOf<4-vectors>()
        // pivots = mutableListOf<4x4 matrices>()
        for (i in pivotIndices) {
            // circle at i to pole -> poles
            // pole to matrix -> pivots
        }
        // parts = parts.map { it.map { i -> pivots[i] }.fold(*, I[4x4]) }
        // cumulativeRules = rules.map { I[4x4] }
    }

    fun update() {
        // cum. rules = rules.zip(cum. rules, *)
        // parts = rules4parts.zip(parts) { rIx, p -> rules[rIx] * p * rules[rIx].inverse() }
        // rules = ruleBlueprints.map { it.map { i -> parts[i] }.fold(*) }

        // poles = apply cum. rules to poles0
        // circles = poles 2 circles
    }
}
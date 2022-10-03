package com.pierbezuhoff.dodeca.data

import android.graphics.Matrix

// TMP: abstract
// NOTE: use nd4j for performance
// https://github.com/deeplearning4j/deeplearning4j/tree/master/nd4j
abstract class ProjectiveCircles(
) : SuspendableCircleGroup {
    val parts: Array<Matrix> = emptyArray() // unique rules
    val rules: Array<Matrix> = emptyArray()
    val cumulativeRules: Array<Matrix> = emptyArray() // array of I[4x4]
    val poles: List<DoubleArray> = emptyList() // poles of all initial circles
    val partsOfRules: List<IntArray> = emptyList()
    val rulesForParts: IntArray = intArrayOf() //

    init {
        // circles -> poles
        // circles -> proj. transf. matrices
        // all rules -> all parts
        // all rules, all parts -> unique parts, partsOfRules, rulesForParts
        // proj. matrices, unique parts -> parts
        // parts -> rules
    }
}
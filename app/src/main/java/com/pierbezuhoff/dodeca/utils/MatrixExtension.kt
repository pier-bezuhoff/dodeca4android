package com.pierbezuhoff.dodeca.utils

import android.graphics.Matrix
import org.apache.commons.math3.complex.Complex

/*
* T(dx, dy) = [1 0 dx] -- translate
*             [0 1 dy]
*             [0 0 1]
*
* S(sx, sy) = [sx 0 0] -- scale
*             [0 sy 0]
*             [0 0  1]
*/
fun Matrix.move(z: Complex): Complex {
    val (x, y) = z.asFF()
    val array = floatArrayOf(x, y)
    this.mapPoints(array)
    return ComplexFF(array[0], array[1])
}
val Matrix.values: FloatArray get() {
    val array = FloatArray(9).apply { fill(0f) }
    getValues(array)
    return array
}
val Matrix.dx: Float get() = values[2]
val Matrix.dy: Float get() = values[5]
val Matrix.sx: Float get() = values[0]
val Matrix.sy: Float get() = values[4]

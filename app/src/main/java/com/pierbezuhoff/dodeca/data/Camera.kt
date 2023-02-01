package com.pierbezuhoff.dodeca.data

import org.jetbrains.kotlinx.multik.api.identity
import org.jetbrains.kotlinx.multik.api.linalg.dot
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.ndarray
import org.jetbrains.kotlinx.multik.api.ndarrayOf
import org.jetbrains.kotlinx.multik.ndarray.data.D1
import org.jetbrains.kotlinx.multik.ndarray.data.D2
import org.jetbrains.kotlinx.multik.ndarray.data.MultiArray
import org.jetbrains.kotlinx.multik.ndarray.data.get
import org.jetbrains.kotlinx.multik.ndarray.operations.div
import org.jetbrains.kotlinx.multik.ndarray.operations.map
import org.jetbrains.kotlinx.multik.ndarray.operations.sum
import org.jetbrains.kotlinx.multik.ndarray.operations.times
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

typealias Vector3 = MultiArray<Double, D1>
typealias TransformationMatrix = MultiArray<Double, D2> // 4x4

// important commands: right, up; Rx, Rz
// Ox, Oy, Oz
// NOTE: forward does nothing w/ ortho-proj. except filters visibility
val RIGHT: Vector3 = mk.ndarray(mk[1.0, 0.0, 0.0])
val FORWARD: Vector3 = mk.ndarray(mk[0.0, 1.0, 0.0])
val UP: Vector3 = mk.ndarray(mk[0.0, 0.0, 1.0])


interface CoordinateSystem3D {
    val matrix: TransformationMatrix

    fun transform(v: Vector3): Vector3 {
        val w = mk.linalg.dot(matrix, mk.ndarrayOf(v[0], v[1], v[2], 1.0))
        return mk.ndarrayOf(w[0]/w[3], w[1]/w[3], w[2]/w[3])
    }

    val right: Vector3 get() = transform(RIGHT).normalized()
    val forward: Vector3 get() = transform(FORWARD).normalized()
    val up: Vector3 get() = transform(UP).normalized()

    // intrinsic motions
    fun moveRight(distance: Double) // = Tx
    fun moveForward(distance: Double) // = Ty
    fun moveUp(distance: Double) // = Tz
    fun rotatePitch(angle: Double) // elevation = Rx
    fun rotateRoll(angle: Double) // bank = Ry
    fun rotateYaw(angle: Double) // heading = Rz
    fun zoom(scale: Double)
}


/** 3D, ortho-proj */ // MAYBE: add FoV and perspective vs orthogonal
class Camera : CoordinateSystem3D {

    override var matrix: TransformationMatrix
        = mk.identity(4)
    // T * R * S

//    mk.ndarray(mk[
//        mk[a00, a01, a02, a03],
//        mk[a10, a11, a12, a13],
//        mk[a20, a21, a22, a23],
//        mk[a30, a31, a32, a33]
//    ])

    private fun postApply(m: TransformationMatrix) {
        matrix = mk.linalg.dot(m, matrix)
    }

    private fun move(shift: Vector3) {
        val shiftMatrix = mk.ndarray(mk[
            mk[1.0, 0.0, 0.0, shift[0]],
            mk[0.0, 1.0, 0.0, shift[1]],
            mk[0.0, 0.0, 1.0, shift[2]],
            mk[0.0, 0.0, 0.0, 1.0]
        ])
        postApply(shiftMatrix)
    }

    // axis should be normalized
    private fun rotate(axis: Vector3, angle: Double) {
        val c = cos(angle)
        val s = sin(angle)
        val mc = 1 - c
        val x = axis[0]
        val y = axis[1]
        val z = axis[2]
        // c * I + (1-c) * [tensor square] + s * [antisym.]
        val rotationMatrix = mk.ndarray(mk[
            mk[c + x*x*mc,   x*y*mc - z*s, x*z*mc + y*s, 0.0],
            mk[y*x*mc + z*s, c + y*y*mc,   y*z*mc - x*s, 0.0],
            mk[z*x*mc - y*s, z*y*mc + x*s, c + z*z*mc,   0.0],
            mk[0.0, 0.0, 0.0, 1.0]
        ])
        postApply(rotationMatrix)
    }

    override fun moveRight(distance: Double) = move(right * distance)
    override fun moveForward(distance: Double) = move(forward * distance)
    override fun moveUp(distance: Double) = move(up * distance)
    override fun rotatePitch(angle: Double) = rotate(right, angle)
    override fun rotateRoll(angle: Double) = rotate(forward, angle)
    override fun rotateYaw(angle: Double) = rotate(up, angle)
    override fun zoom(scale: Double) {
        val scaleMatrix = mk.ndarray(mk[
            mk[scale, 0.0, 0.0, 0.0],
            mk[0.0, scale, 0.0, 0.0],
            mk[0.0, 0.0, scale, 0.0],
            mk[0.0, 0.0, 0.0, 1.0]
        ])
        postApply(scaleMatrix)
    }
}


internal inline fun Vector3.normalized(): Vector3 {
    val norm: Double = sqrt(map { x -> x*x }.sum())
    return this/norm
}
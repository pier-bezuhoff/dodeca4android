package com.pierbezuhoff.dodeca.data

import com.pierbezuhoff.dodeca.data.circlegroup.Vector4
import com.pierbezuhoff.dodeca.data.circlegroup.component1
import com.pierbezuhoff.dodeca.data.circlegroup.component2
import com.pierbezuhoff.dodeca.data.circlegroup.component3
import com.pierbezuhoff.dodeca.data.circlegroup.v3ToV4
import com.pierbezuhoff.dodeca.data.circlegroup.v4ToV3
import org.jetbrains.kotlinx.multik.api.identity
import org.jetbrains.kotlinx.multik.api.linalg.dot
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.ndarray
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

    fun transform(v: Vector3): Vector3 =
        mk.linalg.dot(matrix, v.v3ToV4()).v4ToV3()

    fun transform44(v: Vector4) : Vector4 =
        mk.linalg.dot(matrix, v)

    fun project2D(v: Vector3): Pair<Double, Double>?

    val right: Vector3 get() = transform(RIGHT).normalized()
    val forward: Vector3 get() = transform(FORWARD).normalized()
    val up: Vector3 get() = transform(UP).normalized()

    fun postApply(m: TransformationMatrix)

    fun move(shift: Vector3) {
        val shiftMatrix = mk.ndarray(mk[
            mk[1.0, 0.0, 0.0, shift[0]],
            mk[0.0, 1.0, 0.0, shift[1]],
            mk[0.0, 0.0, 1.0, shift[2]],
            mk[0.0, 0.0, 0.0, 1.0]
        ])
        postApply(shiftMatrix)
    }

    // axis should be normalized
    fun rotate(axis: Vector3, angle: Double) {
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

    // intrinsic motions
    fun moveRight(distance: Double) // = Tx
    fun moveForward(distance: Double) // = Ty
    fun moveUp(distance: Double) // = Tz
    fun rotatePitch(angle: Double) // elevation = Rx
    fun rotateRoll(angle: Double) // bank = Ry
    fun rotateYaw(angle: Double) // heading = Rz
    fun zoom(scale: Double) {
        val scaleMatrix = mk.ndarray(mk[
            mk[scale, 0.0, 0.0, 0.0],
            mk[0.0, scale, 0.0, 0.0],
            mk[0.0, 0.0, scale, 0.0],
            mk[0.0, 0.0, 0.0, 1.0]
        ])
        postApply(scaleMatrix)
    }

    fun doTransformation(transformation: Transformation) {
        when (transformation) {
            is Transformation.Translation -> move(transformation.shift)
            is Transformation.Rotation -> rotate(transformation.axis, transformation.angle)
            is Transformation.Zoom -> zoom(transformation.scale)
        }
    }

    sealed class Transformation {
        open class Translation(val shift: Vector3) : Transformation()
        data class Right(val distance: Double) : Translation(RIGHT * distance)
        data class Forward(val distance: Double) : Translation(FORWARD * distance)
        data class Up(val distance: Double) : Translation(UP * distance)

        open class Rotation(val axis: Vector3, open val angle: Double) : Transformation()
        data class Pitch(override val angle: Double) : Rotation(RIGHT, angle)
        data class Roll(override val angle: Double) : Rotation(FORWARD, angle)
        data class Yaw(override val angle: Double) : Rotation(UP, angle)

        data class Zoom(val scale: Double) : Transformation()
    }
}

/**
 * T     * R     * S ->
 * rel T * rel R * rel S
 * */
class OrthogonalCamera3D : CoordinateSystem3D {
    // NOTE: idt it's correct, rel. T/R are weird

    private val orthoProjMatrix: TransformationMatrix =
        mk.ndarray(mk[
            mk[1.0, 0.0, 0.0, 0.0],
            mk[0.0, 0.0, 0.0, 0.0],
            mk[0.0, 0.0, 1.0, 0.0],
            mk[0.0, 0.0, 0.0, 1.0]
        ])

    override var matrix: TransformationMatrix
        = mk.identity(4)

    override fun project2D(v: Vector3): Pair<Double, Double>? {
        val u = transform(v)
        return if (u[1] < 0.0)
            null
        else {
            val (x, _, z) = mk.linalg.dot(orthoProjMatrix, u)
            x to z
        }
    }

    override fun postApply(m: TransformationMatrix) {
        matrix = mk.linalg.dot(m, matrix)
    }

    override fun moveRight(distance: Double) = move(right * distance)
    override fun moveForward(distance: Double) = move(forward * distance)
    override fun moveUp(distance: Double) = move(up * distance)
    override fun rotatePitch(angle: Double) = rotate(right, angle)
    override fun rotateRoll(angle: Double) = rotate(forward, angle)
    override fun rotateYaw(angle: Double) = rotate(up, angle)
}


/** camera @ (0 0 0),
 * near plane @ y = near.
 * T         * R         * S ->
 * abs T.inv * abs R.inv * abs S
 * */
class PerspectiveCamera3D(private val near: Double) : CoordinateSystem3D {

    override var matrix: TransformationMatrix =
        mk.identity(4)

    init {
        moveForward(near)
        moveForward(-1.5*near)
    }

    private val perspProjMatrix: TransformationMatrix
        = mk.ndarray(mk[
        mk[1.0, 0.0, 0.0, 0.0],
        mk[0.0, 0.0, 0.0, 0.0],
        mk[0.0, 0.0, 1.0, 0.0],
        mk[0.0, 1/near, 0.0, 1.0]
    ])

    override fun project2D(v: Vector3): Pair<Double, Double>? {
        val u = transform44(v.v3ToV4())
        return if (u[1] < near)
            null
        else {
            val (x, _, z) = mk.linalg.dot(perspProjMatrix, u)
            x to z
        }
    }

    override fun postApply(m: TransformationMatrix) {
        matrix = mk.linalg.dot(m, matrix)
    }

    override fun moveRight(distance: Double) = move(RIGHT * -distance)
    override fun moveForward(distance: Double) = move(FORWARD * -distance)
    override fun moveUp(distance: Double) = move(UP * -distance)
    override fun rotatePitch(angle: Double) = rotate(RIGHT, -angle)
    override fun rotateRoll(angle: Double) = rotate(FORWARD, -angle)
    override fun rotateYaw(angle: Double) = rotate(UP, -angle)
}


internal inline fun Vector3.normalized(): Vector3 {
    val norm: Double = sqrt(map { x -> x*x }.sum())
    return this/norm
}

// template
//    mk.ndarray(mk[
//        mk[a00, a01, a02, a03],
//        mk[a10, a11, a12, a13],
//        mk[a20, a21, a22, a23],
//        mk[a30, a31, a32, a33]
//    ])


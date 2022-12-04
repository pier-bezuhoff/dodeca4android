@file:Suppress("NOTHING_TO_INLINE")

package com.pierbezuhoff.dodeca.data

import com.pierbezuhoff.dodeca.utils.component1
import com.pierbezuhoff.dodeca.utils.component2
import org.jetbrains.kotlinx.multik.api.identity
import org.jetbrains.kotlinx.multik.api.linalg.dot
import org.jetbrains.kotlinx.multik.api.linalg.inv
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.ndarray
import org.jetbrains.kotlinx.multik.ndarray.data.D1
import org.jetbrains.kotlinx.multik.ndarray.data.D2
import org.jetbrains.kotlinx.multik.ndarray.data.MultiArray
import org.jetbrains.kotlinx.multik.ndarray.data.get
import org.jetbrains.kotlinx.multik.ndarray.operations.map
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

typealias Vector4 = MultiArray<Double, D1>
typealias Matrix44 = MultiArray<Double, D2>

//internal const val SPHERE_RADIUS = 1000.0
// sphere: (0,0,0), R=1 // MAYBE: R=~1000 is better for accuracy
// proj from the north pole (0,0,1)
// onto plane z=0
// for linear (projective) functions: f(R, x) = R * f(1, x/R)

fun circle2pole(circle: Circle, sphereRadius: Double): Vector4 {
    val (x0, y0) = circle.center
    val x = x0/sphereRadius
    val y = y0/sphereRadius
    val r = circle.radius/sphereRadius
    val tx = x + r
    val ty = y
    // T = any point on the circle
    val d = 1 + tx*tx + ty*ty
    // S = stereographic image of T on the sphere
    val sx = 2*tx/d
    val sy = 2*ty/d
    val sz = (d - 2)/d
    // parameter of the pole on the line thru the circle.center and the sphere's north
    // pole = k*C + (1 - k)*N
    val k = (1 - sz)/(x*sx + y*sy - sz) // P lies on the tangent plane at S <=> PS is perp. to OS
    val px = k*x * sphereRadius
    val py = k*y * sphereRadius
    val pz = (1 - k) * sphereRadius
    return mkVector44(px, py, pz)
//        .also {
//        Log.i(TAG, "($x0, $y0), r=${circle.radius}; R = $sphereRadius\t-> ${it.showAsV3()}")
//    }
}

// NOTE: inlined for performance
fun pole2circle(pole: Vector4, sphereRadius: Double): MultiArray<Double, D1> {
    val (wx,wy,wz,w) = pole
    val x = wx/w /sphereRadius
    val y = wy/w /sphereRadius
    val z = wz/w /sphereRadius
    val nz = 1 - z
    // st. proj. pole->center
    val cx = x/nz * sphereRadius
    val cy = y/nz * sphereRadius
    val op2 = x*x + y*y + z*z // op2 > 1 for *real* circles
    val r = sqrt(op2 - 1) / abs(nz) * sphereRadius // sqrt(op2-1) = segment of tangent
    return mk.ndarray(mk[cx, cy, r])
}

// assuming row-major ordered matrices
@Suppress("LocalVariableName")
fun pole2matrix(pole: Vector4, sphereRadius: Double): Matrix44 {
    val (wx,wy,wz,w0) = pole
    val w = w0 * sphereRadius
    val x = wx/w
    val y = wy/w
    val z = wz/w
    val a2 = x*x + y*y + z*z
    val a = sqrt(a2)
    val th = -atan2(z, x)
    val phi = -atan2(y, hypot(x, z))
    val Ry = mkMatrix44(
        cos(th),  0.0,   -sin(th), 0.0,
        0.0,  1.0, 0.0,   0.0,
        sin(th),  0.0,    cos(th), 0.0,
        0.0,  0.0, 0.0,   1.0
    )
    val Rz = mkMatrix44(
        cos(phi), -sin(phi), 0.0, 0.0,
        sin(phi),  cos(phi), 0.0, 0.0,
        0.0, 0.0,   1.0, 0.0,
        0.0, 0.0,   0.0, 1.0
    )
//    val M = mkMatrix44(
//        a2 + 1, 0.0,    0.0,    -2*a,
//        0.0,    1 - a2, 0.0,    0.0,
//        0.0,    0.0,    1 - a2, 0.0,
//        2*a,    0.0,    0.0,    -a2 - 1
//    )
    val k = sphereRadius
//    val S = mkMatrix44( // uniform scaling
//        k,       0.0, 0.0, 0.0,
//        0.0,     k,   0.0, 0.0,
//        0.0, 0.0,     k,   0.0,
//        0.0, 0.0, 0.0, 1.0
//    )
    val SMSinv = mkMatrix44(
        a2 + 1, 0.0,    0.0,    -2*a*k,
        0.0,    1 - a2, 0.0,    0.0,
        0.0,    0.0,    1 - a2, 0.0,
        2*a/k,  0.0,    0.0,    -a2 - 1
    ) // S perp R_ => res = Ry.inv * Rz.inv * SMSinv * Rz * Ry
//    Log.i(TAG, "${pole.showAsCircle()};\ta=$a, th=$th, phi=$phi")
    val result = listOf(
        Ry.inverse(), Rz.inverse(), SMSinv, Rz, Ry
    )//.map { Log.i(TAG, "*" + it.showAsM44()) ; it }
        .product()
//        .also { Log.i(TAG, "---> "+it.showAsM44()) }
    val descale = 1/abs(result.det()).pow(0.25)
    // scaling in order to not lose accuracy
    return result.map { it*descale }
//        .also {
//            Log.i(TAG, "descale: $descale\n =>${it.showAsM44()}")
//            assert(abs(abs(it.det()) - 1) < 1e-4) { "det=1" }
//        }
}


// NOTE: multik stores vectors as columns, generally uses row-column order
// multik helpers:

internal fun I44(): Matrix44 =
    mk.identity(4)

internal inline fun mkMatrix44(a00: Double, a01: Double, a02: Double, a03: Double, a10: Double, a11: Double, a12: Double, a13: Double, a20: Double, a21: Double, a22: Double, a23: Double, a30: Double, a31: Double, a32: Double, a33: Double,): Matrix44 =
    mk.ndarray(mk[
        mk[a00, a01, a02, a03],
        mk[a10, a11, a12, a13],
        mk[a20, a21, a22, a23],
        mk[a30, a31, a32, a33]
    ])

internal inline fun mkVector44(x: Double, y: Double, z: Double, w: Double = 1.0): Vector4 =
    mk.ndarray(mk[x, y, z, w])

internal inline fun mmult(a: Matrix44, b: Matrix44): Matrix44 =
    mk.linalg.dot(a, b)

// NOTE: is it column-vector? or row-vector?
internal inline fun vmult(a: Matrix44, v: Vector4): Vector4 =
    mk.linalg.dot(a, v)

internal fun Iterable<Matrix44>.product() : Matrix44 =
    fold(I44()) { a, b ->
        mk.linalg.dot(a, b)
    }

internal inline fun Matrix44.inverse(): Matrix44 =
    mk.linalg.inv(this)

// bruh, why do i have to do this
internal fun Matrix44.det(): Double {
    val m = this
    return (
        + m[0,0] * (m[1,1]*m[2,2]*m[3,3] + m[1,2]*m[2,3]*m[3,1] + m[1,3]*m[2,1]*m[3,2] - m[1,3]*m[2,2]*m[3,1] - m[1,2]*m[2,1]*m[3,3] - m[1,1]*m[2,3]*m[3,2])
        - m[1,0] * (m[0,1]*m[2,2]*m[3,3] + m[0,2]*m[2,3]*m[3,1] + m[0,3]*m[2,1]*m[3,2] - m[0,3]*m[2,2]*m[3,1] - m[0,2]*m[2,1]*m[3,3] - m[0,1]*m[2,3]*m[3,2])
        + m[2,0] * (m[0,1]*m[1,2]*m[3,3] + m[0,2]*m[1,3]*m[3,1] + m[0,3]*m[1,1]*m[3,2] - m[0,3]*m[1,2]*m[3,1] - m[0,2]*m[1,1]*m[3,3] - m[0,1]*m[1,3]*m[3,2])
        - m[3,0] * (m[0,1]*m[1,2]*m[2,3] + m[0,2]*m[1,3]*m[2,1] + m[0,3]*m[1,1]*m[2,2] - m[0,3]*m[1,2]*m[2,1] - m[0,2]*m[1,1]*m[2,3] - m[0,1]*m[1,3]*m[2,2])
        )
}

internal operator fun Vector4.component1(): Double = this[0]
internal operator fun Vector4.component2(): Double = this[1]
internal operator fun Vector4.component3(): Double = this[2]
internal operator fun Vector4.component4(): Double = this[3]

internal fun FloatArray.showAsList(): String =
    joinToString("\t", "(", ")") { "%.2f".format(it) }

internal fun Vector4.showAsV3(): String {
    val (x,y,z,w) = this
    return "(%.2f\t%.2f\t%.2f)".format(x/w, y/w, z/w)
}

internal fun Pole.showAsCircle(): String {
    val (x, y, r) = pole2circle(this, 1000.0)
    return "[(%.2f, %.2f)\tR=%.2f]".format(x, y, r)
}

internal fun Matrix44.showAsM44(): String =
    (0..3).joinToString("\n", prefix = "\n((", postfix = "))") { rowIx ->
        (0..3).joinToString("\t") { columnIx ->
            "%.3f".format(this[rowIx][columnIx])
        }
    }

private const val TAG = "Projective"

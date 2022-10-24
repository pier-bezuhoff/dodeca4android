@file:Suppress("NOTHING_TO_INLINE")

package com.pierbezuhoff.dodeca.data

import android.graphics.Paint
import android.opengl.Matrix
import com.pierbezuhoff.dodeca.utils.abs2
import com.pierbezuhoff.dodeca.utils.component1
import com.pierbezuhoff.dodeca.utils.component2
import com.pierbezuhoff.dodeca.utils.consecutiveGroupBy
import com.pierbezuhoff.dodeca.utils.plus
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

typealias Rule = List<Int>
typealias Part = List<Int>
typealias Vector4 = FloatArray
typealias Matrix44 = FloatArray
typealias Pole = Vector4

// TMP: abstract
// NOTE: maybe use nd4j for performance (>200MB), also try deprecated Matrix4f
// NOTE: some ddu may diverge because of Float-s
// https://github.com/deeplearning4j/deeplearning4j/tree/master/nd4j
abstract class ProjectiveCircles(
    figures: List<CircleFigure>,
    private val paint: Paint
) : SuspendableCircleGroup {
    // static
    private val initialPoles: List<Pole> // poles of all initial circles
    private val partsOfRules: List<IntArray> // rule index: int array of parts' indices
    private val rulesForCircles: List<Int> // circle index: (unique) rule index
    private val rulesForParts: List<Int> // part index: rule index
    private val nRules: Int // only counting unique ones
    private val nParts: Int // nParts >= nRules
    private val nCircles: Int = figures.size // nCircles >= nRules
    // dynamic
    private val parts: Array<Matrix44> // unique parts of rules (can contain each other)
    private val rules: Array<Matrix44> // unique rules
    private val cumulativeRules: Array<Matrix44> // each update: cum. rule = rule * cum. rule
    private val poles: Array<Pole>
    private val xs: FloatArray = FloatArray(nCircles)
    private val ys: FloatArray = FloatArray(nCircles)
    private val rs: FloatArray = FloatArray(nCircles)

    init {
        val symbolicRules = figures.map {
            val r = it.rule ?: ""
            r.reversed().map { c -> c.digitToInt() }
        }
        val uniqueRules = symbolicRules.distinct()
        nRules = uniqueRules.size
        rulesForCircles = symbolicRules.map { uniqueRules.indexOf(it) }
        val partsRules = mutableMapOf<Part, Rule>()
        val ruleSplits = mutableListOf<List<Part>>() // rule index: list of parts comprising it
        for (rule in symbolicRules) {
            val split = rule.consecutiveGroupBy { cIx -> symbolicRules[cIx] }
            ruleSplits.add(split.map { it.second })
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
        val pivotIndices: Set<Int> = uniqueRules.flatten().toSet()
        val poles0 = mutableListOf<Vector4>()
        val pivots = mutableListOf<Matrix44>()
        for (i in pivotIndices) {
            val pole = circle2pole(figures[i])
            poles0.add(pole)
            pivots.add(pole2matrix(pole))
        }
        initialPoles = poles0
        poles = poles0.toTypedArray()
        parts = symbolicParts.map { it.map { i -> pivots[i] }.product() }.toTypedArray()
        rules = ruleBlueprints.map { it.map { i -> parts[i] }.product() }.toTypedArray()
        cumulativeRules = rules.map { I44() }.toTypedArray()
        figures.forEachIndexed { i, f ->
            xs[i] = f.center.real.toFloat()
            ys[i] = f.center.imaginary.toFloat()
            rs[i] = f.radius.toFloat()
        }
    }

    fun update() {
        cumulativeRules.zip(rules).forEach { (cr, r) -> cr.setToProduct(r, cr) }
        parts.zip(rulesForParts).forEach { (p, rIx) ->
            val r = rules[rIx]
            p.setToProduct(p, r.inverse())
            p.setToProduct(r, p)
        }
        partsOfRules.forEachIndexed { rIx, ps ->
            rules[rIx] = ps.map { parts[it] }.product()
        }
    }

    // most likely the bottleneck
    fun apply() {
        initialPoles.forEachIndexed { cIx, pole ->
            poles[cIx].setToDotProduct(cumulativeRules[rulesForCircles[cIx]], pole)
        }
        poles.forEachIndexed { cIx, pole ->
            val (cx, cy, r) = pole2circle(pole)
            xs[cIx] = cx
            ys[cIx] = cy
            rs[cIx] = r
        }
    }
}

// sphere: (0,0,0), R=1
// proj from the north pole (0,0,1)
// onto plane z=0
private fun circle2pole(circle: Circle): Vector4 {
    val (x, y) = circle.center
    val t = circle.center + circle.radius // any point on the circle
    val d = 1 + t.abs2()
    // s = stereographic image of t on the sphere
    val sx = 2*t.real/d
    val sy = 2*t.imaginary/d
    val sz = (t.abs2() - 1)/d
    val s2 = sx*sx + sy*sy + sz*sz
    // parameter of the pole on the line thru the circle.center and the sphere's north
    // pole = k*C + (1 - k)*N
    val k = (s2 - sz)/(x * sx + y * sy - sz)
    return doubleArrayOf(k*x, k*y, 1 - k).map { it.toFloat() }.toFloatArray()
}

// TODO: optimize!
private fun pole2circle(pole: Vector4): FloatArray {
    val (wx,wy,wz,w) = pole
    val x = wx/w
    val y = wy/w
    val z = wz/w
    // st. proj. pole->center
    val cx = x/(1 - z)
    val cy = y/(1 - z)
    // let's find a point t on the polar circle
    val d = hypot(x, y)
    val p2 = x*x + y*y + z*z
    val tz: Float = z/p2 + d * sqrt(p2 - 1)/p2
    val tx: Float = if (d == 0.0f) sqrt(1 - tz*tz) else -x * (z*tz - 1)/(d*d)
    val ty: Float = if (d == 0.0f) 0.0f else y * tx/x
    // s = st. proj. of t on the plane
    val sx = tx/(1 - tz)
    val sy = ty/(1- tz)
    val r = hypot(cx - sx, cy - sy) // so much overhead just for the radius...
    return floatArrayOf(cx, cy, r)
}

// TODO: test it
private fun pole2matrix(pole: Vector4): Matrix44 {
    val (wx,wy,wz,w) = pole
    val x = wx/w
    val y = wy/w
    val z = wz/w
    val a = sqrt(x*x + y*y + z*z)
    val a2 = a*a
    val th = -atan2(z, x)
    val phi = -atan2(y, hypot(x, z))
    // NOTE: transposed column-row order
    val Ry: Matrix44 = floatArrayOf(
        cos(th), 0f, sin(th), 0f,
        0f, 1f, 0f, 0f,
        -sin(th), 0f, cos(th), 0f,
        0f, 0f, 0f, 1f
    )
    val Rz: Matrix44 = floatArrayOf(
        cos(phi), sin(phi), 0f, 0f,
        -sin(phi), cos(phi), 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f
    )
    val M: Matrix44 = floatArrayOf(
        a2 + 1, 0f, 0f, 2*a,
        0f, 1 - a2, 0f, 0f,
        0f, 0f, 1 - a2, 0f,
        -2*a, 0f, 0f, -a2 - 1
    )
    val result = I44()
    with(result) {
        setToProduct(Rz, Ry)
        setToProduct(M, this)
        setToProduct(Rz.inverse(), this)
        setToProduct(Ry.inverse(), this)
    }
    return result
}

private fun I44(): Matrix44 =
    FloatArray(16).apply { Matrix.setIdentityM(this, 0) }

private fun Iterable<Matrix44>.product() : Matrix44 {
    val result = I44()
    fold(I44()) { a, b ->
        result.setToProduct(a, b)
        result
    }
    return result
}

private inline fun Matrix44.setToProduct(a: Matrix44, b: Matrix44) {
    Matrix.multiplyMM(a, 0, b, 0, this, 0)
}

private inline fun Vector4.setToDotProduct(m: Matrix44, v: Vector4) {
    Matrix.multiplyMV(m, 0, v, 0, this, 0)
}

private inline fun Matrix44.inverse(): Matrix44 =
    FloatArray(16).also { Matrix.invertM(this, 0, it, 0) }
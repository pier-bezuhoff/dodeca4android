@file:Suppress("NOTHING_TO_INLINE")

package com.pierbezuhoff.dodeca.data

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.SweepGradient
import android.opengl.Matrix
import android.util.Log
import android.util.SparseArray
import com.pierbezuhoff.dodeca.utils.asFF
import com.pierbezuhoff.dodeca.utils.consecutiveGroupBy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.component3
import kotlin.collections.component4
import kotlin.collections.set
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

typealias Ix = Int
typealias Ixs = IntArray
typealias IxTable = List<Ix> // Ix to Ix correspondence
typealias Rule = List<Ix>
typealias Part = List<Ix>
typealias Vector4 = FloatArray
typealias Matrix44 = FloatArray
typealias Pole = Vector4

// BUG: most radii are 0-d and NaN-d, all non I rules are 0 4x4

// NOTE: maybe use nd4j for performance (>200MB), also try deprecated Matrix4f
// https://github.com/deeplearning4j/deeplearning4j/tree/master/nd4j
// NOTE: some ddu may diverge because of Float-s (?)
internal class ProjectiveCircles(
    figures: List<CircleFigure>,
    private val paint: Paint
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
    private val xs: FloatArray = FloatArray(size)
    private val ys: FloatArray = FloatArray(size)
    private val rs: FloatArray = FloatArray(size)
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
                CircleFigure(xs[i].toDouble(), ys[i].toDouble(), rs[i].toDouble(), color, fill, rule, borderColor)
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
        initialPoles = figures.map { circle2pole(it) }
        poles = initialPoles.map { it.copyOf() }.toTypedArray()
        val pivotIndices: Set<Ix> = uniqueRules.flatten().toSet()
        val pivots = mutableMapOf<Ix, Matrix44>()
        for (i in pivotIndices) {
            val pole = circle2pole(figures[i])
            pivots[i] = pole2matrix(pole)
        }
        parts = symbolicParts.map { it.map { i -> pivots[i]!! }.product() }.toTypedArray() // index out of bounds error
        rules = ruleBlueprints.map { it.map { i -> parts[i] }.product() }.toTypedArray()
        cumulativeRules = rules.map { I44() }.toTypedArray()
        applyMatrices()
        val good = figures.all { f ->
            val (x,y,r) = pole2circle(circle2pole(f))
            listOf(f.x to x, f.y to y, f.radius to r).all { (v0, v) ->
                abs(v - v0) < 1e-4
            }
        }
        assert(good) { "p2c != c2p.inv" }
//        figures.forEachIndexed { i, f ->
//            xs[i] = f.center.real.toFloat()
//            ys[i] = f.center.imaginary.toFloat()
//            rs[i] = f.radius.toFloat()
//        }
    }

    private inline fun straightUpdate() {
        cumulativeRules.zip(rules).forEach { (cr, r) -> cr.setToProduct(r, cr) }
        parts.zip(rulesForParts).forEach { (p, rIx) ->
            val r = rules[rIx]
            p.setToProduct(p, r.inverse())
            p.setToProduct(r, p) // p -> r * p * r.inv
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
            poles[cIx].setToDotProduct(cumulativeRules[rulesForCircles[cIx]], initialPoles[cIx])
//            val (cx, cy, r) = pole2circle(pole) // inlined to escape type conversion etc.
            val (wx,wy,wz,w) = poles[cIx]
            val x = wx/w
            val y = wy/w
            val z = wz/w
            val nz = 1 - z
            xs[cIx] = x/nz
            ys[cIx] = y/nz
            rs[cIx] = sqrt(x*x + y*y + z*z - 1)/abs(nz)
//            Log.i(TAG, "#$cIx: ${poles[cIx].showAsCircle()}")
        }
        rules.forEachIndexed { i, m ->
//            Log.i(TAG, "rule '${uniqueRules[i].joinToString("")}':\n${m.showAsM44()}")
        }
    }

    // most likely the bottleneck
    private inline fun applyAllMatrices() {
        initialPoles.forEachIndexed { cIx, pole ->
            poles[cIx].setToDotProduct(cumulativeRules[rulesForCircles[cIx]], pole)
        }
        poles.forEachIndexed { cIx, pole ->
//            val (cx, cy, r) = pole2circle(pole) // inlined to escape type conversion etc.
            val (wx,wy,wz,w) = pole
            val x = wx/w
            val y = wy/w
            val z = wz/w
            val nz = 1 - z
            xs[cIx] = x/nz
            ys[cIx] = y/nz
            rs[cIx] = sqrt(x*x + y*y + z*z - 1)/abs(nz)
        }
    }

    override fun get(i: Int): CircleFigure {
        applyAllMatrices()
        val (color, fill, rule, borderColor) = attrs[i]
        return CircleFigure(xs[i].toDouble(), ys[i].toDouble(), rs[i].toDouble(), color, fill, rule, borderColor)
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
        Unit//_update(reverse) // TMP

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
        val x = xs[i]
        val y = ys[i]
        val r = rs[i]
        canvas.drawCircle(x, y, r, paints[i])
        borderPaints.get(i)?.let { borderPaint ->
            canvas.drawCircle(x, y, r, borderPaint)
        }
    }

    private inline fun drawSquare(i: Int, canvas: Canvas) {
        val x = xs[i]
        val y = ys[i]
        val r = rs[i]
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
        val x = xs[i]
        val y = ys[i]
        val r = rs[i]
        canvas.drawLines(
            floatArrayOf(
                x, y - r, x, y + r,
                x - r, y, x + r, y
            ),
            paints[i]
        )
    }

    private inline fun drawVerticalBar(i: Int, canvas: Canvas) {
        val x = xs[i]
        val y = ys[i]
        val r = rs[i]
        canvas.drawLine(x, y - r, x, y + r, paints[i])
    }

    private inline fun drawHorizontalBar(i: Int, canvas: Canvas) {
        val x = xs[i]
        val y = ys[i]
        val r = rs[i]
        canvas.drawLine(x - r, y, x + r, y, paints[i])
    }

    private fun drawCircleOverlay(i: Int, canvas: Canvas, bold: Boolean = false) {
        val x = xs[i]
        val y = ys[i]
        val r = rs[i]
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
        val x = xs[i]
        val y = ys[i]
        val r = rs[i]
        val p = Paint()
        p.pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
        canvas.drawLine(x, y - r, x, y + r, p) // dashed vertical diameter
        p.pathEffect = DashPathEffect(floatArrayOf(2f, 2f), 0f)
        canvas.drawLine(x, y, x + r, y, p)
        canvas.drawPoint(x + r, y, p)
    }

    companion object {
        internal const val TAG = "ProjectiveCircles"
    }

}

// sphere: (0,0,0), R=1
// proj from the north pole (0,0,1)
// onto plane z=0
// for linear (projective) functions: f(R, x) = R * f(1, x/R)
private fun circle2pole(circle: Circle, sphereRadius: Float = 1f): Vector4 {
    val (x0, y0) = circle.center.asFF()
    val x = x0/sphereRadius
    val y = y0/sphereRadius
    val r = circle.radius/sphereRadius
    val tx = x + circle.radius.toFloat()
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
    return floatArrayOf(px, py, pz, 1f).also {
        Log.i(ProjectiveCircles.TAG, "($x0, $y0), r=${circle.radius}; R = $sphereRadius\t-> ${it.showAsV3()}")
    }
}

// NOTE: inlined for performance
private fun pole2circle(pole: Vector4, sphereRadius: Float = 1f): FloatArray {
    val (wx,wy,wz,w) = pole
    val x = wx/w/sphereRadius
    val y = wy/w/sphereRadius
    val z = wz/w/sphereRadius
    val nz = 1 - z
    // st. proj. pole->center
    val cx = x/nz * sphereRadius
    val cy = y/nz * sphereRadius
    val op2 = x*x + y*y + z*z // op2 > 1 for *real* circles
    val r = sqrt(op2 - 1)/abs(nz) * sphereRadius // sqrt(op2-1) = segment of tangent
    return floatArrayOf(cx, cy, r)
}

// TODO: test it
private fun pole2matrix(pole: Vector4): Matrix44 {
    val (wx,wy,wz,w) = pole
    val x = wx/w
    val y = wy/w
    val z = wz/w
    val a2 = x*x + y*y + z*z
    val a = sqrt(a2)
    val th = -atan2(z, x)
    val phi = -atan2(y, hypot(x, z))
    // NOTE: transposed column-row order
    val Ry: Matrix44 = floatArrayOf(
        cos(th),  0f, sin(th), 0f,
        0f,       1f, 0f,      0f,
        -sin(th), 0f, cos(th), 0f,
        0f,       0f, 0f,      1f
    )
    val Rz: Matrix44 = floatArrayOf(
        cos(phi),  sin(phi), 0f, 0f,
        -sin(phi), cos(phi), 0f, 0f,
        0f,        0f,       1f, 0f,
        0f,        0f,       0f, 1f
    )
    val M: Matrix44 = floatArrayOf(
        a2 + 1, 0f,     0f,     2*a,
        0f,     1 - a2, 0f,     0f,
        0f,     0f,     1 - a2, 0f,
        -2*a,   0f,     0f,     -a2 - 1
    )
    Log.i(ProjectiveCircles.TAG, "${pole.showAsCircle()};\ta=$a, th=$th, phi=$phi")
    return listOf(
        Ry.inverse(), Rz.inverse(), M, Rz, Ry
    ).map { /*Log.i(ProjectiveCircles.TAG, it.showAsM44()) ;*/ it }
        .product()
//        .also { Log.i(ProjectiveCircles.TAG, "-> "+it.showAsM44()) } // bruh all are the same and det=0
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
    Matrix.multiplyMM(this, 0, a, 0, b, 0)
}

private inline fun Vector4.setToDotProduct(m: Matrix44, v: Vector4) {
    Matrix.multiplyMV(this, 0, m, 0, v, 0)
}

private inline fun Matrix44.inverse(): Matrix44 =
    FloatArray(16).also { Matrix.invertM(it, 0, this, 0) }

private fun FloatArray.showAsList(): String =
    joinToString("\t", "(", ")") { "%.2f".format(it) }

private fun Vector4.showAsV3(): String {
    val (x,y,z,w) = this
    return "(%.2f\t%.2f\t%.2f)".format(x/w, y/w, z/w)
}

private fun Pole.showAsCircle(): String {
    val (x, y, r) = pole2circle(this)
    return "[(%.2f, %.2f)\tR=%.2f]".format(x, y, r)
}

private fun Matrix44.showAsM44(): String =
    (0..3).joinToString("\n", prefix = "\n((", postfix = "))") { rowIx ->
        listOf(rowIx, rowIx+4, rowIx+8, rowIx+12).joinToString("\t") { i ->
            "%.2f".format(this[i])
        }
    }
package com.pierbezuhoff.dodeca.data

import android.graphics.Color
import android.util.Log
import com.pierbezuhoff.dodeca.utils.Maybe
import com.pierbezuhoff.dodeca.utils.None
import com.pierbezuhoff.dodeca.utils.abs2
import com.pierbezuhoff.dodeca.utils.div
import com.pierbezuhoff.dodeca.utils.minus
import com.pierbezuhoff.dodeca.utils.normalized
import com.pierbezuhoff.dodeca.utils.plus
import com.pierbezuhoff.dodeca.utils.times
import com.pierbezuhoff.dodeca.utils.unaryMinus
import org.apache.commons.math3.complex.Complex
import org.apache.commons.math3.complex.Complex.I
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

/* radius >= 0 */
open class Circle(var center: Complex, var radius: Double) {
    val x: Double get() = center.real
    val y: Double get() = center.imaginary
    val r2: Double get() = radius * radius

    constructor(x: Double, y: Double, radius: Double) : this(Complex(x, y), radius)

    operator fun component1(): Complex = center
    operator fun component2(): Double = radius

    fun copy(newCenter: Complex? = null, newRadius: Double? = null): Circle =
        Circle(newCenter ?: center, newRadius ?: radius)

    /* Inverts [this] with respect to [circle] */
    open fun inverted(circle: Circle): Circle {
        val (c, r) = circle
        return when {
            r == Double.POSITIVE_INFINITY ->
                Circle(Complex.INF, Double.POSITIVE_INFINITY)
            center == c ->
                Circle(center, circle.r2 / radius)
            else -> {
                val d = center - c
                var d2 = d.abs2()
                if (d.abs() == radius) // c <- this
                    d2 += 1e-6 // cheat to avoid returning a straight line
                val ratio = circle.r2 / (d2 - r2)
                val newCenter = c + ratio * d
                val newRadius = abs(ratio) * radius
                Circle(newCenter, newRadius)
            }
        }
        /* NOTE: c = 0, r = 1; center <- |R
        => Circle(a/(a^2-r2), radius/|a^2-r2|)
         */
    }

    /* Inverts [this] with respect to [circle] */
    open fun invert(circle: Circle) {
        val (c, r) = circle
        when {
            r == Double.POSITIVE_INFINITY -> {
                center = Complex.INF
                radius = Double.POSITIVE_INFINITY
            }
            center == c ->
                radius = circle.r2 / radius
            else -> {
                val d = center - c
                var d2 = d.abs2()
                if (d.abs() == radius) // c <- this
                    d2 += 1e-6 // cheat to avoid returning a straight line
                val ratio = circle.r2 / (d2 - r2)
                val newCenter = c + ratio * d
                val newRadius = abs(ratio) * radius
                center = newCenter
                radius = newRadius
            }
        }
        /* NOTE: c = 0, r = 1; center <- |R
        => Circle(a/(a^2-r2), radius/|a^2-r2|)
         */
    }

    fun translate(dx: Double = 0.0, dy: Double = 0.0) {
        center = Complex(x + dx, y + dy)
    }

    /* scale by [scaleFactor] with respect to [center] (or 0) */
    fun scale(scaleFactor: Double, center: Complex = Complex.ZERO) {
        this.radius *= scaleFactor
        this.center = center + scaleFactor * (this.center - center)
    }

    /* Return the list of intersection points */
    fun findIntersection(circle: Circle): List<Complex> {
        val r12 = r2
        val r22 = circle.r2
        val dc = circle.center - center
        val d2 = dc.abs2()
        val d = sqrt(d2)
        if (abs(radius - circle.radius) > d || d > radius + circle.radius)
            return emptyList()
        val dr2 = r12 - r22
        // reference (0=this, 1=circle):
        // https://stackoverflow.com/questions/3349125/circle-circle-intersection-points#answer-3349134
        val a = (d2 + dr2)/(2 * d)
        val h = sqrt(r2 - a*a)
        val pc = center + a * dc / d
        val v = h * dc / d
        val p1 = pc + Complex(v.imaginary, -v.real)
        val p2 = pc - Complex(v.imaginary, -v.real)
        return listOf(p1, p2)
    }

    /* 'Rotate' [this] circle so that the new angle between the circles will be *= [multiplier] */
    fun changeAngle(circle: Circle, multiplier: Double) {
        val intersection = findIntersection(circle)
        if (intersection.size != 2) {
            Log.w(TAG, "Circles must fully intersect for [changeAngle]!, skipping...")
            return
        }
        val (ip1, ip2) = intersection
        // ip2 -> (0, 0); |ip1| -> 1
        val t = -ip2
        val s = (ip1 + t).abs()
        fun system001(c: Complex): Complex =
            (c + t)/s
        val ip = system001(ip1)
        val c1 = system001(circle.center)
        val c2 = system001(center)
        // |c1| = r1, |c2| = r2
        // apply inversion: ip1 -> ip1, ip2 -> inf
        // val p1 = -c1.real/c1.imaginary
        // val q1 = -(1 + 1/p1) * c1/(2 * c1.abs2())
        // y=p1*x+q1 = image of the [circle]
        val n1 = c1.normalized() // normal vector to the line=inv(circle)
        val n2 = c2.normalized()
        val n2new = n1 * (n2/n1).pow(multiplier)
        val rho = (n2new * ip.conjugate()).real // distances from the origin (ip2) to the line=inv(this)
        val c = n2new/(2 * rho) // inv(rho*n2new) = 2*c
        // back from system001
        val newR = c.abs() * s
        val newC = c * s - t
        center = newC
        radius = newR
    }

    companion object {
        private const val TAG = "Circle"
    }
}

class CircleFigure(center: Complex, radius: Double,
    val color: Int = DEFAULT_COLOR,
    val fill: Boolean = DEFAULT_FILL,
    val rule: String? = DEFAULT_RULE,
    val borderColor: Int? = DEFAULT_BORDER_COLOR // used only when [fill], otherwise [color] is used
) : Circle(center, radius) {
    val dynamic: Boolean get() = rule?.isNotBlank() ?: false // is changing over time
    val dynamicHidden: Boolean get() = rule?.startsWith("n") ?: false
    val show: Boolean get() = dynamic && !dynamicHidden
    // sequence of numbers of figures with respect to which this circle should be inverted
    val sequence: IntArray = rule?.let {
            (if (it.startsWith("n")) it.drop(1) else it)
                .map(Character::getNumericValue)
                .toIntArray()
        } ?: intArrayOf()

    val right: Complex get() = center + radius
    val topLeft: Complex get() = center + radius * (I * (3 * PI/4)).exp()

    fun copy(
        newCenter: Complex? = null, newRadius: Double? = null,
        newShown: Boolean? = null,
        newColor: Int? = null, newFill: Boolean? = null,
        newRule: String? = null, newBorderColor: Maybe<Int?> = None
    ): CircleFigure = CircleFigure(
        newCenter ?: center, newRadius ?: radius,
        newColor ?: color, newFill ?: fill,
        (newRule ?: rule)?.let { r ->
            // rule-less circles cannot be visible yet
            newShown?.let { shown ->
                if (shown && r.startsWith('n'))
                    r.drop(1)
                else if (!shown && r.isNotBlank() && !r.startsWith('n'))
                    "n$r"
                else r
            } ?: r
        },
        newBorderColor.orElse(borderColor)
    )

    constructor(
        circle: Circle,
        color: Int = DEFAULT_COLOR, fill: Boolean = DEFAULT_FILL,
        rule: String? = DEFAULT_RULE, borderColor: Int? = DEFAULT_BORDER_COLOR
    ) : this(circle.center, circle.radius, color, fill, rule, borderColor)

    constructor(
        x: Double, y: Double, radius: Double,
        color: Int = DEFAULT_COLOR, fill: Boolean = DEFAULT_FILL,
        rule: String? = DEFAULT_RULE, borderColor: Int? = DEFAULT_BORDER_COLOR
    ) : this(Circle(x, y, radius), color, fill, rule, borderColor)

    constructor(
        x: Double, y: Double, radius: Double,
        color: Int? = null, fill: Boolean? = null,
        rule: String? = null, borderColor: Int? = null
    ) : this(
        Circle(x, y, radius),
        color ?: DEFAULT_COLOR, fill ?: DEFAULT_FILL,
        rule ?: DEFAULT_RULE, borderColor ?: DEFAULT_BORDER_COLOR
    )

    override fun toString(): String =
        """CircleFigure(
        |  center = ($x, $y)
        |  radius = $radius
        |  color = ${color.fromColor()}
        |  fill = $fill${
        rule?.let { "\n  rule = $it" } ?: ""
        }${
        borderColor?.let { "\n  borderColor = $it" } ?: ""
        }
        |)
    """.trimMargin()

    override fun inverted(circle: Circle): CircleFigure =
        CircleFigure(super.inverted(circle), color, fill, rule)

    companion object {
        const val DEFAULT_COLOR: Int = Color.BLACK
        const val DEFAULT_FILL = false
        val DEFAULT_RULE: String? = null
        val DEFAULT_BORDER_COLOR: Int? = null
    }
}


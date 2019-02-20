package com.pierbezuhoff.dodeca

import android.graphics.Color
import org.apache.commons.math3.complex.Complex

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
            r == 0.0 -> Circle(c, 0.0)
            r == Double.POSITIVE_INFINITY -> Circle(Complex.INF, Double.POSITIVE_INFINITY)
            center == c -> Circle(center, circle.r2 / radius)
            else -> {
                val d = center - c
                var d2 = d.abs2()
                if (d.abs() == radius) // c <- this
                    d2 += 1e-6 // cheat to avoid returning line
                val newCenter = c + circle.r2 * d / (d2 - r2)
                val newRadius = circle.r2 * radius / Math.abs(d2 - r2)
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
            r == 0.0 -> {
                center = c
                radius = 0.0
            }
            r == Double.POSITIVE_INFINITY -> {
                center = Complex.INF
                radius = Double.POSITIVE_INFINITY
            }
            center == c -> radius = circle.r2 / radius
            else -> {
                val d = center - c
                var d2 = d.abs2()
                if (d.abs() == radius) // c <- this
                    d2 += 1e-6 // cheat to avoid returning line
                val newCenter = c + circle.r2 * d / (d2 - r2)
                val newRadius = circle.r2 * radius / Math.abs(d2 - r2)
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

    /* scale by `scaleFactor` relative to `center` (or 0) */
    fun scale(scaleFactor: Double, center: Complex = Complex.ZERO) {
        this.radius *= scaleFactor
        this.center = center + scaleFactor * (this.center - center)
    }
}

class CircleFigure(center: Complex, radius: Double,
    val color: Int = DEFAULT_COLOR,
    val fill: Boolean = DEFAULT_FILL,
    val rule: String? = DEFAULT_RULE,
    val borderColor: Int? = DEFAULT_BORDER_COLOR // used only when `fill`, otherwise `color` is used
) : Circle(center, radius) {
    // point.abs() === 1 // maybe: store only angle
    var point: Complex = Complex.ONE // indicate direction when inverting
    val dynamic: Boolean get() = rule?.isNotBlank() ?: false // is changing over time
    val dynamicHidden: Boolean get() = rule?.startsWith("n") ?: false
    val show: Boolean get() = dynamic && !dynamicHidden
    // sequence of numbers of figures with respect to which this circle should be inverted
    val sequence: IntArray = rule?.let {
            (if (it.startsWith("n")) it.drop(1) else it)
                .map(Character::getNumericValue)
                .toIntArray()
        } ?: intArrayOf()

    fun copy(
        newCenter: Complex? = null, newRadius: Double? = null,
        newShown: Boolean? = null,
        newColor: Int? = null, newFill: Boolean? = null,
        newRule: String? = null, newBorderColor: Maybe<Int?> = None
    ): CircleFigure = CircleFigure(
        newCenter ?: center, newRadius ?: radius,
        newColor ?: color, newFill ?: fill,
        (newRule ?: rule)?.let { r -> // rule-less circles cannot be shown yet
            newShown?.let { shown ->
                if (shown && r.startsWith('n'))
                    r.drop(1)
                else if (!shown && r.isNotBlank() && !r.startsWith('n'))
                    "n$r"
                else r
            } ?: r
        },
        newBorderColor.orElse(borderColor)
    ).apply {
        point = this@CircleFigure.point // hope it refers to outer [this]
    }

//    fun copy(
//        newCenter: Complex? = null, newRadius: Double? = null,
//        newColor: Int? = null, newFill: Boolean? = null,
//        newRule: String? = null, newBorderColor: Int? = null
//    ): CircleFigure = copy(newCenter, newRadius, newColor, newFill, newRule, Just(newBorderColor))

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

    override fun toString(): String = """CircleFigure(
        |  center = ($x, $y)
        |  radius = $radius
        |  point = $point
        |  color = ${color.fromColor()}
        |  fill = $fill${
    rule?.let { "\n  rule = $it" } ?: ""
    }${
    borderColor?.let { "\n  borderColor = $it" } ?: ""
    }
        |)
    """.trimMargin()

    override fun inverted(circle: Circle): CircleFigure =
        CircleFigure(super.inverted(circle), color, fill, rule).apply {
            point = this@CircleFigure.invertedPoint(circle)
        }

    override fun invert(circle: Circle) {
        super.invert(circle)
        point = invertedPoint(circle)
    }

    private fun invertedPoint(circle: Circle): Complex = (center + point).inverted(circle).run {
        if (this.abs() == 0.0) Complex.ONE else (this - center).normalized()
    }

    companion object {
        const val DEFAULT_COLOR: Int = Color.BLACK
        const val DEFAULT_FILL = false
        val DEFAULT_RULE: String? = null
        val DEFAULT_BORDER_COLOR: Int? = null
    }
}

enum class Shapes {
    CIRCLE, SQUARE, CROSS, VERTICAL_BAR, HORIZONTAL_BAR;
    companion object {
        val strings get() = Shapes.values().map { it.toString() }
        fun valueOfOrNull(s: String): Shapes? = try { valueOf(s) } catch (e: IllegalArgumentException) { null }
        fun indexOrFirst(index: Int): Shapes =
            if (index < Shapes.values().size) Shapes.values()[index] else Shapes.values()[0]
    }
}

val List<CircleFigure>.centroid get() = mean(map { it.center })
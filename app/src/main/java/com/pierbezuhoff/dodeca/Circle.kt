package com.pierbezuhoff.dodeca

import android.graphics.Color
import org.apache.commons.math3.complex.Complex
import java.util.StringJoiner

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
    var borderColor: Int = defaultBorderColor,
    var fill: Boolean = defaultFill,
    var rule: String? = defaultRule
) : Circle(center, radius) {
    val dynamic: Boolean get() = rule?.isNotBlank() ?: false // is changing over time
    val dynamicHidden: Boolean get() = rule?.startsWith("n") ?: false
    val show: Boolean get() = dynamic && !dynamicHidden

    fun copy(
        newCenter: Complex? = null, newRadius: Double? = null,
        newBorderColor: Int? = null, newFill: Boolean? = null, newRule: String? = null
    ): CircleFigure = CircleFigure(
        newCenter ?: center, newRadius ?: radius,
        newBorderColor ?: borderColor, newFill ?: fill, newRule ?: rule)

    constructor(
        circle: Circle,
        borderColor: Int = defaultBorderColor, fill: Boolean = defaultFill, rule: String? = defaultRule
    ) : this(circle.center, circle.radius, borderColor, fill, rule)

    constructor(
        x: Double, y: Double, radius: Double,
        borderColor: Int = defaultBorderColor, fill: Boolean = defaultFill, rule: String? = defaultRule
    ) : this(Circle(x, y, radius), borderColor, fill, rule)

    constructor(
        x: Double, y: Double, radius: Double,
        borderColor: Int? = null, fill: Boolean? = null, rule: String? = null
    ) : this(Circle(x, y, radius), borderColor ?: defaultBorderColor, fill ?: defaultFill, rule ?: defaultRule)

    override fun toString(): String = """CircleFigure(
        |  center = ($x, $y)
        |  radius = $radius
        |  borderColor = ${borderColor.fromColor()}
        |  fill = $fill
        |  rule = $rule
        |)
    """.trimMargin()

    override fun inverted(circle: Circle): CircleFigure =
        CircleFigure(super.inverted(circle), borderColor, fill, rule)

    companion object {
        const val defaultBorderColor: Int = Color.BLACK
        const val defaultFill = false
        val defaultRule: String? = null
    }
}
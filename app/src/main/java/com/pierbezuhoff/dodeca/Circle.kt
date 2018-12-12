package com.pierbezuhoff.dodeca

import android.graphics.Color
import org.apache.commons.math3.complex.Complex

data class Circle(var center: Complex, var radius: Double, var borderColor: Int, var fill: Boolean, var rule: String?) {
    val x: Double get() = center.real
    val y: Double get() = center.imaginary
    private val r2: Double get() = radius * radius
    val dynamic: Boolean get() = rule?.isNotBlank() ?: false // is changing over time
    val dynamicHidden: Boolean get() = rule?.startsWith("n") ?: false
    val show: Boolean get() = dynamic && !dynamicHidden

    constructor(center: Complex, radius: Double, borderColor: Int? = null, fill: Boolean? = null, rule: String? = null)
        : this(center, radius, borderColor ?: defaultBorderColor, fill ?: defaultFill, rule ?: defaultRule)

    /* Inverts complex [point] with respect to [this]
     * [point] == [this].center => Complex.INF */
    fun invert(point: Complex): Complex =
        if (point == center) Complex.INF
        else center + r2 / (point - center).conjugate()

    /* Inverts [this] with respect to [circle] */
    fun invert(circle: Circle): Circle {
        var (c, r) = circle // not val to change r if c <- this
        if (center == c)
            return Circle(center, circle.r2 / radius, borderColor, fill, rule)
        val d = center - c
        val d2 = d.abs2()
        if (d.abs() == radius) // c <- this
            r += 1e-6 // cheat to avoid returning line
            // return Line(c, d)
        val newCenter = c + circle.r2 * d / (d2 - r2)
        val newRadius = circle.r2 * radius / Math.abs(d2 - r2)
        return Circle(newCenter, newRadius, borderColor, fill, rule)
        /* NOTE: c = 0, r = 1; center <- |R
        => Circle(a/(a^2-r2), radius/|a^2-r2|)
         */
    }

    fun translate(dx: Double = 0.0, dy: Double = 0.0) {
        center = Complex(x + dx, y + dy)
    }

    /* scale by `scaleFactor` relative to `center` (or 0) */
    fun scale(scaleFactor: Double = 1.0, center: Complex = Complex.ZERO) {
        radius *= scaleFactor
        this.center = center + scaleFactor * (this.center - center)
    }

    companion object {
        const val defaultBorderColor: Int = Color.BLACK
        const val defaultFill = false
        val defaultRule: String? = null
    }
}

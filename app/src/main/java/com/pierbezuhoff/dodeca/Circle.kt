package com.pierbezuhoff.dodeca

import android.graphics.Color
import org.apache.commons.math3.complex.Complex

interface Invertible<Self> {
    /* return [this] inverted with respect to [circle] */
    fun inverted(circle: Circle): Self
    /* invert [this] with respect to [circle] */
    fun invert(circle: Circle)
}

interface Shape<Self> : Invertible<Self> {
    fun translate(dx: Double = 0.0, dy: Double = 0.0)
    fun scale(scaleFactor: Double, center: Complex = Complex.ZERO)
}

/* radius >= 0 */
open class Circle(var center: Complex, var radius: Double) : Shape<Circle> {
    val x: Double get() = center.real
    val y: Double get() = center.imaginary
    val r2: Double get() = radius * radius

    constructor(x: Double, y: Double, radius: Double) : this(Complex(x, y), radius)

    operator fun component1(): Complex = center
    operator fun component2(): Double = radius

    fun copy(newCenter: Complex? = null, newRadius: Double? = null): Circle =
        Circle(newCenter ?: center, newRadius ?: radius)

    /* Inverts [this] with respect to [circle] */
    override fun inverted(circle: Circle): Circle {
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
    override fun invert(circle: Circle) {
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

    override fun translate(dx: Double, dy: Double) {
        center = Complex(x + dx, y + dy)
    }

    /* scale by `scaleFactor` relative to `center` (or 0) */
    override fun scale(scaleFactor: Double, center: Complex) {
        this.radius *= scaleFactor
        this.center = center + scaleFactor * (this.center - center)
    }
}

data class Point(var point: Complex) : Shape<Point> {
    override fun inverted(circle: Circle): Point = Point(point.inverted(circle))
    override fun invert(circle: Circle) { point = point.inverted(circle) }
    override fun translate(dx: Double, dy: Double) { point = Complex(point.real + dx, point.imaginary + dy) }
    override fun scale(scaleFactor: Double, center: Complex) { point = center + scaleFactor * (point - center) }
}

class Pointed<A>(
    val point: Point = Point(Complex(1.0, 0.0)),
    val shape: A
) : Shape<Pointed<A>> where A : Shape<A> {
    fun copy(newPoint: Point? = null, newShape: A? = null) =
        Pointed(newPoint ?: point, newShape ?: shape)

    override fun inverted(circle: Circle): Pointed<A> =
        Pointed(point.inverted(circle), shape.inverted(circle))

    override fun invert(circle: Circle) {
        point.invert(circle)
        shape.invert(circle)
    }

    override fun translate(dx: Double, dy: Double) {
        point.translate(dx, dy)
        shape.translate(dx, dy)
    }

    override fun scale(scaleFactor: Double, center: Complex) {
        point.scale(scaleFactor, center)
        shape.scale(scaleFactor, center)
    }
}

class Figure<A>(
    val shape: A,
    var borderColor: Int = defaultBorderColor,
    var fill: Boolean = defaultFill,
    var rule: String? = defaultRule
) : Shape<Figure<A>> where A : Shape<A> {
    val dynamic: Boolean get() = rule?.isNotBlank() ?: false // is changing over time
    val dynamicHidden: Boolean get() = rule?.startsWith("n") ?: false
    val show: Boolean get() = dynamic && !dynamicHidden

    constructor(
        shape: A,
        borderColor: Int? = null, fill: Boolean? = null, rule: String? = null
    ) : this(shape, borderColor ?: defaultBorderColor, fill ?: defaultFill, rule ?: defaultRule)

    fun copy(
        newInvertible: A? = null,
        newBorderColor: Int? = null, newFill: Boolean? = null, newRule: String? = null
    ): Figure<A> = Figure(
        newInvertible ?: shape,
        newBorderColor ?: borderColor, newFill ?: fill, newRule ?: rule)

    override fun inverted(circle: Circle): Figure<A> = Figure(shape.inverted(circle), borderColor, fill, rule)
    override fun invert(circle: Circle) { shape.invert(circle) }
    override fun translate(dx: Double, dy: Double) { shape.translate(dx, dy) }
    override fun scale(scaleFactor: Double, center: Complex) { shape.scale(scaleFactor, center) }

    companion object {
        const val defaultBorderColor: Int = Color.BLACK
        const val defaultFill = false
        val defaultRule: String? = null
    }
}

// make CircleFigure Circle-like
typealias CircleFigure = Figure<Pointed<Circle>>

fun circleFigure(
    x: Double, y: Double, radius: Double,
    borderColor: Int? = null, fill: Boolean? = null, rule: String? = null
): CircleFigure = CircleFigure(Pointed(shape = Circle(x, y, radius)), borderColor, fill, rule)

operator fun CircleFigure.component1() = circle.component1()
operator fun CircleFigure.component2() = circle.component2()

val CircleFigure.circle: Circle get() = shape.shape
val CircleFigure.center: Complex get() = circle.center
val CircleFigure.x: Double get() = circle.x
val CircleFigure.y: Double get() = circle.y
val CircleFigure.radius: Double get() = circle.radius

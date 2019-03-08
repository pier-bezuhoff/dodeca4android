package com.pierbezuhoff.dodeca

import org.apache.commons.math3.complex.Complex

fun ComplexFF(x: Float, y: Float): Complex = Complex(x.toDouble(), y.toDouble())
operator fun Complex.unaryMinus(): Complex = this.negate()
operator fun Complex.plus(addend: Complex): Complex = this.add(addend)
operator fun Double.plus(addend: Complex): Complex = addend.add(this)
operator fun Complex.plus(addend: Double): Complex = this.add(addend)
operator fun Complex.minus(subtrahend: Complex): Complex = this.subtract(subtrahend)
operator fun Double.minus(subtrahend: Complex): Complex = subtrahend.negate().add(this)
operator fun Complex.minus(subtrahend: Double): Complex = this.subtract(subtrahend)
operator fun Complex.times(factor: Complex): Complex = this.multiply(factor)
operator fun Double.times(factor: Complex): Complex = factor.multiply(this)
operator fun Complex.times(factor: Double): Complex = this.multiply(factor)
operator fun Complex.times(factor: Float): Complex = this.multiply(factor.toDouble())
operator fun Complex.div(divisor: Complex): Complex = this.divide(divisor)
operator fun Double.div(divisor: Complex): Complex = divisor.reciprocal().multiply(this)
operator fun Complex.div(divisor: Double): Complex = this.divide(divisor)
operator fun Complex.component1(): Double = real
operator fun Complex.component2(): Double = imaginary
fun Complex.asFF(): Pair<Float, Float> = Pair(real.toFloat(), imaginary.toFloat())
fun Complex.abs2(): Double = (this * this.conjugate()).real
fun Complex.normalized(): Complex = if (this == Complex.ZERO) Complex.ZERO else this / this.abs()
val Complex.degrees: Double get() = Math.toDegrees(argument)

/* Inverts [this] complex point with respect to [circle]
 * [circle].center == [this].center => Complex.INF */
fun Complex.inverted(circle: Circle): Complex {
    val (c, r) = circle
    return when {
        r == 0.0 -> c
        c == this || r == Double.POSITIVE_INFINITY -> Complex.INF
        else -> c + circle.r2 / (this - c).conjugate()
    }
}

fun List<Complex>.mean(): Complex {
    val n = size
    val sum = foldRight(Complex.ZERO, Complex::plus)
    return sum / n.toDouble()
}

internal fun scrollToCentroid(center: Complex, zs: List<Complex>) : Complex {
    // algorithm to decide whether to center x and y or not
//    val mean = mean(zs)
//    val deltas = zs.map { it - mean }
//    val mu2 = mean(deltas.map { it * it }) // variance
//    val mu3 = mean(deltas.map { it * it * it }) // 3-rd central moment
//    val xAsymK = mu3.real / sqrt(mu2.real.absoluteValue).pow(3) // skewness
//    val yAsymK = mu3.imaginary / sqrt(mu2.imaginary.absoluteValue).pow(3)
//    val threshold = zs.size * 10.0 // maybe: delete
//    val dx = if (xAsymK.absoluteValue > threshold && !xAsymK.isNaN()) 0.0 else center.real - mean.real
//    val dy = if (yAsymK.absoluteValue > threshold && !yAsymK.isNaN()) 0.0 else center.imaginary - mean.imaginary
//    Log.i("scrollToCentroid", "xAsymK: $xAsymK, yAsymK: $yAsymK, threshold: $threshold\ndx: $dx, dy: $dy")
//    return Complex(dx, dy)
    return center - zs.mean()
}


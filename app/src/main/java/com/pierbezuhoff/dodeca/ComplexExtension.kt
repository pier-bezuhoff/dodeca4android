package com.pierbezuhoff.dodeca

import org.apache.commons.math3.complex.Complex

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
operator fun Complex.div(divisor: Complex): Complex = this.divide(divisor)
operator fun Double.div(divisor: Complex): Complex = divisor.reciprocal().multiply(this)
operator fun Complex.div(divisor: Double): Complex = this.divide(divisor)
fun Complex.abs2(): Double = (this * this.conjugate()).real

package com.pierbezuhoff.dodeca

// Kotlin's `/\A. A?` is not expressive enough (A?? = A?)
sealed class Maybe<out A> {
    abstract fun toNullable(): A?
    operator fun component1(): A? = toNullable()
    fun orElse(a: @UnsafeVariance A): A = when(this) {
        None -> a
        is Just -> this.value
    }
}
class Just<out A>(val value: A) : Maybe<A>() {
    override fun toNullable(): A? = value
}
object None : Maybe<Nothing>() {
    override fun toNullable(): Nothing? = null
}

fun <A> Maybe(maybeA: A?): Maybe<A> = maybeA?.let { Just(it) } ?: None
fun <A> justIf(a: A, condition: Boolean): Maybe<A> = if (condition) Just(a) else None

package com.pierbezuhoff.dodeca.utils

import kotlin.reflect.KProperty

/** Become false after every view (getValue) */
class Once(initialValue: Boolean = false) {
    private var value: Boolean = initialValue

    operator fun getValue(thisRef: Any?, property: KProperty<*>): Boolean =
        value.also {
            value = false
        }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
        this.value = value
    }

    fun peek(): Boolean =
        value
}
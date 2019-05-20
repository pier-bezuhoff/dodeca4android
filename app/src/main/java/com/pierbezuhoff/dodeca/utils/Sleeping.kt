package com.pierbezuhoff.dodeca.utils

import kotlin.reflect.KProperty

class Sleeping<out T>(private val initializer: () -> T) {
    private var _value: T? = null
    val value: T get() =
        if (_value != null) {
            _value!!
        } else {
            _value = initializer()
            _value!!
        }

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T =
        value

    fun awake(): T {
        _value = initializer()
        return _value!!
    }

    fun forget() {
        _value = null
    }
}

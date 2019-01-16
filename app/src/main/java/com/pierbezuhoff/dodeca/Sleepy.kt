package com.pierbezuhoff.dodeca

class Sleepy<out T>(private val initializer: () -> T) {
    private var _value: T? = null
    val value: T get() =
        if (_value != null) {
            _value!!
        } else {
            _value = initializer()
            _value!!
        }

    fun awake(): T {
        _value = initializer()
        return _value!!
    }

}
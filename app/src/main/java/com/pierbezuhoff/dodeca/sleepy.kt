package com.pierbezuhoff.dodeca

class sleepy<out T>(val initializer: () -> T) {
    private var _value: T? = null
    val value: T get() {
        if (_value != null) {
            return _value!!
        } else {
            _value = initializer()
            return _value!!
        }
    }

    fun awake(): T {
        _value = initializer()
        return _value!!
    }

}
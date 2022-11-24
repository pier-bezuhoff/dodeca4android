package com.pierbezuhoff.dodeca.data

import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlin.reflect.KProperty

// NOTE: using LiveData like this is wrong
abstract class Option<T : Any>(val default: T) {
    var value = default
        protected set(value) {
            val oldValue = field
            field = value
            if (value != oldValue)
                _liveData.value = value
        }
    private val _liveData: MutableLiveData<T> = MutableLiveData(value)
    val liveData: LiveData<T> = _liveData

    abstract fun peekFrom(sharedPreferences: SharedPreferences): T

    fun fetchFrom(sharedPreferences: SharedPreferences) {
        try {
            val newValue = peekFrom(sharedPreferences)
            value = newValue
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    abstract fun putIn(editor: SharedPreferences.Editor)

    fun setToIn(newValue: T, editor: SharedPreferences.Editor? = null) {
        value = newValue
        editor?.let { putIn(editor) }
    }

    abstract fun removeFrom(editor: SharedPreferences.Editor)

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T =
        value
}

open class KeyOption<T : Any>(val key: String, default: T) : Option<T>(default) {
    override fun equals(other: Any?): Boolean = other is KeyOption<*> && other.key == key
    override fun hashCode(): Int = key.hashCode()
    override fun toString(): String = "KeyOption '$key': $value (default $default)"

    @Suppress("UNCHECKED_CAST")
    override fun peekFrom(sharedPreferences: SharedPreferences): T = when (default) {
        is Boolean -> sharedPreferences.getBoolean(key, default) as T
        is String -> sharedPreferences.getString(key, default) as T
        is Float -> sharedPreferences.getFloat(key, default) as T
        is Int -> sharedPreferences.getInt(key, default) as T
        is Long -> sharedPreferences.getLong(key, default) as T
        else -> throw Exception("Unsupported type of $default")
    }

    override fun putIn(editor: SharedPreferences.Editor) {
        when (value) {
            is Boolean -> editor.putBoolean(key, value as Boolean)
            is String -> editor.putString(key, value as String)
            is Float -> editor.putFloat(key, value as Float)
            is Int -> editor.putInt(key, value as Int)
            is Long -> editor.putLong(key, value as Long)
            else -> throw Exception("Unsupported type of $value")
        }
    }

    override fun removeFrom(editor: SharedPreferences.Editor) {
        editor.remove(key)
    }
}

open class ParsedKeyOption<T : Any>(
    key: String,
    default: T,
    val parse: String.() -> T?
) : KeyOption<T>(key, default) {
    override fun peekFrom(sharedPreferences: SharedPreferences): T =
        sharedPreferences.getString(key, default.toString())?.parse() ?: default
    override fun putIn(editor: SharedPreferences.Editor) {
        editor.putString(key, value.toString())
    }
}

class ParsedIntKeyOption(key: String, default: Int) : ParsedKeyOption<Int>(key, default, String::toIntOrNull)
class ParsedFloatKeyOption(key: String, default: Float) : ParsedKeyOption<Float>(key, default, String::toFloatOrNull)


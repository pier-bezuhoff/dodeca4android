package com.pierbezuhoff.dodeca

import android.content.SharedPreferences

abstract class SharedPreference<T>(val default: T) where T : Any {
    var value = default

    abstract fun peek(sharedPreferences: SharedPreferences): T

    // NOTE: fetch(_) { * } == fetch(_, onPostChange = { * })
    fun fetch(sharedPreferences: SharedPreferences, onPreChange: (T) -> Unit = {}, onPostChange: (T) -> Unit = {}) {
        val newValue = peek(sharedPreferences)
        val changed = value != newValue
        if (changed) onPreChange(newValue)
        value = newValue
        if (changed) onPostChange(value)
    }

    abstract fun put(editor: SharedPreferences.Editor)
}

open class Option<T>(val key: String, default: T) : SharedPreference<T>(default) where T : Any {

    override fun peek(sharedPreferences: SharedPreferences): T = when (default) {
        is Boolean -> sharedPreferences.getBoolean(key, default) as T
        is String -> sharedPreferences.getString(key, default) as T
        is Float -> sharedPreferences.getFloat(key, default) as T
        is Int -> sharedPreferences.getInt(key, default) as T
        is Long -> sharedPreferences.getLong(key, default) as T
        else -> throw Exception("Unsupported type: ${default.javaClass.name}")
    }

    override fun put(editor: SharedPreferences.Editor) {
        when (value) {
            is Boolean -> editor.putBoolean(key, value as Boolean)
            is String -> editor.putString(key, value as String)
            is Float -> editor.putFloat(key, value as Float)
            is Int -> editor.putInt(key, value as Int)
            is Long -> editor.putLong(key, value as Long)
            else -> throw Exception("Unsupported type: ${value.javaClass.name}")
        }
    }
}

fun <T: Any> SharedPreferences.fetch(
    preference: SharedPreference<T>,
    onPreChange: (T) -> Unit = {}, onPostChange: (T) -> Unit = {}
) = preference.fetch(this, onPreChange, onPostChange)

fun <T: Any> SharedPreferences.Editor.put(preference: SharedPreference<T>) =
    preference.put(this)
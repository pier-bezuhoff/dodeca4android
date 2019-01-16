package com.pierbezuhoff.dodeca

import android.content.SharedPreferences
import android.graphics.Matrix

abstract class SharedPreference<T>(val default: T) where T : Any {
    var value = default

    abstract fun peek(sharedPreferences: SharedPreferences): T

    // NOTE: fetch(_) { * } == fetch(_, onPostChange = { * })
    fun fetch(sharedPreferences: SharedPreferences, onPreChange: (T) -> Unit = {}, onPostChange: (T) -> Unit = {}) {
        try {
            val newValue = peek(sharedPreferences)
            val changed = value != newValue
            if (changed) onPreChange(newValue)
            value = newValue
            if (changed) onPostChange(value)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    abstract fun put(editor: SharedPreferences.Editor)

    fun set(newValue: T? = null, editor: SharedPreferences.Editor? = null) {
        value = newValue ?: default
        editor?.let { put(editor) }
    }

    abstract fun remove(editor: SharedPreferences.Editor)
}

open class Option<T>(val key: String, default: T) : SharedPreference<T>(default) where T : Any {
    override fun equals(other: Any?): Boolean = other is Option<*> && other.key == key
    override fun hashCode(): Int = key.hashCode()
    override fun toString(): String = "Option '$key': $value (default $default)"

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

    override fun remove(editor: SharedPreferences.Editor) {
        editor.remove(key)
    }
}

class ParsedIntOption(key: String, default: Int) : Option<Int>(key, default) {
    override fun peek(sharedPreferences: SharedPreferences): Int =
        sharedPreferences.getString(key, default.toString())?.toInt() ?: default
    override fun put(editor: SharedPreferences.Editor) {
        editor.putString(key, value.toString())
    }
}

class ParsedFloatOption(key: String, default: Float) : Option<Float>(key, default) {
    override fun peek(sharedPreferences: SharedPreferences): Float =
        sharedPreferences.getString(key, default.toString())?.toFloat() ?: default
    override fun put(editor: SharedPreferences.Editor) {
        editor.putString(key, value.toString())
    }
}

// ddu:r -> motion -> visible:r
val motion = object : SharedPreference<Matrix>(Matrix()) {
    override fun peek(sharedPreferences: SharedPreferences): Matrix {
        with(sharedPreferences) {
            val dx = getFloat("dx", 0f)
            val dy = getFloat("dy", 0f)
            val scale = getFloat("scale", 1f)
            return Matrix().apply { postTranslate(dx, dy); postScale(scale, scale) }
        }
    }
    override fun put(editor: SharedPreferences.Editor) {
        with(editor) {
            putFloat("dx", value.dx)
            putFloat("dy", value.dy)
            putFloat("scale", value.sx) // sx == sy
        }
    }
    override fun remove(editor: SharedPreferences.Editor) {
        setOf("dx", "dy", "scale").forEach { editor.remove(it) }
    }
}
val drawTrace = Option("draw_trace", true)
val updating = Option("updating", true)
val redrawTraceOnMove = Option("redraw_trace", false)
val showAllCircles = Option("show_all_circles", false)
//val showCenters = Option("show_centers", false)
val showOutline = Option("show_outline", false)
val reverseMotion = Option("reverse_motion", false)
val shape = object : Option<Shapes>("shape", Shapes.CIRCLE) {
    override fun peek(sharedPreferences: SharedPreferences): Shapes =
        sharedPreferences.getString(key, default.toString())
            ?.toUpperCase()?.let { Shapes.valueOfOrNull(it) ?: default } ?: default
    override fun put(editor: SharedPreferences.Editor) {
        editor.putString(key, value.toString().toLowerCase())
    }
}
//val rotateShapes = Option("rotate_shapes", false)
val autosave = Option("autosave", false)
val autocenterAlways = Option("autocenter_always", false)
val speed = ParsedFloatOption("speed", 1f)
val canvasFactor = ParsedIntOption("canvas_factor", 2)
val preferRecentDDU = Option("prefer_recent_ddu", true) // TODO: add to preferences

fun <T: Any> SharedPreferences.fetch(
    preference: SharedPreference<T>,
    onPreChange: (T) -> Unit = {}, onPostChange: (T) -> Unit = {}
) = preference.fetch(this, onPreChange, onPostChange)

fun <T: Any> SharedPreferences.Editor.put(preference: SharedPreference<T>) =
    preference.put(this)
fun <T: Any> SharedPreferences.Editor.set(preference: SharedPreference<T>, value: T? = null) =
    preference.set(value, this)
fun <T: Any> SharedPreferences.Editor.remove(preference: SharedPreference<T>) {
    preference.remove(this)
}

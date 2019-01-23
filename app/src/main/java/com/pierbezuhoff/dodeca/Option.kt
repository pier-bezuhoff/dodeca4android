package com.pierbezuhoff.dodeca

import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.Matrix
import androidx.annotation.BoolRes

// cannot see better solution yet
// MUST NOT be changed after set up from MainActivity
lateinit var options: Options private set
lateinit var values: Values private set

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


class Options(val resources: Resources) {
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
    private fun BooleanOption(key: String, @BoolRes id: Int): Option<Boolean> = Option(key, resources.getBoolean(id))
    val drawTrace = BooleanOption("draw_trace", R.bool.draw_trace)
    val updating = BooleanOption("updating", R.bool.updating)
    val redrawTraceOnMove = BooleanOption("redraw_trace", R.bool.redraw_trace)
    val showAllCircles = BooleanOption("show_all_circles", R.bool.show_all_circles)
    // val showCenters = Option("show_centers", false)
    val showOutline = BooleanOption("show_outline", R.bool.show_outline)
    val reverseMotion = BooleanOption("reverse_motion", R.bool.reverse_motion)
    val shape = object : Option<Shapes>(
        "shape",
        Shapes.valueOfOrNull(resources.getString(R.string.shape)) ?: Shapes.CIRCLE
    ) {
        override fun peek(sharedPreferences: SharedPreferences): Shapes =
            sharedPreferences.getString(key, default.toString())
                ?.toUpperCase()?.let { Shapes.valueOfOrNull(it) ?: default } ?: default

        override fun put(editor: SharedPreferences.Editor) {
            editor.putString(key, value.toString().toLowerCase())
        }
    }
    // val rotateShapes = Option("rotate_shapes", false)
    val autosave = BooleanOption("autosave", R.bool.autosave)
    val autocenterAlways = BooleanOption("autocenter_always", R.bool.autocenter_always)
    val speed = ParsedFloatOption("speed", resources.getString(R.string.speed).toFloatOrNull() ?: 1f)
    val canvasFactor = ParsedIntOption("canvas_factor", resources.getString(R.string.canvas_factor).toIntOrNull() ?: 2)
    val preferRecentDDU = BooleanOption("prefer_recent_ddu", R.bool.prefer_recent_ddu) // TODO: add to preferences
    val previewSize = ParsedIntOption("preview_size", resources.getString(R.string.preview_size).toIntOrNull() ?: 300)
    val nPreviewUpdates = ParsedIntOption("n_preview_updates", resources.getString(R.string.n_preview_updates).toIntOrNull() ?: 100)
    val previewSmartUpdates = BooleanOption("preview_smart_updates", R.bool.preview_smart_updates)

    init {
        if (!::options.isInitialized) {
            options = this
            values = Values(this)
        }
    }
}

class Values(options: Options) {
    val motion: Matrix get() = options.motion.value
    val drawTrace: Boolean get() = options.drawTrace.value
    val updating: Boolean get() = options.updating.value
    val redrawTraceOnMove: Boolean get() = options.redrawTraceOnMove.value
    val showAllCircles: Boolean get() = options.showAllCircles.value
    val showOutline: Boolean get() = options.showOutline.value
    val reverseMotion: Boolean get() = options.reverseMotion.value
    val shape: Shapes get() = options.shape.value
    val autosave: Boolean get() = options.autosave.value
    val autocenterAlways: Boolean get() = options.autocenterAlways.value
    val speed: Float get() = options.speed.value
    val canvasFactor: Int get() = options.canvasFactor.value
    val preferRecentDDU: Boolean get() = options.preferRecentDDU.value
    val previewSize: Int get() = options.previewSize.value
    val nPreviewUpdates: Int get() = options.nPreviewUpdates.value
    val previewSmartUpdates: Boolean get() = options.previewSmartUpdates.value
}
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

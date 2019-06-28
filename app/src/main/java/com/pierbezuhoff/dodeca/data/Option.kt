package com.pierbezuhoff.dodeca.data

import android.content.SharedPreferences
import android.content.res.Resources
import android.util.DisplayMetrics
import androidx.annotation.BoolRes
import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.pierbezuhoff.dodeca.R
import com.pierbezuhoff.dodeca.utils.Filename
import kotlin.reflect.KProperty

// TODO: migrate to OptionsViewModel
// initialization (from MainActivity): `Options(resources: Resources)`
lateinit var options: Options private set
lateinit var values: Values private set

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

@Suppress("FunctionName")
class Options(val resources: Resources) {
    private fun BooleanKeyOption(key: String, @BoolRes id: Int): KeyOption<Boolean> =
        KeyOption(key, resources.getBoolean(id))
    private fun <T : Any> ParsedKeyOption(key: String, @StringRes id: Int, parse: String.() -> T?): ParsedKeyOption<T> =
        ParsedKeyOption(key, resources.getString(id).parse()!!, parse)
    private fun ParsedIntKeyOption(key: String, @StringRes id: Int): ParsedIntKeyOption =
        ParsedIntKeyOption(key, default = resources.getString(id).toInt())
    private fun ParsedFloatKeyOption(key: String, @StringRes id: Int): ParsedFloatKeyOption =
        ParsedFloatKeyOption(key, resources.getString(id).toFloat())
    
    val redrawTraceOnMove = BooleanKeyOption("redraw_trace", R.bool.redraw_trace)
    val showAllCircles = BooleanKeyOption("show_all_circles", R.bool.show_all_circles)
    // val showCenters = KeyOption("show_centers", false)
    val reverseMotion = BooleanKeyOption("reverse_motion", R.bool.reverse_motion)
    // val rotateShapes = KeyOption("rotate_shapes", false)
    val autosave = BooleanKeyOption("autosave", R.bool.autosave)
    val saveAs = BooleanKeyOption("save_as", R.bool.save_as)
    val autocenterAlways = BooleanKeyOption("autocenter_always", R.bool.autocenter_always)
    val speed = ParsedFloatKeyOption("speed", R.string.speed)
    val skipN = ParsedIntKeyOption("skip_n", R.string.skip_n)
    val canvasFactor = ParsedIntKeyOption("canvas_factor", R.string.canvas_factor)
    val showStat = BooleanKeyOption("show_stat", R.bool.show_stat)
    // buildPreview size in pixels, yet to be converted to dp
    val previewSize = ParsedIntKeyOption("preview_size", R.string.preview_size)
    val autocenterPreview = BooleanKeyOption("autocenter_preview", R.bool.autocenter_preview)
    val nPreviewUpdates = ParsedIntKeyOption("n_preview_updates", R.string.n_preview_updates)
    val previewSmartUpdates = BooleanKeyOption("preview_smart_updates", R.bool.preview_smart_updates)
    val showFolders = BooleanKeyOption("show_folders", R.bool.show_folders)
    /** Absolute path of the most recent ddu-file */
    val recentDdu = ParsedKeyOption("recent_ddu", R.string.first_ddu) { Filename(this) }
    val versionCode = KeyOption("version_code", resources.getInteger(R.integer.version_code))

    fun init() {
        if (!::options.isInitialized) {
            options = this
            values = Values(this)
        }
    }
}

class Values(private val options: Options) {
    val redrawTraceOnMove: Boolean by options.redrawTraceOnMove
    val showAllCircles: Boolean by options.showAllCircles
    val reverseMotion: Boolean by options.reverseMotion
    val autosave: Boolean by options.autosave
    val saveAs: Boolean by options.saveAs
    val autocenterAlways: Boolean by options.autocenterAlways
    val speed: Float by options.speed
    val skipN: Int by options.skipN
    val canvasFactor: Int by options.canvasFactor
    val showStat: Boolean by options.showStat
    val previewSize: Int by options.previewSize
    val autocenterPreview: Boolean by options.autocenterPreview
    val previewSizePx: Int get() = options.resources.dp2px(values.previewSize)
    val nPreviewUpdates: Int by options.nPreviewUpdates
    val previewSmartUpdates: Boolean by options.previewSmartUpdates
    val showFolders: Boolean by options.showFolders
    /** Absolute path of the most recent ddu-file */
    val recentDdu: Filename by options.recentDdu // TODO: Filename -> File
    val versionCode: Int by options.versionCode
}

private fun Resources.dp2px(dp: Int): Int =
    dp * displayMetrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT

private fun Resources.px2dp(px: Int): Int =
    px * DisplayMetrics.DENSITY_DEFAULT / displayMetrics.densityDpi


package com.pierbezuhoff.dodeca.models

import android.content.Context
import android.content.res.Resources
import android.util.DisplayMetrics
import androidx.annotation.BoolRes
import androidx.annotation.StringRes
import androidx.core.content.edit
import com.pierbezuhoff.dodeca.R
import com.pierbezuhoff.dodeca.data.KeyOption
import com.pierbezuhoff.dodeca.data.Option
import com.pierbezuhoff.dodeca.data.ParsedFloatKeyOption
import com.pierbezuhoff.dodeca.data.ParsedIntKeyOption
import com.pierbezuhoff.dodeca.data.ParsedKeyOption
import com.pierbezuhoff.dodeca.utils.Filename
import org.jetbrains.anko.defaultSharedPreferences

// MAYBE: the context should be the *application*
class OptionsManager(context: Context) {
    private val sharedPreferences = context.defaultSharedPreferences
    val options: Options
    val values: Values

    init {
        // MAYBE: lock smth for thread safety
        if (!initialized) {
            val resources = context.resources
            _options = Options(resources)
            _values = Values(_options!!, resources)
            initialized = true
        }
        options = _options!!
        values = _values!!
    }

    class Values(options: Options, resources: Resources) {
        val redrawTraceOnMove: Boolean by options.redrawTraceOnMove
        val reverseMotion: Boolean by options.reverseMotion
        val autosave: Boolean by options.autosave
        val saveAs: Boolean by options.saveAs
        val autocenterAlways: Boolean by options.autocenterAlways
        val speed: Float by options.speed
//        val skipN: Int by options.skipN
//        val skipNTimeout: Int by options.skipNTimeout
        val circleGroupImplementation: String by options.circleGroupImplementation
        val projR: Float by options.projR
        val canvasFactor: Int by options.canvasFactor
        val showStat: Boolean by options.showStat
        val drawScreenFillingCircles: Boolean by options.drawScreenFillingCircles
        val screenMinSize: Int by options.screenMinSize
        val previewSize: Int by options.previewSize
        val autocenterPreview: Boolean by options.autocenterPreview
        private val densityDpi = resources.displayMetrics.densityDpi
        val previewSizePx: Int get() =
            previewSize * densityDpi / DisplayMetrics.DENSITY_DEFAULT
        val nPreviewUpdates: Int by options.nPreviewUpdates
        val previewSmartUpdates: Boolean by options.previewSmartUpdates
        /** Absolute path of the most recent ddu-file */
        val recentDdu: Filename by options.recentDdu // TODO: Filename -> File
        val versionCode: Int by options.versionCode
    }

    class Options(resources: Resources) {
        private fun BooleanKeyOption(key: String, @BoolRes id: Int, resources: Resources): KeyOption<Boolean> =
            KeyOption(key, resources.getBoolean(id))
        private fun <T : Any> ParsedKeyOption(key: String, @StringRes id: Int, resources: Resources, parse: String.() -> T?): ParsedKeyOption<T> =
            ParsedKeyOption(key, resources.getString(id).parse()!!, parse)
        private fun ParsedIntKeyOption(key: String, @StringRes id: Int, resources: Resources): ParsedIntKeyOption =
            ParsedIntKeyOption(key, default = resources.getString(id).toInt())
        private fun ParsedFloatKeyOption(key: String, @StringRes id: Int, resources: Resources): ParsedFloatKeyOption =
            ParsedFloatKeyOption(key, resources.getString(id).toFloat())

        val redrawTraceOnMove = BooleanKeyOption("redraw_trace", R.bool.redraw_trace, resources)
        val reverseMotion = BooleanKeyOption("reverse_motion", R.bool.reverse_motion, resources)
        // val rotateShapes = KeyOption("rotate_shapes", false)
        val autosave = BooleanKeyOption("autosave", R.bool.autosave, resources)
        val saveAs = BooleanKeyOption("save_as", R.bool.save_as, resources)
        val autocenterAlways = BooleanKeyOption("autocenter_always", R.bool.autocenter_always, resources)
        val speed = ParsedFloatKeyOption("speed", R.string.speed, resources)
        val angularSpeedFactor = ParsedFloatKeyOption("angular_speed_factor", R.string.angular_speed_factor, resources)
        val skipN = ParsedIntKeyOption("skip_n", R.string.skip_n, resources)
        val skipNTimeout = ParsedIntKeyOption("skip_n_timeout", R.string.skip_n_timeout, resources)
        val circleGroupImplementation = ParsedKeyOption("circlegroup_implementation", R.string.circlegroup_implementation, resources) { this }
        val projR = ParsedFloatKeyOption("projective_sphere_radius", R.string.projective_sphere_radius, resources)
        val canvasFactor = ParsedIntKeyOption("canvas_factor", R.string.canvas_factor, resources)
        val showStat = BooleanKeyOption("show_stat", R.bool.show_stat, resources)
        val drawScreenFillingCircles = BooleanKeyOption("draw_screen_filling_circles", R.bool.draw_screen_filling_circles, resources)
        val screenMinSize = ParsedIntKeyOption("screen_min_size", 0)
        val showMassEditorButton = BooleanKeyOption("show_mass_editor_button", R.bool.show_mass_editor_button, resources)
        val showTraceButton = BooleanKeyOption("show_trace_button", R.bool.show_trace_button, resources)
        val showClearButton = BooleanKeyOption("show_clear_button", R.bool.show_clear_button, resources)
        val showAutocenterButton = BooleanKeyOption("show_autocenter_button", R.bool.show_autocenter_button, resources)
        val showRestartButton = BooleanKeyOption("show_restart_button", R.bool.show_restart_button, resources)
        val showFillButton = BooleanKeyOption("show_fill_button", R.bool.show_fill_button, resources)
        // buildPreview size in pixels, yet to be converted to dp
        val previewSize = ParsedIntKeyOption("preview_size", R.string.preview_size, resources)
        val autocenterPreview = BooleanKeyOption("autocenter_preview", R.bool.autocenter_preview, resources)
        val nPreviewUpdates = ParsedIntKeyOption("n_preview_updates", R.string.n_preview_updates, resources)
        val previewSmartUpdates = BooleanKeyOption("preview_smart_updates", R.bool.preview_smart_updates, resources)
        val showFolders = BooleanKeyOption("show_folders", R.bool.show_folders, resources)
        /** Absolute path of the most recent ddu-file */
        val recentDdu: ParsedKeyOption<Filename> =
            ParsedKeyOption("recent_ddu", R.string.first_ddu, resources) { Filename(this) }
        val versionCode = KeyOption("version_code", resources.getInteger(R.integer.version_code))

        internal val allOptions: Set<Option<*>> = setOf(
            redrawTraceOnMove,
            reverseMotion,
            autosave,
            saveAs,
            autocenterAlways,
            speed,
            angularSpeedFactor,
            skipNTimeout,
            skipN,
            canvasFactor,
            showStat,
            drawScreenFillingCircles,
            showMassEditorButton,
            showTraceButton,
            showClearButton,
            showAutocenterButton,
            showRestartButton,
            showFillButton,
            previewSize,
            autocenterPreview,
            nPreviewUpdates,
            previewSmartUpdates,
            showFolders,
            recentDdu,
            versionCode
        )
    }


    fun fetchAll() {
        options.allOptions.forEach {
            fetch(it)
        }
    }

    fun <T : Any> fetch(option: Option<T>) {
        option.fetchFrom(sharedPreferences)
    }

    fun <T : Any> fetched(option: Option<T>): T {
        option.fetchFrom(sharedPreferences)
        return option.value
    }

    fun <T : Any> set(option: Option<T>, value: T) {
        sharedPreferences.edit {
            option.setToIn(value, this) // NOTE: setToIn(option, value) means recursion
        }
    }

    fun toggle(option: Option<Boolean>): Boolean {
        val newValue = !option.value
        sharedPreferences.edit {
            option.setToIn(newValue, this)
        }
        return newValue
    }

    companion object {
        private var initialized = false
        private var _options: Options? = null
        private var _values: Values? = null
    }
}

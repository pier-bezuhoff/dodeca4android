package com.pierbezuhoff.dodeca.models

import android.app.Application
import android.content.res.Resources
import androidx.annotation.BoolRes
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import com.pierbezuhoff.dodeca.R
import com.pierbezuhoff.dodeca.data.KeyOption
import com.pierbezuhoff.dodeca.data.ParsedFloatKeyOption
import com.pierbezuhoff.dodeca.data.ParsedIntKeyOption
import com.pierbezuhoff.dodeca.data.ParsedKeyOption
import com.pierbezuhoff.dodeca.data.dp2px
import com.pierbezuhoff.dodeca.utils.Filename

// TODO: migrate to this from options.*
@Suppress("FunctionName")
class OptionsViewModel(application: Application) : AndroidViewModel(application) {
    private val resources: Resources
        get() = getApplication<Application>().applicationContext.resources

    val values: Values = Values(this)

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
    val skipNTimeout = ParsedIntKeyOption("skip_n_timeout", R.string.skip_n_timeout)
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

    class Values(private val options: OptionsViewModel) {
        val redrawTraceOnMove: Boolean by options.redrawTraceOnMove
        val showAllCircles: Boolean by options.showAllCircles
        val reverseMotion: Boolean by options.reverseMotion
        val autosave: Boolean by options.autosave
        val saveAs: Boolean by options.saveAs
        val autocenterAlways: Boolean by options.autocenterAlways
        val speed: Float by options.speed
        val skipN: Int by options.skipN
        val skipNTimeout: Int by options.skipNTimeout
        val canvasFactor: Int by options.canvasFactor
        val showStat: Boolean by options.showStat
        val previewSize: Int by options.previewSize
        val autocenterPreview: Boolean by options.autocenterPreview
        val previewSizePx: Int get() = options.resources.dp2px(previewSize)
        val nPreviewUpdates: Int by options.nPreviewUpdates
        val previewSmartUpdates: Boolean by options.previewSmartUpdates
        val showFolders: Boolean by options.showFolders
        /** Absolute path of the most recent ddu-file */
        val recentDdu: Filename by options.recentDdu // TODO: Filename -> File
        val versionCode: Int by options.versionCode
    }
}



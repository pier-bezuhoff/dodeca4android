package com.pierbezuhoff.dodeca.models

import android.app.Application
import android.content.res.Resources
import android.util.DisplayMetrics
import androidx.annotation.BoolRes
import androidx.lifecycle.AndroidViewModel
import com.pierbezuhoff.dodeca.R
import com.pierbezuhoff.dodeca.data.KeyOption
import com.pierbezuhoff.dodeca.data.ParsedFloatKeyOption
import com.pierbezuhoff.dodeca.data.ParsedIntKeyOption

// TODO: migrate to it from options.*
class OptionsViewModel(application: Application) : AndroidViewModel(application) {
    private val resources: Resources
        get() = getApplication<Application>().applicationContext.resources

    val values: Values = Values(this)

    private fun dp2px(dp: Int): Int =
        dp * resources.displayMetrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT

    private fun px2dp(px: Int): Int =
        px * DisplayMetrics.DENSITY_DEFAULT / resources.displayMetrics.densityDpi

    @Suppress("FunctionName")
    private fun BooleanOption(key: String, @BoolRes id: Int): KeyOption<Boolean> =
        KeyOption(key, resources.getBoolean(id))

    val redrawTraceOnMove =
        BooleanOption("redraw_trace", R.bool.redraw_trace)
    val showAllCircles =
        BooleanOption("show_all_circles", R.bool.show_all_circles)
    // val showCenters = KeyOption("show_centers", false)
    val reverseMotion =
        BooleanOption("reverse_motion", R.bool.reverse_motion)
    // val rotateShapes = KeyOption("rotate_shapes", false)
    val autosave =
        BooleanOption("autosave", R.bool.autosave)
    val saveAs =
        BooleanOption("save_as", R.bool.save_as)
    val autocenterAlways =
        BooleanOption("autocenter_always", R.bool.autocenter_always)
    val speed = ParsedFloatKeyOption(
        "speed",
        resources.getString(R.string.speed).toFloat()
    )
    val skipN = ParsedIntKeyOption(
        "skip_n",
        resources.getString(R.string.skip_n).toInt()
    )
    val canvasFactor = ParsedIntKeyOption(
        "canvas_factor",
        resources.getString(R.string.canvas_factor).toInt()
    )
    val showStat =
        BooleanOption("show_stat", R.bool.show_stat)
    // buildPreview size in pixels, yet to be converted to dp
    val previewSize = ParsedIntKeyOption(
        "preview_size",
        resources.getString(R.string.preview_size).toInt()
    )
    val autocenterPreview =
        BooleanOption("autocenter_preview", R.bool.autocenter_preview)
    val nPreviewUpdates = ParsedIntKeyOption(
        "n_preview_updates",
        resources.getString(R.string.n_preview_updates).toInt()
    )
    val previewSmartUpdates =
        BooleanOption("preview_smart_updates", R.bool.preview_smart_updates)
//    val recentDdu: KeyOption<Filename> =
//        KeyOption("recent_ddu", resources.getString(R.string.first_ddu))
    val versionCode =
        KeyOption("version_code", resources.getInteger(R.integer.version_code))

    class Values(private val options: OptionsViewModel) {
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
        val previewSizePx: Int get() = options.dp2px(previewSize)
        val nPreviewUpdates: Int by options.nPreviewUpdates
        val previewSmartUpdates: Boolean by options.previewSmartUpdates
//        val recentDdu: Filename by options.recentDdu
        val versionCode: Int by options.versionCode
    }
}



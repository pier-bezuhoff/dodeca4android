package com.pierbezuhoff.dodeca.models

import android.content.SharedPreferences
import androidx.core.content.edit
import com.pierbezuhoff.dodeca.data.Option
import com.pierbezuhoff.dodeca.data.options

/** Handy wrapper, does not hold any data except [SharedPreferences] instance; depends on [options] */
class OptionsManager(
    private val sharedPreferences: SharedPreferences
) {

    fun fetchAll() {
        ALL_OPTIONS.forEach {
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

    fun <T: Any> set(option: Option<T>, value: T) {
        sharedPreferences.edit {
            option.setToIn(value, this) // NOTE: setToIn(option, value) means recursion
        }
    }

    fun toggle(option: Option<Boolean>) {
        sharedPreferences.edit {
            option.setToIn(!option.value, this)
        }
    }

    companion object {
        private val ALL_OPTIONS: Set<Option<*>> = options.run {
            setOf(
                redrawTraceOnMove,
                showAllCircles,
                reverseMotion,
                autosave,
                saveAs,
                autocenterAlways,
                speed,
                skipN,
                canvasFactor,
                showStat,
                previewSize,
                autocenterPreview,
                nPreviewUpdates,
                previewSmartUpdates,
                recentDdu,
                versionCode
            )
        }
    }
}


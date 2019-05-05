package com.pierbezuhoff.dodeca.models

import android.content.SharedPreferences
import androidx.core.content.edit
import com.pierbezuhoff.dodeca.data.SharedPreference
import com.pierbezuhoff.dodeca.data.fetch
import com.pierbezuhoff.dodeca.data.options

class SharedPreferencesModel(
    private val sharedPreferences: SharedPreferences
) : SharedPreferences by sharedPreferences {

    fun loadAll() {
        allPreferences.forEach { sharedPreferences.fetch(it) }
    }

    fun <T: Any> set(sharedPreference: SharedPreference<T>, value: T) {
        sharedPreferences.edit {
            set(sharedPreference, value)
        }
    }

    fun toggle(sharedPreference: SharedPreference<Boolean>) {
        sharedPreferences.edit {
            set(sharedPreference, !sharedPreference.value)
        }
    }

    companion object {
        private val effectivePreferences: Set<SharedPreference<*>> =
            setOf(
                options.showAllCircles,
                options.autocenterAlways,
                options.speed,
                options.skipN,
                options.canvasFactor
            )
        private val secondaryPreferences: Set<SharedPreference<*>> =
            setOf(
                options.redrawTraceOnMove,
                options.reverseMotion,
                options.autosave,
                options.saveAs,
                options.previewSize,
                options.previewSmartUpdates,
                options.nPreviewUpdates,
                options.recentDDU
            )
        private val allPreferences: Set<SharedPreference<*>> = effectivePreferences + secondaryPreferences
     }
}


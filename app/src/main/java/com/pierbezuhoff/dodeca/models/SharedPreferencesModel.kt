package com.pierbezuhoff.dodeca.models

import android.content.SharedPreferences
import androidx.core.content.edit
import com.pierbezuhoff.dodeca.data.SharedPreference
import com.pierbezuhoff.dodeca.data.fetch
import com.pierbezuhoff.dodeca.data.options

class SharedPreferencesModel(
    private val sharedPreferences: SharedPreferences
) {

    fun fetchAll() {
        allPreferences.forEach {
            fetch(it)
        }
    }

    fun <T : Any> fetch(sharedPreference: SharedPreference<T>) {
        sharedPreferences.fetch(sharedPreference)
    }

    fun <T : Any> fetched(sharedPreference: SharedPreference<T>): T {
        sharedPreferences.fetch(sharedPreference)
        return sharedPreference.value
    }

    fun <T: Any> set(sharedPreference: SharedPreference<T>, value: T) {
        sharedPreferences.edit {
            sharedPreference.set(value, this) // NOTE: set(sharedPreference, value) means recursion
        }
    }

    fun toggle(sharedPreference: SharedPreference<Boolean>) {
        sharedPreferences.edit {
            sharedPreference.set(!sharedPreference.value, this)
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
                options.recentDdu
            )
        private val allPreferences: Set<SharedPreference<*>> =
            effectivePreferences + secondaryPreferences
     }
}


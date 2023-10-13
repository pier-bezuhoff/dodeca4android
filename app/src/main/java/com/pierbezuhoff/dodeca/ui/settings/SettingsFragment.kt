package com.pierbezuhoff.dodeca.ui.settings

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.pierbezuhoff.dodeca.BuildConfig
import com.pierbezuhoff.dodeca.R
import com.pierbezuhoff.dodeca.ui.MainActivity
import org.jetbrains.anko.support.v4.email

class SettingsFragment : PreferenceFragmentCompat() {
    var settingsActivity: SettingsActivity? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setupPreferences(rootKey)
    }

    private fun setupPreferences(rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        mapOf(
            "canvas_factor" to R.string.canvas_factor_summary,
            "speed" to R.string.speed_summary,
            "n_preview_updates" to R.string.n_preview_updates_summary,
            "skip_n_timeout" to R.string.skip_n_timeout_summary
        ).forEach { (key, summaryResource) ->
            findPreference<ListPreference>(key)?.summaryProvider =
                Preference.SummaryProvider<ListPreference> { preference ->
                    getString(summaryResource).format(preference.entry)
                }
        }
        findPreference<ListPreference>("preview_size")?.summaryProvider =
            Preference.SummaryProvider<ListPreference> { preference ->
                getString(R.string.preview_size_summary).format(preference.entry, preference.entry)
            }
        setOf("default_ddu", "default_ddus", "discard_previews").forEach { key ->
            hookClick(key) { addExtraResult(key) }
        }
        hookClick("default") {
            context?.let { c ->
                val editor = PreferenceManager.getDefaultSharedPreferences(c).edit()
                editor.clear()
                PreferenceManager.setDefaultValues(c, R.xml.preferences, true)
                editor.apply()
                setupPreferences(rootKey) // a bit recursive, redraw defaults
            }
        }
        setOf("preview_size", "n_preview_updates", "preview_smart_updates").forEach { key ->
            hookClick(key) { addExtraResult("discard_previews") }
        }
        hookClick("support") { sendFeedback() }
        setOf("circlegroup_implementation", "projective_sphere_radius").forEach { key ->
            hookClick(key) { addExtraResult("update_circlegroup") }
        }
        // NOTE: skipN and alike are observed on change by viewModels and handled there
        if (MainActivity.LIMITED_VERSION) {
            ADVANCED_PREFERENCES.forEach { key ->
                val removed = findPreference<Preference>(key)?.let {
                    it.parent?.removePreference(it)
                }
                if (removed == false)
                    Log.w(TAG, "Advanced preference $key was not removed!")
            }
        }
    }

    private inline fun hookClick(param: String, crossinline action: (String) -> Unit) {
        findPreference<Preference>(param)
            ?.setOnPreferenceClickListener { action(param); true }
    }

    private fun hookChange(param: String, action: (String) -> Unit) {
        findPreference<Preference>(param)
            ?.setOnPreferenceChangeListener { _, _ -> action(param); true }
    }

    private fun addExtraResult(key: String, value: Boolean = true) {
        settingsActivity?.resultIntent?.putExtra(key, value)
    }

    private fun sendFeedback() {
        val address = getString(R.string.developer_email) // mine
        val subject = getString(R.string.feedback_subject)
        val appVersion = BuildConfig.VERSION_NAME
        val body = """
            |
            |
            |-----------------------------
            |App version: $appVersion
            |Device OS: Android
            |Device OS version: ${Build.VERSION.RELEASE}
            |Device brand: ${Build.BRAND}
            |Device model: ${Build.MODEL}
            |Device manufacturer: ${Build.MANUFACTURER}
        """.trimMargin()
        email(address, subject, body)
    }

    companion object {
        private const val TAG: String = "SettingsFragment"
        // hidden in limited version
        private val ADVANCED_PREFERENCES = setOf(
//            "angular_speed_factor",
            "circlegroup_implementation",
            "projective_sphere_radius",
//            "show_all_circles",
            "autocenter_always",
            /*"rotate_shapes",*/
//            "skip_n",
            "skip_n_timeout",
//            "show_stat",
//            "draw_screen_filling_circles",
            "autocenter_preview",
            "preview_smart_updates"
        )
    }
}

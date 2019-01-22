package com.pierbezuhoff.dodeca

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SeekBarPreference
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
            "speed" to R.string.speed_summary
            // preview_size, n_preview_updates
        ).forEach { (key, summaryResource) ->
            findPreference<ListPreference>(key)?.summaryProvider =
                Preference.SummaryProvider<ListPreference> { preference ->
                    getString(summaryResource).format(preference.entry)
                }
        }
        val hooking = { param: String, action: (String) -> Unit ->
            findPreference<Preference>(param)?.setOnPreferenceClickListener { action(param); true }
        }
        setOf("default_ddu", "default_ddus", "discard_previews").forEach {
            hooking(it) { settingsActivity?.resultIntent?.putExtra(it, true) }
        }
        hooking("default") {
            val editor = PreferenceManager.getDefaultSharedPreferences(context).edit()
            editor.clear()
            PreferenceManager.setDefaultValues(context, R.xml.preferences, true)
            editor.apply()
            setupPreferences(rootKey) // a bit recursive, update defaults
        }
        hooking("support") { sendFeedback() }
        SeekBarPreference(context)
        if (MainActivity.LIMITED_VERSION) {
            ADVANCED_PREFERENCES.forEach {
                val removed = findPreference<Preference>(it)?.let { it.parent?.removePreference(it) }
                if (removed == false)
                    Log.w("Preferences", "Advanced preference $it was not removed")
            }
        }
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
        private val ADVANCED_PREFERENCES = setOf(
            "show_all_circles", /*"show_centers",*/ /*"rotate_shapes",*/ "show_stat"
        )
    }
}

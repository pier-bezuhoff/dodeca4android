package com.pierbezuhoff.dodeca

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.support.v7.preference.PreferenceFragmentCompat
import android.support.v7.preference.PreferenceManager
import org.jetbrains.anko.support.v4.email

class SettingsFragment : PreferenceFragmentCompat() {
    var settingsActivity: SettingsActivity? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setupPreferences(rootKey)
    }

    private fun setupPreferences(rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        val hooking: (String, (String) -> Unit) -> Unit = { param, action ->
            findPreference(param).setOnPreferenceClickListener { action(param); true }
        }
        setOf("autocenter", "default_ddu", "default_ddus").forEach {
            hooking(it) { settingsActivity?.resultIntent?.putExtra(it, true) }
        }
        hooking("default") {
            val editor = PreferenceManager.getDefaultSharedPreferences(context).edit()
            editor.clear()
            PreferenceManager.setDefaultValues(context, R.xml.preferences, true)
            editor.apply()
            setupPreferences(rootKey) // a bit recursive, update defaults
        }
        hooking("support") { sendFeedback(context) }
    }

    private fun sendFeedback(context: Context?) {
        val address = getString(R.string.developer_email) // mine
        val subject = getString(R.string.feedback_subject)
        val appVersion = context?.packageManager?.getPackageInfo(context.packageName, 0)?.versionName ?: "-"
        val body = """
            |
            |
            |-----------------------------
            | Device OS: Android
            | Device OS version: ${Build.VERSION.RELEASE}
            | App version: $appVersion
            | Device brand: ${Build.BRAND}
            | Device model: ${Build.MODEL}
            | Device manufacturer: ${Build.MANUFACTURER}
        """.trimMargin()
        email(address, subject, body)
    }
}

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
        findPreference("autocenter").setOnPreferenceClickListener {
            settingsActivity?.resultIntent?.putExtra("autocenter", true)
            true
        }
        findPreference("default_ddus").setOnPreferenceClickListener {
            settingsActivity?.resultIntent?.putExtra("default_ddus", true)
            true
        }
        findPreference("default").setOnPreferenceClickListener {
            val editor = PreferenceManager.getDefaultSharedPreferences(context).edit()
            editor.clear()
            PreferenceManager.setDefaultValues(context, R.xml.preferences, true)
            editor.commit()
            setupPreferences(rootKey) // a bit recursive
            true
        }
        findPreference("support").setOnPreferenceClickListener {
            sendFeedback(context)
            true
        }
    }

    private fun sendFeedback(context: Context?) {
        val address = "pierbezuhoff2016@gmail.com"
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

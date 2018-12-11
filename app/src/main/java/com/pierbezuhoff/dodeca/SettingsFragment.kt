package com.pierbezuhoff.dodeca

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.support.v7.preference.EditTextPreference
import android.support.v7.preference.PreferenceFragmentCompat
import android.support.v7.preference.PreferenceManager
import org.jetbrains.anko.support.v4.email

class SettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {
    var settingsActivity: SettingsActivity? = null
    private lateinit var sharedPreferences: SharedPreferences

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

    override fun onResume() {
        super.onResume()
//        sharedPreferences = preferenceManager.sharedPreferences
//        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
//        sharedPreferences.all.entries.filterIsInstance(EditTextPreference::class.java).forEach { updateSummary(it) }
    }

    override fun onPause() {
//        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        super.onPause()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
//        if (sharedPreferences != null && key != null) {
//            val changedPreference = sharedPreferences.all[key]
//            when (changedPreference) { is EditTextPreference -> updateSummary(changedPreference) }
//        }
    }

    private fun updateSummary(preference: EditTextPreference) {
        preference.summary = preference.text
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

package com.pierbezuhoff.dodeca

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.support.v7.preference.PreferenceFragmentCompat
import android.support.v7.preference.PreferenceManager
import org.jetbrains.anko.support.v4.email

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setupPreferences(rootKey)
    }

    private fun setupPreferences(rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
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
        val subject = "Dodeca support"
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
        // following approach does not work ("No Activity found to handle Intent...")
//        val sending = Intent(Intent.ACTION_SENDTO)
//        with (sending) {
//            data = Uri.parse("mailto:")
//            type = "message/rfc822"
//            putExtra(Intent.EXTRA_EMAIL, arrayOf("pierbezuhoff2016@gmail.com"))
//            putExtra(Intent.EXTRA_SUBJECT, "Dodeca support")
//            putExtra(Intent.EXTRA_TEXT, body)
//        }
    }
}
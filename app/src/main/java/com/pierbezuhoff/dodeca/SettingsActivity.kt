package com.pierbezuhoff.dodeca

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    val resultIntent = Intent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        val fragment = SettingsFragment()
        fragment.settingsActivity = this
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_container, fragment)
            .commit()
        setResult(Activity.RESULT_OK, resultIntent)
    }
}

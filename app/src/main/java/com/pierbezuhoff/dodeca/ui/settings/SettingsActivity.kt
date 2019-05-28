package com.pierbezuhoff.dodeca.ui.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.pierbezuhoff.dodeca.R

class SettingsActivity : AppCompatActivity() {
    val resultIntent = Intent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        // maybe: use some androidx.ktx
        val fragment = SettingsFragment()
        fragment.settingsActivity = this
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_container, fragment)
            .commit()
        setResult(Activity.RESULT_OK, resultIntent)
    }
}

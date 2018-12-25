package com.pierbezuhoff.dodeca

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_help.*

class HelpActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.help_title)
        setContentView(R.layout.activity_help)
        help.loadUrl("file:///android_res/raw/help.html")
    }
}

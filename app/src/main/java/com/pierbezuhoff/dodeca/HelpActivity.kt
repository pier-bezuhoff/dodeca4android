package com.pierbezuhoff.dodeca

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_help.*

class HelpActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)
        generalHelpMarkDown.loadMarkdown("""
        | ### General help
        | # Bottom bar
        | - Toggle by tapping area above it
        | - Long tap on button shows brief explanation
        | - More detailed explanation of each button further
        """.trimMargin())
    }
}

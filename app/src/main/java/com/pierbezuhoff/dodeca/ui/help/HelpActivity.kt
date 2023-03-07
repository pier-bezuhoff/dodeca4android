package com.pierbezuhoff.dodeca.ui.help

import android.os.Bundle
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import com.pierbezuhoff.dodeca.R
import java.util.Locale

class HelpActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.help_title)
        setContentView(R.layout.activity_help)
        val htmls = mapOf(
            "en" to "file:///android_asset/help/help.html",
            "ru" to "file:///android_asset/help/help-ru.html"
        )
        var htmlPath: String = htmls["en"]!!
        val locale = Locale.getDefault().toString()
        htmls.keys.find {
            locale.contains(it, ignoreCase = true)
        }?.let {
            htmlPath = htmls[it]!!
        }
        findViewById<WebView>(R.id.help)
            .loadUrl(htmlPath)
    }
}

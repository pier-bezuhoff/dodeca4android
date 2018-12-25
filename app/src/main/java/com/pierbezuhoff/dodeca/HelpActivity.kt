package com.pierbezuhoff.dodeca

import android.content.res.Resources
import android.os.Bundle
import android.text.Html
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_help.*
import ru.noties.markwon.SpannableConfiguration
import ru.noties.markwon.il.AsyncDrawableLoader

class HelpActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)
        val configuration = SpannableConfiguration.builder(this)
            .asyncDrawableLoader(AsyncDrawableLoader.builder()
                .resources(Resources.getSystem()) // WARNING: deprecated
                .build())
            .build()
        val markdown = """
        | # Help
        | ## Bottom bar
        | - Tap on area above bottom bar to toggle it
        | - Long tap on button to see brief description
        | ### Help button
        | ![help-button]( "Help button")
        | Show this help
        """.trimMargin()
        val html = """
        | <h1>Help</h1>
        | <h2>Bottom bar</h2>
        | <ul>
        |   <li>Tap on area above bottom bar to toggle it</li>
        |   <li>Long tap on button to see brief description</li>
        | </ul>
        """.trimMargin()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N)
            help.text = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
        else
            help.text = Html.fromHtml(html)
//        Markwon.setMarkdown(help, configuration, markdown)
    }
}

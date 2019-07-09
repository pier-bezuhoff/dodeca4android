package com.pierbezuhoff.dodeca.ui.dodecaview

import android.content.Context
import android.util.AttributeSet
import androidx.lifecycle.Observer
import com.pierbezuhoff.dodeca.ui.meta.MetaDodecaView

class DodecaView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null
) : MetaDodecaView(context, attributeSet) {
    override lateinit var viewModel: DodecaViewModel // injected via DataBinding

    override fun setupObservers() {
        super.setupObservers()
        viewModel.bottomBarShown.observe(this, Observer {
            systemUiVisibility = IMMERSIVE_UI_VISIBILITY
        })
    }

    companion object {
        private const val TAG = "DodecaView"
        private const val IMMERSIVE_UI_VISIBILITY = SYSTEM_UI_FLAG_LAYOUT_STABLE or SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or SYSTEM_UI_FLAG_IMMERSIVE or SYSTEM_UI_FLAG_FULLSCREEN or SYSTEM_UI_FLAG_HIDE_NAVIGATION or SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }
}

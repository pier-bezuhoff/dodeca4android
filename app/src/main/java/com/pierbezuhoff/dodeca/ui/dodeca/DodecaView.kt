package com.pierbezuhoff.dodeca.ui.dodeca

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.observe
import com.pierbezuhoff.dodeca.models.DduRepresentation
import com.pierbezuhoff.dodeca.ui.MainViewModel
import com.pierbezuhoff.dodeca.utils.ComplexFF
import org.apache.commons.math3.complex.Complex

class DodecaView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null
) : View(context, attributeSet)
    , LifecycleOwner
    , DduRepresentation.Presenter
{
    lateinit var mainViewModel: MainViewModel // injected via DataBinding
    lateinit var dodecaViewModel: DodecaViewModel // injected via DataBinding

    private var _lifecycle: Lifecycle = LifecycleRegistry(this)
    private var lifecycleInherited: Boolean = false
    private var initialized = false

    private val centerX: Float get() = x + width / 2
    private val centerY: Float get() = y + height / 2

    private val knownSize: Boolean get() = width > 0 || height > 0

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!initialized) {
            onFirstRun()
        }
    }

    private fun onFirstRun() {
        initialized = true
        dodecaViewModel.gestureDetector.registerAsOnTouchListenerFor(this)
        setupObservers()
    }

    private fun setupObservers() {
        require(lifecycleInherited)
        mainViewModel.bottomBarShown.observe(this) {
            systemUiVisibility = IMMERSIVE_UI_VISIBILITY
        }
        dodecaViewModel.dduRepresentation.observe(this) {
            it.connectPresenter(this)
        }
    }

    override fun getCenter(): Complex? =
        if (knownSize && initialized) ComplexFF(centerX, centerY)
        else null

    override fun getSize(): Pair<Int, Int>? = // NOTE: android.utils.Size from API 21
        if (knownSize && initialized) Pair(width, height)
        else null

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        canvas?.let {
            dodecaViewModel.onDraw(canvas)
        }
    }

    override fun redraw() {
        postInvalidate()
    }

    override fun getLifecycle(): Lifecycle =
        _lifecycle

    /** Inherit lifecycle (of MainActivity) */
    fun inheritLifecycle(lifecycleOwner: LifecycleOwner) {
        require(!lifecycleInherited)
        _lifecycle = lifecycleOwner.lifecycle
        lifecycleInherited = true
    }

    companion object {
        private const val TAG = "DodecaView"
        private const val IMMERSIVE_UI_VISIBILITY = SYSTEM_UI_FLAG_LAYOUT_STABLE or SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or SYSTEM_UI_FLAG_IMMERSIVE or SYSTEM_UI_FLAG_FULLSCREEN or SYSTEM_UI_FLAG_HIDE_NAVIGATION or SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }
}


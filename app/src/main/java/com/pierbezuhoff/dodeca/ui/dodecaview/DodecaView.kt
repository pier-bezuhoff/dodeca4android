package com.pierbezuhoff.dodeca.ui.dodecaview

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.pierbezuhoff.dodeca.models.DduRepresentation
import com.pierbezuhoff.dodeca.utils.ComplexFF
import com.pierbezuhoff.dodeca.utils.LifecycleInheritance
import com.pierbezuhoff.dodeca.utils.LifecycleInheritor
import org.apache.commons.math3.complex.Complex

class DodecaView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null
) : View(context, attributeSet)
    , LifecycleInheritor by LifecycleInheritance() // inherited from DodecaViewActivity
    , DduRepresentation.Presenter
{
    lateinit var viewModel: DodecaViewModel // injected via DataBinding

    private var initialized = false

    private val centerX: Float get() = x + width / 2
    private val centerY: Float get() = y + height / 2

    private val knownSize: Boolean get() = width > 0 || height > 0

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val parentLifecycleOwner = findViewTreeLifecycleOwner()!!
        viewModel.dduRepresentation.observe(parentLifecycleOwner) { dduR ->
            dduR.connectPresenter(this)
        }
        viewModel.bottomBarShown.observe(parentLifecycleOwner) {
            systemUiVisibility = IMMERSIVE_UI_VISIBILITY
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!initialized) {
            onFirstRun()
        }
    }

    private fun onFirstRun() {
        initialized = true
        viewModel.gestureDetector.registerAsOnTouchListenerFor(this)
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
            viewModel.onDraw(canvas)
        }
    }

    override fun redraw() {
        postInvalidate()
    }

    companion object {
        private const val TAG = "DodecaView"
        private const val IMMERSIVE_UI_VISIBILITY = SYSTEM_UI_FLAG_LAYOUT_STABLE or SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or SYSTEM_UI_FLAG_IMMERSIVE or SYSTEM_UI_FLAG_FULLSCREEN or SYSTEM_UI_FLAG_HIDE_NAVIGATION or SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }
}


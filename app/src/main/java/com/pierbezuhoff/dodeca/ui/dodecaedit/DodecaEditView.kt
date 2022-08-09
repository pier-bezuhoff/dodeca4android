package com.pierbezuhoff.dodeca.ui.dodecaedit

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import com.pierbezuhoff.dodeca.models.DduRepresentation
import com.pierbezuhoff.dodeca.utils.ComplexFF
import com.pierbezuhoff.dodeca.utils.LifecycleInheritance
import com.pierbezuhoff.dodeca.utils.LifecycleInheritor
import org.apache.commons.math3.complex.Complex

// **very** similar to DodecaView so far
class DodecaEditView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null
) : View(context, attributeSet)
    , LifecycleInheritor by LifecycleInheritance() // inherited from MainActivity
    , DduRepresentation.Presenter
{
    lateinit var viewModel: DodecaEditViewModel // injected via DataBinding

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
        viewModel.gestureDetector.registerAsOnTouchListenerFor(this)
        setupObservers()
    }

    private fun setupObservers() {
        require(lifecycleInherited)
        viewModel.dduRepresentation.observe(this) {
            it.connectPresenter(this)
        }
    }

    override fun getCenter(): Complex? =
        if (knownSize && initialized) ComplexFF(centerX, centerY)
        else null

    override fun getSize(): Pair<Int, Int>? =
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
        private const val TAG = "DodecaEditView"
    }
}


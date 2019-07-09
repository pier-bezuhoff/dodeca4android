package com.pierbezuhoff.dodeca.ui.meta

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.pierbezuhoff.dodeca.models.DduRepresentation
import com.pierbezuhoff.dodeca.utils.ComplexFF
import com.pierbezuhoff.dodeca.utils.LifecycleInheritance
import com.pierbezuhoff.dodeca.utils.LifecycleInheritor
import org.apache.commons.math3.complex.Complex

abstract class MetaDodecaView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null
) : View(context, attributeSet)
    , LifecycleInheritor by LifecycleInheritance() // inherited from _Activity
    , DduRepresentation.Presenter
{
    interface MetaDodecaViewModel {
        val gestureDetector: AttachableGestureDetector
        val dduRepresentation: LiveData<DduRepresentation>
        fun drawInDduRepresentation(canvas: Canvas) {
            dduRepresentation.value?.draw(canvas)
        }
    }
    interface AttachableGestureDetector { fun registerAsOnTouchListenerFor(view: View) }

    /** Should be overridden by `lateinit var viewModel: CustomViewModel`
     * where `CustomViewModel : MetaDodecaView.MetaDodecaViewModel` */
    abstract val viewModel: MetaDodecaViewModel

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

    protected open fun setupObservers() {
        require(lifecycleInherited) { "As [LifecycleInheritor] you should [this.inheritLifecycleOf(LifecycleOwner)]" }
        viewModel.dduRepresentation.observe(this, Observer {
            it.connectPresenter(this)
        })
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
            viewModel.drawInDduRepresentation(canvas)
        }
    }

    override fun redraw() {
        postInvalidate()
    }
}


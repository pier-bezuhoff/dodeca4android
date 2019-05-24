package com.pierbezuhoff.dodeca.ui

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.Observer
import com.pierbezuhoff.dodeca.models.DduRepresentation
import com.pierbezuhoff.dodeca.models.DodecaViewModel
import com.pierbezuhoff.dodeca.models.MainViewModel
import com.pierbezuhoff.dodeca.utils.ComplexFF
import org.apache.commons.math3.complex.Complex

class DodecaView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null
) : View(context, attributeSet),
    LifecycleOwner,
    DduRepresentation.Presenter,
    MainViewModel.OnDestroyMainActivity
{
    lateinit var mainModel: MainViewModel // inject
    lateinit var model: DodecaViewModel // inject
    private val lifecycleRegistry: LifecycleRegistry =
        LifecycleRegistry(this)

    private var initialized = false

    private val centerX: Float get() = x + width / 2
    private val centerY: Float get() = y + height / 2

    private val knownSize: Boolean get() = width > 0 || height > 0

    init {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!initialized) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            onFirstRun()
        }
    }

    private fun onFirstRun() {
        initialized = true
        mainModel.onDestroyMainActivitySubscription.subscribeFrom(this)
        mainModel.bottomBarShown.observe(this, Observer {
            systemUiVisibility = IMMERSIVE_UI_VISIBILITY
        })
        model.gestureDetector.registerAsOnTouchListenerFor(this)
        model.dduRepresentation.observe(this, Observer {
            it.connectPresenter(this)
        })
    }

    override fun getLifecycle(): Lifecycle =
        lifecycleRegistry

    override fun getCenter(): Complex? =
        if (knownSize && initialized) ComplexFF(centerX, centerY)
        else null

    override fun getSize(): Pair<Int, Int>? = // NOTE: android.utils.Size from API 21
        if (knownSize && initialized) Pair(width, height)
        else null

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        canvas?.let {
            model.onDraw(canvas)
        }
    }

    override fun redraw() {
        postInvalidate()
    }

    override fun onDestroyMainActivity() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        model.maybeAutosave()
        Log.i(TAG, "onDestroy")
    }

    companion object {
        private const val TAG = "DodecaView"
        private const val IMMERSIVE_UI_VISIBILITY = SYSTEM_UI_FLAG_LAYOUT_STABLE or SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or SYSTEM_UI_FLAG_IMMERSIVE or SYSTEM_UI_FLAG_FULLSCREEN or SYSTEM_UI_FLAG_HIDE_NAVIGATION or SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }
}


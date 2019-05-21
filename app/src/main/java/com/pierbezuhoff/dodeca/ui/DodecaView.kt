package com.pierbezuhoff.dodeca.ui

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.pierbezuhoff.dodeca.models.DduRepresentation
import com.pierbezuhoff.dodeca.models.DodecaViewModel
import com.pierbezuhoff.dodeca.models.MainViewModel
import com.pierbezuhoff.dodeca.utils.ComplexFF
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.apache.commons.math3.complex.Complex
import kotlin.coroutines.CoroutineContext

class DodecaView(
    context: Context,
    attributeSet: AttributeSet? = null
) : View(context, attributeSet),
    LifecycleOwner,
    CoroutineScope,
    DduRepresentation.Presenter
{
    lateinit var mainModel: MainViewModel // inject
    lateinit var model: DodecaViewModel // inject
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main
    private val lifecycleRegistry: LifecycleRegistry =
        LifecycleRegistry(this)
    private val job: Job = Job()

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
        registerObservers()
    }

    private fun registerObservers() {
        model.dduRepresentation.observeHere {
            it.connectPresenter(this)
        }
        model.gestureDetector.observeHere { detector ->
            detector.registerAsOnTouchListenerFor(this)
        }
        mainModel.bottomBarShown.observeHere {
            systemUiVisibility = IMMERSIVE_UI_VISIBILITY
        }
        // bestowing death
        mainModel.onDestroy.observeHere {
            onDestroy()
        }
    }

    private fun <T> LiveData<T>.observeHere(action: (T) -> Unit) {
        observe(this@DodecaView, Observer(action))
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

    private fun onDestroy() {
        job.cancel()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        model.maybeAutosave()
        Log.i(TAG, "onDestroy")
    }

    companion object {
        private const val TAG = "DodecaView"
        private const val IMMERSIVE_UI_VISIBILITY = SYSTEM_UI_FLAG_LAYOUT_STABLE or SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or SYSTEM_UI_FLAG_IMMERSIVE or SYSTEM_UI_FLAG_FULLSCREEN or SYSTEM_UI_FLAG_HIDE_NAVIGATION or SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }
}


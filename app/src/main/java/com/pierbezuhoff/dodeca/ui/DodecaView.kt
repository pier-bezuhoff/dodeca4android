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
import com.pierbezuhoff.dodeca.data.SharedPreference
import com.pierbezuhoff.dodeca.data.options
import com.pierbezuhoff.dodeca.models.DduRepresentation
import com.pierbezuhoff.dodeca.models.DodecaViewModel
import com.pierbezuhoff.dodeca.models.MainViewModel
import com.pierbezuhoff.dodeca.utils.ComplexFF
import org.apache.commons.math3.complex.Complex
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import kotlin.concurrent.fixedRateTimer

class DodecaView(
    context: Context,
    attributeSet: AttributeSet? = null
) : View(context, attributeSet),
    LifecycleOwner,
    DduRepresentation.Presenter
{
    lateinit var mainModel: MainViewModel // inject
    lateinit var model: DodecaViewModel // inject
    private val lifecycleRegistry: LifecycleRegistry =
        LifecycleRegistry(this)

    private var initialized = false

    private val centerX: Float get() = x + width / 2
    private val centerY: Float get() = y + height / 2

    private val knownSize: Boolean get() = width > 0 || height > 0

//    private var updating: Boolean = false // unused default, cannot have lateinit here

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
        registerOptionsObservers()
        fixedRateTimer("DodecaView-updater", initialDelay = 1L, period = dt.toLong()) {
            if (updating) postInvalidate()
        }
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

    private fun registerOptionsObservers() {
        options.showAllCircles.observeHere {
            postInvalidate()
        }
        // TODO: invoke only when changed
        options.autocenterAlways.observeHere {
            if (it && knownSize)
                autocenter()
        }
        options.canvasFactor.observeHere {
            if (it != model.trace.currentCanvasFactor && knownSize)
                retrace()
        }
        options.speed.observeHere {
            UPS =
                if (it < 1)
                    (it * DEFAULT_UPS).roundToInt()
                else DEFAULT_UPS
        }
        options.skipN.observeHere {
            if (it > 0) {
                Log.i(TAG, "skipping $it updates")
                model.pause()
                doAsync {
                    repeat(it) {
                        circleGroup.update()
                    }
                    // NOTE: slow and diverges
                    // circleGroup.updateTimes(values.skipN, values.reverseMotion)
                    uiThread {
                        model.setSharedPreference(options.skipN, 0)
                        model.resume()
                    }
                }
            }
        }
    }

    private fun <T : Any> SharedPreference<T>.observeHere(action: (T) -> Unit) {
        liveData.observe(this@DodecaView, Observer {
            if (initialized)
                action(it)
        })
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
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        model.maybeAutosave()
        Log.i(TAG, "onDestroy")
    }

    companion object {
        private const val TAG = "DodecaView"
        private const val FPS = 60 // empirical
        const val dt = 1000f / FPS
        private const val IMMERSIVE_UI_VISIBILITY = SYSTEM_UI_FLAG_LAYOUT_STABLE or SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or SYSTEM_UI_FLAG_IMMERSIVE or SYSTEM_UI_FLAG_FULLSCREEN or SYSTEM_UI_FLAG_HIDE_NAVIGATION or SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }
}


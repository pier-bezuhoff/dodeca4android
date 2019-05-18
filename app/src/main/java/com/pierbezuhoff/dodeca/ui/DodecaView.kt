package com.pierbezuhoff.dodeca.ui

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.core.graphics.withMatrix
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.pierbezuhoff.dodeca.R
import com.pierbezuhoff.dodeca.data.SharedPreference
import com.pierbezuhoff.dodeca.data.options
import com.pierbezuhoff.dodeca.data.values
import com.pierbezuhoff.dodeca.models.DduRepresentation
import com.pierbezuhoff.dodeca.models.DodecaViewModel
import com.pierbezuhoff.dodeca.models.MainViewModel
import com.pierbezuhoff.dodeca.utils.ComplexFF
import com.pierbezuhoff.dodeca.utils.Sleepy
import org.apache.commons.math3.complex.Complex
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.toast
import org.jetbrains.anko.uiThread
import kotlin.concurrent.fixedRateTimer
import kotlin.math.roundToInt

class DodecaView(
    context: Context,
    attributeSet: AttributeSet? = null
) : View(context, attributeSet),
    LifecycleOwner,
    DduRepresentation.Presenter
{
    private val lifecycleRegistry: LifecycleRegistry = LifecycleRegistry(this)
    lateinit var mainModel: MainViewModel
    lateinit var model: DodecaViewModel

    private var initialized = false

    private val centerX: Float get() = x + width / 2
    private val centerY: Float get() = y + height / 2

    private val knownSize: Boolean get() = width > 0

    private var updating: Boolean = false // unused default, cannot have lateinit here
    private var redrawTraceOnce: Boolean = true
        get() = if (field) { field = false; true } else false

    init {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun getLifecycle(): Lifecycle =
        lifecycleRegistry

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!initialized) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            onFirstRun()
        }
        if (!model.trace.initialized)
            retrace()
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        canvas?.let {
            when {
                updating -> updateCanvas(canvas)
                model.updateOnce -> updateCanvas(canvas)
                drawTrace -> canvas.drawTraceCanvas()
                else -> onCanvas(canvas) {
                    // pause and not drawTrace
                    drawBackground()
                    drawCircles()
                }
            }
        }
    }

    override fun getCenter(): Complex? =
        if (knownSize && initialized) ComplexFF(centerX, centerY)
        else null

    override fun getSize(): Pair<Int, Int>? =
        if (knownSize && initialized) Pair(width, height)
        else null

    private fun onFirstRun() {
        if (!initialized) {
            initialized = true
            registerOptionsObservers()
            registerObservers()
            fixedRateTimer("DodecaView-updater", initialDelay = 1L, period = dt.toLong()) {
                if (updating) postInvalidate()
            }
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

    private fun registerObservers() {
        model.dduRepresentation.observeHere {
            it.connectPresenter(this)
            on new ddu
        }
        model.circleGroup.observeHere {
            circleGroup = it
            postInvalidate()
        }
        model.updating.observeHere {
            updating = it
            if (it) {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
                postInvalidate()
            } else {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            }
        }
        model.drawTrace.observeHere {
            drawTrace = it
            if (it) retrace()
            else if (model.trace.initialized) {
                model.trace.clear()
                postInvalidate()
            }
        }
        model.shape.observeHere {
            shape = it
            postInvalidate()
        }

        model.gestureDetector.observeHere { detector ->
            detector.registerAsOnTouchListenerFor(this)
        }
        model.oneStepRequest.observeHere { oneStep() }
        model.clearRequest.observeHere { retrace() }
        model.autocenterRequest.observeHere { autocenter() }

        mainModel.bottomBarShown.observeHere {
            systemUiVisibility = IMMERSIVE_UI_VISIBILITY
        }
        // bestowing death
        mainModel.onDestroy.observeHere {
            onDestroy()
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

    private fun updateCanvas(canvas: Canvas) {
        val timeToUpdate: Boolean = System.currentTimeMillis() - model.lastUpdateTime >= updateDt.value
        if (updating && timeToUpdate) {
            val times = values.speed.roundToInt()
            drawTimes(times)
        }
        drawUpdatedCanvas(canvas)
    }

    private inline fun drawUpdatedCanvas(canvas: Canvas) {
        if (drawTrace) {
            onTraceCanvas {
                if (redrawTraceOnce)
                    drawBackground()
                drawCircles()
            }
            canvas.drawTraceCanvas()
        } else {
            onCanvas(canvas) {
                drawBackground()
                drawCircles()
            }
        }
    }

    /** Reset trace and invalidate */
    private fun retrace() {
        ? delegate to ddu repr
        tryRetrace()
        model.trace.canvas.concat(model.motion)
        redrawTraceOnce = drawTrace
        if (!updating) {
            onTraceCanvas {
                drawBackground()
                drawCircles()
            }
        }
        invalidate()
    }

    private fun tryRetrace() {
        var done = false
        var canvasFactor = values.canvasFactor
        while (!done) {
            try {
                model.createTrace(width, height)
                done = true
            } catch (e: OutOfMemoryError) {
                e.printStackTrace()
                if (canvasFactor > 1) {
                    val nextFactor = canvasFactor - 1
                    Log.w(TAG, "too large canvasFactor: $canvasFactor -> $nextFactor")
                    context.toast(context.getString(R.string.canvas_factor_oom_toast).format(canvasFactor, nextFactor))
                    canvasFactor = nextFactor
                } else {
                    Log.e(TAG, "min canvasFactor  $canvasFactor is too large! Retrying...")
                    context.toast(context.getString(R.string.minimal_canvas_factor_oom_toast).format(canvasFactor))
                }
            }
        }
        if (canvasFactor != values.canvasFactor)
            model.setSharedPreference(options.canvasFactor, canvasFactor)
    }

    /** Draw bg and circles with motion */
    private inline fun Canvas.drawVisible() =
        onCanvas(this) {
            drawBackground()
            drawCircles()
        }

    private inline fun drawTimes(times: Int = 1) {
        if (times <= 1) {
            updateCircles()
        } else {
            onTraceCanvas {
                drawCirclesTimes(times)
            }
        }
        model.lastUpdateTime = System.currentTimeMillis()
        model.updateStat(times) // FIX: wrong stat
    }

    private inline fun onCanvas(canvas: Canvas, crossinline draw: Canvas.() -> Unit) =
        canvas.withMatrix(model.motion) { draw() }

    private inline fun onTraceCanvas(crossinline draw: Canvas.() -> Unit) =
        model.trace.canvas.draw()

    private inline fun Canvas.drawTraceCanvas() =
        drawBitmap(model.trace.bitmap, model.trace.blitMatrix, model.paint)

    private inline fun Canvas.drawBackground() =
        drawColor(ddu.backgroundColor)

    private inline fun Canvas.drawCircles() {
        circleGroup.draw(this, shape = shape, showAllCircles = values.showAllCircles)
    }

    private inline fun Canvas.drawCirclesTimes(times: Int) {
//        circleGroup.drawTimes(
//            times,
//            values.reverseMotion,
//            canvas, shape = values.shape, showAllCircles = values.showAllCircles, showOutline = values.showOutline
//        )
        // for some mysterious reason this is a bit faster than circleGroup.drawTimes
        repeat(times) {
            drawCircles()
            circleGroup.update(values.reverseMotion)
        }
        drawCircles()
    }

    // 1 step = [speed] updates
    private fun oneStep() {
        model.stop()
        val batch = values.speed.roundToInt()
        drawTimes(batch)
        model.requestUpdateOnce()
        postInvalidate()
    }

    private inline fun updateCircles() {
        circleGroup.update(values.reverseMotion)
    }

    private fun onDestroy() {
        !disconnect DR
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        model.maybeAutosave()
        Log.i(TAG, "onDestroy")
    }

    companion object {
        private const val TAG = "DodecaView"
        private const val FPS = 60 // empirical
        private const val DEFAULT_UPS = 60 // empirical
        private var UPS = DEFAULT_UPS
            set(value) { field = value; updateDt.awake() }
        const val dt = 1000f / FPS
        val updateDt = Sleepy { 1000f / UPS }
        private const val IMMERSIVE_UI_VISIBILITY = SYSTEM_UI_FLAG_LAYOUT_STABLE or SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or SYSTEM_UI_FLAG_IMMERSIVE or SYSTEM_UI_FLAG_FULLSCREEN or SYSTEM_UI_FLAG_HIDE_NAVIGATION or SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }
}


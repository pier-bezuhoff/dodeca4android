package com.pierbezuhoff.dodeca.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
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
import com.pierbezuhoff.dodeca.data.CircleFigure
import com.pierbezuhoff.dodeca.data.CircleGroup
import com.pierbezuhoff.dodeca.data.Ddu
import com.pierbezuhoff.dodeca.data.Shapes
import com.pierbezuhoff.dodeca.data.SharedPreference
import com.pierbezuhoff.dodeca.data.options
import com.pierbezuhoff.dodeca.data.values
import com.pierbezuhoff.dodeca.models.DodecaViewModel
import com.pierbezuhoff.dodeca.models.MainViewModel
import com.pierbezuhoff.dodeca.utils.ComplexFF
import com.pierbezuhoff.dodeca.utils.Just
import com.pierbezuhoff.dodeca.utils.Sleepy
import com.pierbezuhoff.dodeca.utils.asFF
import com.pierbezuhoff.dodeca.utils.dx
import com.pierbezuhoff.dodeca.utils.dy
import com.pierbezuhoff.dodeca.utils.mean
import com.pierbezuhoff.dodeca.utils.minus
import com.pierbezuhoff.dodeca.utils.move
import com.pierbezuhoff.dodeca.utils.sx
import org.apache.commons.math3.complex.Complex
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.toast
import org.jetbrains.anko.uiThread
import java.io.File
import kotlin.concurrent.fixedRateTimer
import kotlin.math.roundToInt

class DodecaView(
    context: Context,
    attributeSet: AttributeSet? = null
) : View(context, attributeSet),
    LifecycleOwner,
    DodecaGestureDetector.ScrollListener,
    DodecaGestureDetector.ScaleListener
{
    private val lifecycleRegistry: LifecycleRegistry = LifecycleRegistry(this)
    lateinit var mainModel: MainViewModel
    lateinit var model: DodecaViewModel

    private var initialized = false

    private val centerX: Float get() = x + width / 2
    private val centerY: Float get() = y + height / 2
    private val center: Complex get() = ComplexFF(centerX, centerY)

    private val knownSize: Boolean get() = width > 0

    private val scale get() = model.motion.sx

    private lateinit var ddu: Ddu
    private lateinit var circleGroup: CircleGroup

    private var updating: Boolean = false // unused default, cannot have lateinit here
    private var drawTrace: Boolean = false // unused default
    private lateinit var shape: Shapes
    private var redrawTraceOnce: Boolean = true
        get() = if (field) { field = false; true } else false

    init {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun getLifecycle(): Lifecycle = lifecycleRegistry

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!initialized) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            firstRun()
            initialized = true
        }
        centerize()
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

    private fun firstRun() {
        if (!initialized) {
            registerOptionsObservers()
            registerObservers()
            model.initFrom(context) // set ddu, defaults and ddu-related LiveData-s
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
        model.ddu.observeHere { onNewDdu(it) }
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
            detector.registerScrollListener(this)
            detector.registerScaleListener(this)
        }
        model.oneStepRequest.observeHere { oneStep() }
        model.clearRequest.observeHere { retrace() }
        model.autocenterRequest.observeHere { autocenter() }

        mainModel.bottomBarShown.observeHere {
            systemUiVisibility = IMMERSIVE_UI_VISIBILITY
        }
        // bestowing death
        mainModel.onDestroy.observeHere {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            maybeAutosave()
            Log.i(TAG, "onDestroy")
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

    private fun onNewDdu(newDdu: Ddu) {
        // NOTE: on first run: some bug may occur, because somewhere: retrace ... centerizeTo ...
        // i.e. white border may appear
        maybeAutosave()
        // defaulting
        ddu = newDdu
        redrawTraceOnce = drawTrace
        if (newDdu.bestCenter == null && knownSize)
            newDdu.bestCenter = if (values.autocenterAlways) newDdu.autoCenter else center
        if (initialized)
            centerize(newDdu)
        postInvalidate()
    }

    private fun centerize(ddu: Ddu? = null) {
        // must occur BEFORE retrace
        if (knownSize)
            (ddu ?: model.ddu.value)?.let { ddu ->
                ddu.bestCenter
                    ?.let { centerizeTo(it) }
                    ?: if (values.autocenterAlways) centerizeTo(ddu.autoCenter)
            }
    }

    private fun autocenter() {
        // maybe: when canvasFactor * scale ~ 1
        // try to fit screen
        // BUG: sometimes skip to else
        if (drawTrace && model.trace.initialized &&
            values.canvasFactor == 1 &&
            1 - 1e-4 < scale && scale < 1 + 1e-1
        ) {
            updateScroll(-model.trace.motion.dx, -model.trace.motion.dy)
        } else {
            val shownCircles: List<CircleFigure> = circleGroup.figures.filter(CircleFigure::show)
            val visibleCenter = shownCircles.map { visible(it.center) }.mean()
            val (dx, dy) = (center - visibleCenter).asFF()
            updateScroll(dx, dy)
        }
    }

    private fun centerizeTo(newCenter: Complex) {
        val visibleNewCenter = visible(newCenter)
        val (dx, dy) = (center - visibleNewCenter).asFF()
        updateScroll(dx, dy)
    }

    private fun updateCanvas(canvas: Canvas) {
        val timeToUpdate: Boolean = System.currentTimeMillis() - model.lastUpdateTime >= updateDt.value
        if (updating && timeToUpdate) {
            val times = values.speed.roundToInt()
            if (times <= 1) {
                updateCircles()
                model.lastUpdateTime = System.currentTimeMillis()
            } else {
                onTraceCanvas {
                    drawCirclesTimes(times)
                }
                model.lastUpdateTime = System.currentTimeMillis()
                model.updateStat(times) // wrong behaviour
                // FIX: wrong stat
            }
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

    private inline fun transform(crossinline transformation: Matrix.() -> Unit) {
        model.motion.transformation()
        if (drawTrace) {
            if (values.redrawTraceOnMove)
                retrace()
            else {
                model.trace.motion.transformation()
                invalidate()
            }
        } else if (!updating)
            invalidate()
    }

    override fun onScroll(dx: Float, dy: Float) = updateScroll(-dx, -dy)
    override fun onScale(scale: Float, focusX: Float, focusY: Float) = updateScale(scale, focusX, focusY)

    private fun updateScroll(ddx: Float, ddy: Float) =
        transform {
            postTranslate(ddx, ddy)
        }

    private fun updateScale(dscale: Float, focusX: Float? = null, focusY: Float? = null) =
        transform {
            postScale(dscale, dscale, focusX ?: centerX, focusY ?: centerY)
        }

    /** Reset trace and invalidate */
    private fun retrace() {
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
                model.trace.retrace(width, height)
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
        if (values.speed.roundToInt() <= 1) {
            updateCircles()
            model.lastUpdateTime = System.currentTimeMillis()
        } else {
            val batch = values.speed.roundToInt()
            onTraceCanvas {
                drawCirclesTimes(batch)
            }
            model.lastUpdateTime = System.currentTimeMillis()
            model.updateStat(batch) // wrong behaviour
        }
        model.requestUpdateOnce()
        postInvalidate()
    }

    private inline fun updateCircles() {
        model.updateStat()
        circleGroup.update(values.reverseMotion)
    }

    /** Scale and translate all figures in ddu according to current view creating new ddu */
    private fun includeChangesIntoCurrentDdu(): Ddu {
        // avoiding name clashes
        val _drawTrace = drawTrace
        val _shape = shape
        return ddu.copy().apply {
            circles.zip(circleGroup.figures) { figure, newFigure ->
                figure.center = visible(figure.center)
                figure.radius *= scale // = visibleRadius(figure.radius)
                return@zip figure.copy(
                    newColor = newFigure.color, newFill = newFigure.fill,
                    newRule = newFigure.rule, newBorderColor = Just(newFigure.borderColor)
                )
            }
            drawTrace = _drawTrace
            bestCenter = center
            shape = _shape
        }
    }

    private inline fun visible(z: Complex): Complex = model.motion.move(z)

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


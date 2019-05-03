package com.pierbezuhoff.dodeca.ui

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.core.content.edit
import androidx.core.graphics.withMatrix
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.Observer
import com.pierbezuhoff.dodeca.R
import com.pierbezuhoff.dodeca.data.CircleFigure
import com.pierbezuhoff.dodeca.data.CircleGroup
import com.pierbezuhoff.dodeca.data.DDU
import com.pierbezuhoff.dodeca.data.Shapes
import com.pierbezuhoff.dodeca.data.Trace
import com.pierbezuhoff.dodeca.data.options
import com.pierbezuhoff.dodeca.data.set
import com.pierbezuhoff.dodeca.data.values
import com.pierbezuhoff.dodeca.models.DodecaViewModel
import com.pierbezuhoff.dodeca.models.MainViewModel
import com.pierbezuhoff.dodeca.models.SharedPreferencesModel
import com.pierbezuhoff.dodeca.utils.ComplexFF
import com.pierbezuhoff.dodeca.utils.DB
import com.pierbezuhoff.dodeca.utils.Just
import com.pierbezuhoff.dodeca.utils.Sleepy
import com.pierbezuhoff.dodeca.utils.asFF
import com.pierbezuhoff.dodeca.utils.dduPath
import com.pierbezuhoff.dodeca.utils.dx
import com.pierbezuhoff.dodeca.utils.dy
import com.pierbezuhoff.dodeca.utils.insertOrUpdate
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
import kotlin.reflect.KMutableProperty0

class DodecaView(
    context: Context,
    attributeSet: AttributeSet? = null
) : View(context, attributeSet), LifecycleOwner {
    private val lifecycleRegistry: LifecycleRegistry = LifecycleRegistry(this)
        .also { it.handleLifecycleEvent(Lifecycle.Event.ON_CREATE) }
    private lateinit var mainModel: MainViewModel
    private lateinit var model: DodecaViewModel
    private lateinit var sharedPreferencesModel: SharedPreferencesModel

    private var initialized = false

    private val centerX: Float get() = x + width / 2
    private val centerY: Float get() = y + height / 2
    private val center: Complex get() = ComplexFF(centerX, centerY)

    private val knownSize: Boolean get() = width > 0

    private val dx get() = model.motion.dx
    private val dy get() = model.motion.dy
    private val scale get() = model.motion.sx

    private lateinit var ddu: DDU
    private lateinit var circleGroup: CircleGroup

    private var updating: Boolean = false // unused default, cannot have lateinit here
    private var drawTrace: Boolean = false // unused default
    private lateinit var shape: Shapes
    private var redrawTraceOnce: Boolean = true

    override fun getLifecycle(): Lifecycle = lifecycleRegistry

    init {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        // listen to activity's onDestroy somehow
    }

    private fun init() {
        registerOnFetchCallbacks()
        sharedPreferencesModel.loadAll()
        registerObservers()
        model.initFrom(context) // invoke set ddu and circleGroup
        fixedRateTimer("DodecaView-updater", initialDelay = 1L, period = dt.toLong()) {
            if (updating) postInvalidate()
        }
    }

    private fun registerOnFetchCallbacks() {
        !do once: not for every view - lifecycle aware
        options.showAllCircles.addOnFetchCallback { postInvalidate() }
        options.autocenterAlways.addOnFetchCallback { if (it && knownSize) autocenter() }
        options.canvasFactor.addOnFetchCallback { if (knownSize) retrace() }
        options.speed.addOnFetchCallback {
            UPS = if (it < 1) (it * DEFAULT_UPS).roundToInt() else DEFAULT_UPS
        }
        options.skipN.addOnFetchCallback {
            if (it > 0) {
                Log.i(TAG, "skipping $it updates")
                // TODO: in separate thread
                repeat(it) {
                    circleGroup.update()
                }
                // NOTE: slow and diverges
                // circleGroup.updateTimes(values.skipN, values.reverseMotion)
                sharedPreferencesModel.edit { set(options.skipN, 0) }
            }
        }
    }

    private fun registerObservers() {
        !handle activity onDestroy -> lifecycle
        model.ddu.observe(this, Observer { onNewDDU(it) })
        model.circleGroup.observe(this, Observer { onNewCircleGroup(it) })
        model.updating.observe(this, Observer {
            updating = it
            if (it)
                postInvalidate()
        })
        model.drawTrace.observe(this, Observer {
            drawTrace = it
            if (it) retrace()
            else if (model.trace.initialized) {
                model.trace.clear()
                if (updating)
                    postInvalidate()
            }
        })
        model.shape.observe(this, Observer {
            shape = it
            postInvalidate()
        })
        mainModel.bottomBarShown.observe(this, Observer {
            systemUiVisibility = IMMERSIVE_UI_VISIBILITY
        })
    }

    private fun onNewDDU(newDDU: DDU) {
        // NOTE: on first run: some bug may occur, because somewhere: retrace ... centerizeTo ...
        // i.e. white border may appear
        if (values.autosave && ::ddu.isInitialized && ddu.file != null) {
            saveDDU()
        }
        // defaulting
        ddu = newDDU
        redrawTraceOnce = drawTrace
        if (newDDU.bestCenter == null && knownSize)
            newDDU.bestCenter = if (values.autocenterAlways) newDDU.autoCenter else center
        if (initialized)
            centerize(newDDU)
        postInvalidate()
    }

    private fun onNewCircleGroup(circleGroup: CircleGroup) {
        this.circleGroup = circleGroup
        ...
    }

    private fun centerize(ddu: DDU? = null) {
        // must occur BEFORE retrace
        if (knownSize)
            (ddu ?: model.ddu.value)?.let { ddu ->
                ddu.bestCenter
                    ?.let { centerizeTo(it) }
                    ?: if (values.autocenterAlways) centerizeTo(ddu.autoCenter)
            }
    }

    fun autocenter() {
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

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!initialized) {
            initialized = true
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            !TODO: listen to self lifecycle
            init()
        }
        centerize()
        if (!model.trace.initialized) retrace()
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        canvas?.let {
            when {
                updating -> updateCanvas(canvas)
                model.updateOnce -> {
                    updateCanvas(canvas)
                }
                drawTrace -> drawTraceCanvas(canvas)
                else -> onCanvas(canvas) {
                    // pause and no options.drawTrace
                    drawBackground(it)
                    drawCircles(it)
                }
            }
        }
    }

    private fun updateCanvas(canvas: Canvas) {
        val timeToUpdate: Boolean = System.currentTimeMillis() - model.lastUpdateTime >= updateDt.value
        if (updating && timeToUpdate) {
            val times = values.speed.roundToInt()
            if (times <= 1) {
                updateCircles()
                model.lastUpdateTime = System.currentTimeMillis()
            } else {
                onTraceCanvas { traceCanvas ->
                    drawCirclesTimes(times, traceCanvas)
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
                    drawBackground(it).also { redrawTraceOnce = false }
                drawCircles(it)
            }
            drawTraceCanvas(canvas)
        } else {
            onCanvas(canvas) {
                drawBackground(it)
                drawCircles(it)
            }
        }
    }

    /* when options.drawTrace: retrace if should do it on move, else do [action] */
    private inline fun updatingTrace(action: (Trace) -> Unit) {
        if (drawTrace) {
            if (values.redrawTraceOnMove)
                retrace()
            else {
                action(model.trace)
                invalidate()
            }
        } else if (updating)
            invalidate()
    }

    /* scroll/translate screen and options.drawTrace */
    fun updateScroll(ddx: Float, ddy: Float) {
        model.motion.postTranslate(ddx, ddy)
        updatingTrace { it.translate(ddx, ddy) }
    }

    /* scale screen and options.drawTrace */
    fun updateScale(dscale: Float, focusX: Float? = null, focusY: Float? = null) {
        model.motion.postScale(dscale, dscale, focusX ?: centerX, focusY ?: centerY)
        updatingTrace { it.scale(dscale, dscale, focusX ?: centerX, focusY ?: centerY) }
    }

    /* when options.drawTrace turns on or sizes change */
    fun retrace() {
        tryRetrace()
        model.trace.canvas.concat(model.motion)
        redrawTraceOnce = drawTrace
        if (!updating) {
            onTraceCanvas {
                drawBackground(it)
                drawCircles(it)
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
            sharedPreferencesModel.set(options.canvasFactor, canvasFactor)
    }

    private inline fun onCanvas(canvas: Canvas, crossinline draw: (Canvas) -> Unit) =
        canvas.withMatrix(model.motion) { draw(this) }

    private inline fun onTraceCanvas(crossinline draw: (Canvas) -> Unit) =
        draw(model.trace.canvas)

    /* draw options.drawTrace canvas on DodecaView canvas */
    private inline fun drawTraceCanvas(canvas: Canvas) =
        canvas.drawBitmap(model.trace.bitmap, model.trace.blitMatrix, model.paint)

    /* draw background color on [canvas] */
    private inline fun drawBackground(canvas: Canvas) =
        canvas.drawColor(ddu.backgroundColor)

    /* draw`figures` on [canvas] */
    private inline fun drawCircles(canvas: Canvas) {
        circleGroup.draw(canvas, shape = shape, showAllCircles = values.showAllCircles)
    }

    private inline fun drawCirclesTimes(times: Int, canvas: Canvas) {
//        circleGroup.drawTimes(
//            times,
//            values.reverseMotion,
//            canvas, shape = values.shape, showAllCircles = values.showAllCircles, showOutline = values.showOutline
//        )
        // for some mysterious reason this is a bit faster than circleGroup.drawTimes
        repeat(times) {
            drawCircles(canvas)
            circleGroup.update(values.reverseMotion)
        }
        drawCircles(canvas)
    }

    fun oneStep() {
        // 1 step = [speed] updates
        if (values.speed.roundToInt() <= 1) {
            updateCircles()
            model.lastUpdateTime = System.currentTimeMillis()
        } else {
            val batch = values.speed.roundToInt()
            onTraceCanvas { traceCanvas ->
                drawCirclesTimes(batch, traceCanvas)
            }
            model.lastUpdateTime = System.currentTimeMillis()
            model.updateStat(batch) // wrong behaviour
        }
        model.requestUpdateOnce()
        model.updating.value = false
        postInvalidate()
    }

    private inline fun updateCircles() {
        model.updateStat()
        circleGroup.update(values.reverseMotion)
    }

    /* scale and translate all figures in ddu according to current view */
    private fun prepareDDUToSave(): DDU {
        // avoiding name clashes
        val _drawTrace = drawTrace
        val _shape = shape
        return ddu.copy().apply {
            circles.forEach {
                it.center = visible(it.center)
                it.radius *= scale // = visibleRadius(it.radius)
            }
            drawTrace = _drawTrace
            bestCenter = center
            shape = _shape
        }
    }

    fun saveDDU(newFile: File? = null) {
        val ddu = prepareDDUToSave()
        if (newFile == null && ddu.file == null) {
            Log.i(TAG, "saveDDU: ddu has no file")
            // then save as
            context.toast(context.getString(R.string.error_ddu_save_no_file_toast))
        } else {
            doAsync {
                ddu.circles = ddu.circles.zip(circleGroup.figures) { figure, newFigure -> figure.copy(
                    newColor = newFigure.color, newFill = newFigure.fill,
                    newRule = newFigure.rule, newBorderColor = Just(newFigure.borderColor)
                ) }
                try {
                    (newFile ?: ddu.file)?.let { file ->
                        Log.i(TAG, "Saving ddu at ${context.dduPath(file)}")
                        ddu.saveStream(file.outputStream())
                        uiThread {
                            context.toast(context.getString(R.string.ddu_saved_toast, context.dduPath(file)))
                        }
                        DB.dduFileDao().insertOrUpdate(file.name) { preview = null }
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                    uiThread {
                        context.toast(context.getString(R.string.error_ddu_save_toast))
                    }
                }
            }
        }
    }

    private inline fun visible(z: Complex): Complex = model.motion.move(z)
    private inline fun visibleRadius(r: Float): Float = model.motion.mapRadius(r)

    companion object {
        private const val TAG = "DodecaView"
        private const val FPS = 60 // empirical
        private const val DEFAULT_UPS = 60 // empirical
        private var UPS = DEFAULT_UPS
            set(value) { field = value; updateDt.awake() }
        const val dt = 1000f / FPS
        val updateDt = Sleepy { 1000f / UPS }
        private const val IMMERSIVE_UI_VISIBILITY = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }
}


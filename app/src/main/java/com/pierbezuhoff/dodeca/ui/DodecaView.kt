package com.pierbezuhoff.dodeca.ui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.core.content.edit
import androidx.core.graphics.withMatrix
import androidx.lifecycle.Observer
import com.pierbezuhoff.dodeca.R
import com.pierbezuhoff.dodeca.data.CircleFigure
import com.pierbezuhoff.dodeca.data.CircleGroup
import com.pierbezuhoff.dodeca.data.CircleGroupImpl
import com.pierbezuhoff.dodeca.data.DDU
import com.pierbezuhoff.dodeca.data.SharedPreference
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
import kotlin.system.measureTimeMillis

class DodecaView(context: Context, attributeSet: AttributeSet? = null) : View(context, attributeSet) {
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

    private fun init() {
        registerOnFetchCallbacks()
        sharedPreferencesModel.loadAll()
        model.ddu.observe(_, Observer {
            ... // set ddu and circleGroup
        })
        model.initFrom(context) // invoke set ddu and circleGroup
        fixedRateTimer("DodecaView-updater", initialDelay = 1L, period = dt.toLong()) {
            if (model.updating) postInvalidate()
        }
    }

    fun centerize(ddu: DDU? = null) {
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
        if (model.drawTrace && model.trace.initialized &&
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
            init()
        }
        centerize()
        if (!model.trace.initialized) retrace()
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        canvas?.let {
            when {
                model.updating -> updateCanvas(canvas)
                model.updateOnce -> {
                    updateCanvas(canvas)
                }
                model.drawTrace -> drawTraceCanvas(canvas)
                else -> onCanvas(canvas) {
                    // pause and no options.drawTrace
                    drawBackground(it)
                    drawCircles(it)
                }
            }
        }
    }

    private fun updateCanvas(canvas: Canvas) {
        val timeToUpdate by lazy { System.currentTimeMillis() - model.lastUpdateTime >= updateDt.value }
        if (model.updating && timeToUpdate) {
            val times = values.speed.roundToInt()
            if (times <= 1) {
                updateCircles()
                model.lastUpdateTime = System.currentTimeMillis()
            } else {
                onTraceCanvas { traceCanvas ->
                    drawCirclesTimes(times, traceCanvas)
                }
                model.lastUpdateTime = System.currentTimeMillis()
                updateStat(times) // wrong behaviour
                // FIX: wrong stat
            }
        }
        drawUpdatedCanvas(canvas)
    }

    private inline fun drawUpdatedCanvas(canvas: Canvas) {
        if (model.drawTrace) {
            onTraceCanvas {
                if (model.redrawTraceOnce)
                    drawBackground(it)
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
        if (model.drawTrace) {
            if (values.redrawTraceOnMove)
                retrace()
            else {
                action(model.trace)
                invalidate()
            }
        } else if (!model.updating)
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
        model._redrawTraceOnce = model.drawTrace
        if (!model.updating) {
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
        canvas.withMatrix(values.motion) { draw(this) }

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
        model.circleGroup.draw(canvas, shape = values.shape, showAllCircles = values.showAllCircles)
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
            model.circleGroup.update(values.reverseMotion)
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
            updateStat(batch) // wrong behaviour
        }
        model.updateOnce = true
        sharedPreferencesModel.set(options.updating, false)
        postInvalidate()
    }

    private inline fun updateCircles() {
        updateStat()
        model.circleGroup.update(values.reverseMotion)
    }

    private inline fun updateStat(times: Int = 1) {
        nUpdates += times * (if (values.reverseMotion) -1 else 1)
        if (showStat) {
            nUpdatesView?.text = context.getString(R.string.stat_n_updates_text).format(nUpdates)
            val overhead = nUpdates - last20NUpdates
            if (overhead >= 20) {
                val delta20 = (lastUpdateTime - last20UpdateTime) / (overhead / 20f) / 1000f
                time20UpdatesView?.text = context.getString(R.string.stat_20_updates_per_text).format(delta20)
                last20NUpdates = nUpdates
                last20UpdateTime = lastUpdateTime
            }
        }
    }

    private fun onNewDDU(newDDU: DDU) {
        // NOTE: on first run: some bug may occur, because somewhere: retrace ... centerizeTo ...
        // i.e. white border may appear
        if (values.autosave && ddu.file != null) {
            saveDDU()
        }
        // defaulting
        redrawTraceOnce = values.drawTrace
        nUpdates = 0
        last20NUpdates = nUpdates // some bugs in stat when nUpdates < 0
        lastUpdateTime = System.currentTimeMillis()
        last20UpdateTime = lastUpdateTime

        circleGroup = CircleGroupImpl(newDDU.circles, paint)
        if (newDDU.bestCenter == null && knownSize)
            newDDU.bestCenter = if (values.autocenterAlways) newDDU.autoCenter else center
        values.motion.reset() // clear scale, BUG: editor.set(options.motion) does not set default
        if (initialized)
            centerize(newDDU)
        editing {
            newDDU.file?.let { file -> putString("recent_ddu", context.dduPath(file)) }
            minorIndependentPreferences.forEach { set(it) }
            minorDDUPreferences.forEach { setFromDDU(newDDU, it) }
            minorPreferences.forEach { onChange(it) }
        }
        postInvalidate()
    }

    /* scale and translate all figures in ddu according to current view */
    private fun prepareDDUToSave(): DDU {
        // avoiding name clashes
        val _drawTrace = values.drawTrace
        val _shape = values.shape
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

    private inline fun visible(z: Complex): Complex = values.motion.move(z)
    private inline fun visibleRadius(r: Float): Float = values.motion.mapRadius(r)

    fun <T : Any> change(option: SharedPreference<T>, value: T) {
        editing { set(option, value) }
        onChange(option, value)
    }

    // after update of option.value for minorPreferences
    private fun <T : Any> onChange(option: SharedPreference<T>, value: T = option.value) {
        when(option) {
            options.drawTrace -> if (knownSize) {
                if (value as Boolean) retrace()
                else if (trace.initialized) {
                    trace.clear()
                    if (!values.updating) postInvalidate()
                }
            }
            options.updating -> if (value as Boolean) postInvalidate()
            options.shape -> postInvalidate()
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
                editing { set(options.skipN, 0) }
            }
        }
    }

    private fun <T : Any> SharedPreferences.Editor.setFromDDU(ddu: DDU, option: SharedPreference<T>) {
        when(option) {
            options.drawTrace -> set(options.drawTrace, ddu.drawTrace)
            options.shape -> set(options.shape, ddu.shape)
        }
    }

    fun toggle(option: SharedPreference<Boolean>) {
        change(option, !option.value)
    }

    companion object {
        private const val FPS = 60 // empirical
        private const val DEFAULT_UPS = 60 // empirical
        private var UPS = DEFAULT_UPS
            set(value) { field = value; updateDt.awake() }
        const val dt = 1000f / FPS
        val updateDt = Sleepy { 1000f / UPS }
        private const val TAG = "DodecaView"
    }
}


internal inline fun upon(prop: KMutableProperty0<Boolean>, action: () -> Unit) {
    if (prop.get()) {
        prop.set(false)
        action()
    }
}


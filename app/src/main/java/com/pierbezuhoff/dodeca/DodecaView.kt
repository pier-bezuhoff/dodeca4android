package com.pierbezuhoff.dodeca

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.preference.PreferenceManager
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.core.graphics.withMatrix
import org.apache.commons.math3.complex.Complex
import org.jetbrains.anko.toast
import java.io.File
import kotlin.concurrent.fixedRateTimer
import kotlin.math.roundToInt
import kotlin.properties.Delegates
import kotlin.reflect.KMutableProperty0
import kotlin.system.measureTimeMillis

// NOTE: condition (width > 0) used when we should know view sizes (otherwise width == height == 0)
class DodecaView(context: Context, attributeSet: AttributeSet? = null) : View(context, attributeSet) {
    // dummy default, actual from init(), because I cannot use lateinit here
    var afterNewDDU: (DDU) -> Unit = {}
    private var initialized = false
    // NOTE: used Delegates.vetoable instead of Delegates.observable because the latter handle after-change
    var ddu: DDU by Delegates.vetoable(DDU(circles = emptyList()))
        { _, _, value -> onNewDDU(value); afterNewDDU(value); true } // before change
    private lateinit var circleGroup: CircleGroup
    val sharedPreferences: SharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(context) }
    var nUpdatesView: TextView? = null
    var time20UpdatesView: TextView? = null

    private var redrawTraceOnce: Boolean = options.drawTrace.default
    private var lastUpdateTime: Long = 0L
    private var updateOnce = false
    private val paint = Paint(defaultPaint)
    private val trace: Trace = Trace(Paint(defaultPaint))
    private var nUpdates: Long = 0L

    // MainActivity should set up this whenever SharedPreference/show_stat changes, maybe use Sleepy or smth
    var showStat = true
    private var last20NUpdates: Long = 0L
    private var last20UpdateTime: Long = 0L

    val dx get() = options.motion.value.dx
    val dy get() = options.motion.value.dy
    val scale get() = options.motion.value.sx
    var pickedColor: Int? = null

    val centerX: Float get() = x + width / 2
    val centerY: Float get() = y + height / 2
    val center: Complex get() = ComplexFF(centerX, centerY)

    private fun init() {
        loadSharedPreferences()
        try {
            val recentDDU by lazy { sharedPreferences.getString("recent_ddu", null) }
            val filename = if (options.preferRecentDDU.value && recentDDU != null) recentDDU else DEFAULT_DDU_FILENAME
            val dduFile = File(File(context.filesDir, "ddu"), filename)
            this.ddu = DDU.readFile(dduFile)
        } catch (e: Exception) {
            e.printStackTrace()
            this.ddu = exampleDDU
        }
        fixedRateTimer("DodecaView-updater", initialDelay = 1L, period = dt.toLong()) {
            if (options.updating.value) // maybe: store in _options.updating field
                postInvalidate()
        }
    }

    fun loadSharedPreferences() {
        loadMajorSharedPreferences()
        with(sharedPreferences) { minorPreferences.forEach { fetch(it) } }
    }

    fun loadMajorSharedPreferences() {
        with(sharedPreferences) {
            effectiveMajorPreferences.forEach { preference -> fetch(preference) { onNew(preference) } }
            secondaryMajorPreferences.forEach { fetch(it) }
        }
    }

    fun centerize(ddu: DDU? = null) {
        // must occur BEFORE retrace
        val targetDDU: DDU = ddu ?: this.ddu
        if (width > 0) { // we know sizes
            if (options.autocenterAlways.value)
                autocenter()
            else if (targetDDU.bestCenter != null)
                centerize(targetDDU.bestCenter!!)
        }
    }

    fun autocenter() {
        val shownCircles = circleGroup.figures.filter(CircleFigure::show)
        val visibleCenter = mean(shownCircles.map { visible(it.center) })
        val (dx, dy) = (center - visibleCenter).asFF()
        updateScroll(dx, dy)
    }

    private fun centerize(newCenter: Complex) {
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
        if (!trace.initialized) retrace()
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        canvas?.let {
            when {
                options.updating.value -> updateCanvas(canvas)
                updateOnce -> {
                    updateCanvas(canvas)
                    updateOnce = false
                }
                options.drawTrace.value -> drawTraceCanvas(canvas)
                else -> onCanvas(canvas) {
                    // pause and no options.drawTrace
                    drawBackground(it)
                    drawCircles(it)
                }
            }
        }
    }

    private fun updateCanvas(canvas: Canvas) {
        val timeToUpdate by lazy { System.currentTimeMillis() - lastUpdateTime >= updateDt.value }
        if (options.updating.value && timeToUpdate) {
            val times = options.speed.value.roundToInt()
            if (times <= 1) {
                updateCircles()
                lastUpdateTime = System.currentTimeMillis()
            } else {
                onTraceCanvas { traceCanvas ->
                    drawCirclesTimes(times, traceCanvas)
                }
                lastUpdateTime = System.currentTimeMillis()
                updateStat(times) // wrong behaviour
                // FIX: wrong stat
            }
        }
        drawUpdatedCanvas(canvas)
    }

    private inline fun drawUpdatedCanvas(canvas: Canvas) {
        if (options.drawTrace.value) {
            onTraceCanvas {
                upon(::redrawTraceOnce) { drawBackground(it) }
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

    /* scroll/translate screen and options.drawTrace */
    fun updateScroll(ddx: Float, ddy: Float) {
        options.motion.value.postTranslate(ddx, ddy)
        updatingTrace { trace.translate(ddx, ddy) }
    }

    /* scale screen and options.drawTrace */
    fun updateScale(dscale: Float, focusX: Float? = null, focusY: Float? = null) {
        options.motion.value.postScale(dscale, dscale, focusX ?: centerX, focusY ?: centerY)
        updatingTrace { trace.scale(dscale, dscale, focusX ?: centerX, focusY ?: centerY) }
    }

    /* when options.drawTrace: retrace if should do it on move, else do [action] */
    private inline fun updatingTrace(action: () -> Unit) {
        if (options.drawTrace.value) {
            if (options.redrawTraceOnMove.value)
                retrace()
            else {
                action()
                invalidate()
            }
        } else if (!options.updating.value)
            invalidate()
    }

    /* when options.drawTrace turns on or sizes change */
    fun retrace() {
        tryRetrace()
        trace.canvas.concat(options.motion.value)
        redrawTraceOnce = options.drawTrace.value
        if (!options.updating.value) {
            onTraceCanvas {
                drawBackground(it)
                drawCircles(it)
            }
        }
        invalidate()
    }

    private fun tryRetrace() {
        var done = false
        while (!done) {
            try {
                trace.retrace(width, height)
                done = true
            } catch (e: OutOfMemoryError) {
                e.printStackTrace()
                val canvasFactor = options.canvasFactor.value
                if (canvasFactor > 1) {
                    val nextFactor = canvasFactor - 1
                    Log.w(TAG, "too large canvasFactor: $canvasFactor -> $nextFactor")
                    context.toast(context.getString(R.string.canvas_factor_oom_toast).format(canvasFactor, nextFactor))
                    editing { options.canvasFactor.set(nextFactor, this) }
                } else {
                    Log.e(TAG, "min canvasFactor  $canvasFactor is too large! Retrying...")
                    context.toast(context.getString(R.string.minimal_canvas_factor_oom_toast).format(canvasFactor))
                }
            }
        }
    }

    private inline fun onCanvas(canvas: Canvas, draw: (Canvas) -> Unit) =
        canvas.withMatrix(options.motion.value) { draw(this) }

    private inline fun onTraceCanvas(draw: (Canvas) -> Unit) =
        draw(trace.canvas)

    /* draw options.drawTrace canvas on DodecaView canvas */
    private inline fun drawTraceCanvas(canvas: Canvas) =
        canvas.drawBitmap(trace.bitmap, trace.blitMatrix, trace.paint)

    /* draw background color on [canvas] */
    private inline fun drawBackground(canvas: Canvas) =
        canvas.drawColor(ddu.backgroundColor)

    /* draw`figures` on [canvas] */
    private inline fun drawCircles(canvas: Canvas) {
        circleGroup.draw(canvas, shape = options.shape.value, showAllCircles = options.showAllCircles.value, showOutline = options.showOutline.value)
    }

    private inline fun drawCirclesTimes(times: Int, canvas: Canvas) {
        circleGroup.drawTimes(times, options.reverseMotion.value, canvas, shape = options.shape.value, showAllCircles = options.showAllCircles.value, showOutline = options.showOutline.value)
//        repeat(times) {
//            drawCircles(canvas)
//            circleGroup.update(reverseMotion.value)
//        }
//        drawCircles(canvas)
    }

    fun oneStep() {
        updateCircles()
        lastUpdateTime = System.currentTimeMillis()
        updateOnce = true
        editing { set(options.updating, false) }
        postInvalidate()
    }

    private inline fun updateCircles() {
        updateStat()
        circleGroup.update(options.reverseMotion.value)
    }

    private inline fun updateStat(times: Int = 1) {
        nUpdates += times * (if (options.reverseMotion.value) -1 else 1)
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
        // NOTE: on first run: some bug may occur, because somewhere: retrace ... centerize ...
        // i.e. white border may appear
        if (options.autosave.value && ddu.file != null) {
            saveDDU()
        }
        // defaulting
        redrawTraceOnce = options.drawTrace.value
        nUpdates = 0
        last20NUpdates = nUpdates // some bugs in stat when nUpdates < 0
        lastUpdateTime = System.currentTimeMillis()
        last20UpdateTime = lastUpdateTime

        circleGroup = CircleGroupImpl(newDDU.circles, paint)
        if (newDDU.bestCenter == null && width > 0)
            newDDU.bestCenter = center
        if (initialized)
            centerize(newDDU)
        editing {
            newDDU.file?.let { file -> putString("recent_ddu", file.name) }
            minorIndependentPreferences.forEach { set(it) }
            minorDDUPreferences.forEach { setFromDDU(newDDU, it) }
            minorPreferences.forEach { onChange(it) }
        }
        postInvalidate()
    }

    /* scale and translate all figures in ddu according to current view */
    private fun prepareDDUToSave(): DDU {
        // avoiding name clashes
        val _drawTrace = options.drawTrace.value
        val _shape = options.shape.value
        val _showOutline = options.showOutline.value
        return ddu.copy().apply {
            circles.forEach {
                it.center = visible(it.center)
                it.radius *= scale // = visibleRadius(it.radius)
            }
            drawTrace = _drawTrace
            bestCenter = center
            shape = _shape
            showOutline = _showOutline
        }
    }

    fun saveDDU() {
        val ddu = prepareDDUToSave()
        if (ddu.file == null) {
            Log.i(TAG, "saveDDU: ddu has no file")
            // then save as
            context.toast(context.getString(R.string.error_ddu_save_no_file_toast))
        } else { // maybe: run in background
            try {
                ddu.file?.let { file ->
                    Log.i(TAG, "Saving ddu at at ${file.path}")
                    ddu.saveStream(file.outputStream())
                    context.toast(context.getString(R.string.ddu_saved_toast) + " ${file.name}")
                    // maybe: store current trace
                    DB.dduFileDao().insertOrUpdate(file.name) { preview = null }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                context.toast(context.getString(R.string.error_ddu_save_toast))
            }
        }
    }

    private inline fun visible(z: Complex): Complex = options.motion.value.move(z)
    private inline fun visibleRadius(r: Float): Float = options.motion.value.mapRadius(r)

    fun <T : Any> change(option: SharedPreference<T>, value: T) {
        editing { option.set(value, this) }
        onChange(option, value)
    }

    // after update of option.value for minorPreferences
    private fun <T : Any> onChange(option: SharedPreference<T>, value: T = option.value) {
        when(option) {
            options.drawTrace -> if (width > 0) {
                if (value as Boolean) retrace()
                else if (trace.initialized) {
                    trace.clear()
                    if (!options.updating.value) postInvalidate()
                }
            }
            options.updating -> if (value as Boolean) postInvalidate()
            options.showOutline -> postInvalidate()
            options.shape -> postInvalidate()
        }
    }

    // after update of option.value for effectiveMajorPreferences
    private fun <T : Any> onNew(option: SharedPreference<T>) {
        when(option) {
            options.showAllCircles -> postInvalidate()
            options.autocenterAlways -> if (option.value as Boolean && width > 0) autocenter()
            options.canvasFactor -> if (width > 0) retrace()
            options.speed -> UPS =
                if (options.speed.value < 1)
                    (options.speed.value * defaultUPS).roundToInt()
                else
                    defaultUPS
        }
    }

    private fun <T : Any> SharedPreferences.Editor.setFromDDU(ddu: DDU, option: SharedPreference<T>) {
        when(option) {
            options.drawTrace -> set(options.drawTrace, ddu.drawTrace)
            options.shape -> set(options.shape, ddu.shape)
            options.showOutline -> set(options.showOutline, ddu.showOutline)
        }
    }

    private inline fun editing(block: SharedPreferences.Editor.() -> Unit) {
        with (sharedPreferences.edit()) {
            this.block()
            apply()
        }
    }

    fun toggle(option: SharedPreference<Boolean>) {
        change(option, !option.value)
    }

    companion object {
        val defaultPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
        }
        private const val FPS = 60 // empirical
        private const val defaultUPS = 60 // empirical
        private var UPS = defaultUPS
            set(value) { field = value; updateDt.awake() }
        const val dt = 1000f / FPS
        val updateDt = Sleepy { 1000f / UPS }
        private val effectiveMajorPreferences: Set<SharedPreference<*>> =
            setOf(options.showAllCircles, options.autocenterAlways, options.speed, options.canvasFactor)
        private val secondaryMajorPreferences: Set<SharedPreference<*>> =
            setOf(options.redrawTraceOnMove, options.reverseMotion, options.autosave, options.previewSize, options.previewSmartUpdates, options.nPreviewUpdates)
        private val majorPreferences: Set<SharedPreference<*>> = effectiveMajorPreferences + secondaryMajorPreferences
        private val minorIndependentPreferences: Set<SharedPreference<*>> =
            setOf(options.motion, options.updating)
        private val minorDDUPreferences: Set<SharedPreference<*>> =
            setOf(options.drawTrace, options.showOutline, options.shape)
        private val minorPreferences: Set<SharedPreference<*>> = minorIndependentPreferences + minorDDUPreferences
        private const val DEFAULT_DDU_FILENAME = "290305_z1_erot2.ddu"
        private const val TAG = "DodecaView"
    }
}


internal inline fun upon(prop: KMutableProperty0<Boolean>, action: () -> Unit) {
    if (prop.get()) {
        prop.set(false)
        action()
    }
}

// performance measurement
internal val times: MutableMap<String, Long> = mutableMapOf()
internal val ns: MutableMap<String, Int> = mutableMapOf()
internal inline fun logMeasureTimeMilis(name: String = "", block: () -> Unit) {
    val time = measureTimeMillis(block)
    if (ns.contains(name)) {
        ns[name] = ns[name]!! + 1
        times[name] = times[name]!! + time
    } else {
        ns[name] = 1
        times[name] = time
    }
    val overall = "%.3f".format(times[name]!!.toFloat()/ns[name]!!)
    Log.i("logMeasureTimeMilis/$name", "overall: ${overall}ms, current: ${time}ms")
}
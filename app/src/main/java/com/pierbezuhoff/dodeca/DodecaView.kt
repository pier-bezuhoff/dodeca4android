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
import kotlin.properties.Delegates
import kotlin.reflect.KMutableProperty0
import kotlin.system.measureTimeMillis

class DodecaView(context: Context, attributeSet: AttributeSet? = null) : View(context, attributeSet) {
    // dummy default, actual from init(), because I cannot use lateinit here
    var ddu: DDU by Delegates.observable(DDU(circles = emptyList()))
        { _, _, value -> onNewDDU(value) }
    private lateinit var circleGroup: CircleGroup
    val sharedPreferences: SharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(context) }
    var nUpdatesView: TextView? = null
    var time20UpdatesView: TextView? = null

    private var redrawTraceOnce: Boolean = drawTrace.default
    private var lastUpdateTime: Long = 0L
    private var updateOnce = false
    private val paint = Paint(defaultPaint)
    private val trace: Trace = Trace(Paint(defaultPaint))
    private var nUpdates: Long = 0L

    var showStat = true // MainActivity should set up this whenever SharedPreference/show_stat changes
    private var last20NUpdates: Long = 0L
    private var last20UpdateTime: Long = 0L

    val dx get() = motion.value.dx
    val dy get() = motion.value.dy
    val scale get() = motion.value.sx
    var pickedColor: Int? = null

    val centerX: Float get() = x + width / 2
    val centerY: Float get() = y + height / 2

    init {
        loadSharedPreferences()
        try {
            val recentDDU by lazy { sharedPreferences.getString("recent_ddu", null) }
            val filename = if (preferRecentDDU.value && recentDDU != null) recentDDU else DEFAULT_DDU_FILENAME
            val dduFile = File(File(context.filesDir, "ddu"), filename)
            this.ddu = DDU.readFile(dduFile)
        } catch (e: Exception) {
            e.printStackTrace()
            this.ddu = exampleDDU
        }
        fixedRateTimer("DodecaView-updater", initialDelay = 1L, period = dt.toLong()) {
            if (updating.value) // maybe: store in _updating field
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

    private fun centerize(ddu: DDU? = null) {
        // check rotation! or move checking to trace.translation
        val targetDDU: DDU = ddu ?: this.ddu
        if (width > 0) { // we know sizes
            if (autocenterAlways.value)
                autocenter()
            else if (targetDDU.bestCenter != null)
                centerize(targetDDU.bestCenter!!)
        }
    }

    fun autocenter() {
        val center = ComplexFF(centerX, centerY)
        val shownCircles = circleGroup.figures.filter(CircleFigure::show)
        val visibleCenter = mean(shownCircles.map { visible(it.center) })
        val (dx, dy) = (center - visibleCenter).asFF()
        updateScroll(dx, dy)
    }

    private fun centerize(newCenter: Complex) {
        val visibleNewCenter = visible(newCenter)
        val center = ComplexFF(centerX, centerY)
        Log.i(TAG, "centering $visibleNewCenter -> $center")
        val (dx, dy) = (visibleNewCenter - center).asFF()
        updateScroll(dx, dy)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerize()
        if (!trace.initialized)
            retrace()
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        canvas?.let {
            when {
                updating.value -> updateCanvas(canvas)
                updateOnce -> {
                    updateCanvas(canvas)
                    updateOnce = false
                }
                drawTrace.value -> drawTraceCanvas(canvas)
                else -> onCanvas(canvas) {
                    // pause and no drawTrace
                    drawBackground(it)
                    drawCircles(it)
                }
            }
        }
    }

    private fun updateCanvas(canvas: Canvas) {
        val timeToUpdate by lazy { System.currentTimeMillis() - lastUpdateTime >= updateDt }
        if (updating.value && timeToUpdate) {
            val nTimes = speed.value
            if (nTimes == 1) {
                updateCircles()
                lastUpdateTime = System.currentTimeMillis()
            } else {
                repeat(speed.value) {
                    updateCircles()
                    lastUpdateTime = System.currentTimeMillis()
                    drawUpdatedCanvas(canvas)
                }
            }
        }
        drawUpdatedCanvas(canvas)
    }

    private inline fun drawUpdatedCanvas(canvas: Canvas) {
        if (drawTrace.value) {
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

    /* scroll/translate screen and drawTrace */
    fun updateScroll(ddx: Float, ddy: Float) {
        motion.value.postTranslate(ddx, ddy)
        updatingTrace { trace.translate(ddx, ddy) }
    }

    /* scale screen and drawTrace */
    fun updateScale(dscale: Float, focusX: Float? = null, focusY: Float? = null) {
        motion.value.postScale(dscale, dscale, focusX ?: centerX, focusY ?: centerY)
        updatingTrace { trace.scale(dscale, dscale, focusX ?: centerX, focusY ?: centerY) }
    }

    /* when drawTrace: retrace if should do it on move, else do [action] */
    private fun updatingTrace(action: () -> Unit) {
        if (drawTrace.value) {
            if (redrawTraceOnMove.value)
                retrace()
            else {
                action()
                invalidate()
            }
        } else if (!updating.value)
            invalidate()
    }

    /* when drawTrace turns on or sizes change */
    fun retrace() {
        tryRetrace()
        trace.canvas.concat(motion.value)
        redrawTraceOnce = drawTrace.value
        if (!updating.value) {
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
                if (canvasFactor.value > 1) {
                    val nextFactor = canvasFactor.value - 1
                    Log.w(TAG, "too large canvasFactor: ${canvasFactor.value} -> $nextFactor")
                    context.toast(resources.getString(R.string.canvas_factor_oom_toast).format(canvasFactor.value, nextFactor))
                    editing { canvasFactor.set(nextFactor, this) }
                } else {
                    Log.e(TAG, "min canvasFactor  ${canvasFactor.value} is too large! Retrying...")
                    context.toast(resources.getString(R.string.minimal_canvas_factor_oom_toast).format(canvasFactor.value))
                }
            }
        }
    }

    private inline fun onCanvas(canvas: Canvas, draw: (Canvas) -> Unit) =
        canvas.withMatrix(motion.value) { draw(this) }

    private inline fun onTraceCanvas(draw: (Canvas) -> Unit) =
        draw(trace.canvas)

    /* draw drawTrace canvas on DodecaView canvas */
    private inline fun drawTraceCanvas(canvas: Canvas) =
        canvas.drawBitmap(trace.bitmap, trace.blitMatrix, trace.paint)

    /* draw background color on [canvas] */
    private inline fun drawBackground(canvas: Canvas) =
        canvas.drawColor(ddu.backgroundColor)

    /* draw`figures` on [canvas] */
    private inline fun drawCircles(canvas: Canvas) {
        circleGroup.draw(canvas, shape = shape.value, showAllCircles = showAllCircles.value, showOutline = showOutline.value)
    }

    fun oneStep() {
        updateCircles()
        lastUpdateTime = System.currentTimeMillis()
        updateOnce = true
        editing { set(updating, false) }
        postInvalidate()
    }

    private inline fun updateCircles() {
        nUpdates += if (reverseMotion.value) -1 else 1
        if (showStat) {
            nUpdatesView?.text = context.getString(R.string.stat_n_updates_text).format(nUpdates)
            if (nUpdates - last20NUpdates >= 20) {
                time20UpdatesView?.text = context.getString(R.string.stat_20_updates_per_text)
                    .format((lastUpdateTime - last20UpdateTime) / 1000f)
                last20NUpdates = nUpdates
                last20UpdateTime = lastUpdateTime
            }
        }
        circleGroup.update(reverseMotion.value)
    }

    private fun onNewDDU(ddu: DDU) {
        circleGroup = PrimitiveCircles(ddu.circles.toMutableList(), paint)
        editing {
            ddu.file?.let { file -> putString("recent_ddu", file.name) }
            minorIndependentPreferences.forEach { set(it) }
            minorDDUPreferences.forEach { setFromDDU(ddu, it) }
        }
        redrawTraceOnce = drawTrace.value
        nUpdates = 0
        last20NUpdates = nUpdates
        lastUpdateTime = System.currentTimeMillis()
        last20UpdateTime = lastUpdateTime
        centerize(ddu)
        invalidate()
    }

    /* scale and translate all figures in ddu according to current view */
    fun prepareDDUToSave(): DDU {
        val _drawTrace = drawTrace.value
        return ddu.copy().apply {
            circles.forEach {
                it.center = visible(it.center)
                it.radius *= scale // = visibleRadius(it.radius)
            }
            drawTrace = _drawTrace
            bestCenter = ComplexFF(centerX, centerY)
        }
    }

    private fun visible(z: Complex): Complex = motion.value.move(z)
    private fun visibleRadius(r: Float): Float = motion.value.mapRadius(r)

    fun <T: Any> change(option: SharedPreference<T>, value: T) {
        editing { option.set(value, this) }
        when(option) {
            drawTrace -> if (value != drawTrace.value && value as Boolean && width > 0) retrace()
            updating, showOutline -> postInvalidate()
        }
        onNew(option)
    }

    // after update of option.value
    private fun <T: Any> onNew(option: SharedPreference<T>) {
        when(option) {
            showAllCircles -> postInvalidate()
            autocenterAlways -> if (option.value as Boolean && width > 0) autocenter()
            canvasFactor -> if (width > 0) retrace()
        }
    }

    private fun <T: Any> SharedPreferences.Editor.setFromDDU(ddu: DDU, option: SharedPreference<T>) {
        when(option) {
            drawTrace -> set(drawTrace, ddu.drawTrace)
            showOutline -> set(showOutline) // TODO: save to ddu
            shape -> set(shape) // TODO: save to ddu
        }
    }

    private fun editing(block: SharedPreferences.Editor.() -> Unit) {
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
        private const val FPS = 100
        private const val UPS = 100
        const val dt = 1000f / FPS
        const val updateDt = 1000f / UPS
        private val effectiveMajorPreferences: Set<SharedPreference<*>> = setOf(showAllCircles, autocenterAlways, canvasFactor)
        private val secondaryMajorPreferences: Set<SharedPreference<*>> = setOf(redrawTraceOnMove, reverseMotion, speed)
        private val majorPreferences: Set<SharedPreference<*>> = effectiveMajorPreferences + secondaryMajorPreferences
        private val minorIndependentPreferences: Set<SharedPreference<*>> = setOf(motion, updating)
        private val minorDDUPreferences: Set<SharedPreference<*>> = setOf(drawTrace, showOutline, shape)
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
internal fun logMeasureTimeMilis(name: String = "", block: () -> Unit) {
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
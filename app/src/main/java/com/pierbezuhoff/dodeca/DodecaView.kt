package com.pierbezuhoff.dodeca

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.preference.PreferenceManager
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.core.graphics.withMatrix
import org.apache.commons.math3.complex.Complex
import java.io.File
import kotlin.concurrent.fixedRateTimer
import kotlin.properties.Delegates
import kotlin.reflect.KMutableProperty0
import kotlin.system.measureTimeMillis

class DodecaView(context: Context, attributeSet: AttributeSet? = null) : View(context, attributeSet) {
    // dummy default, actual from init(), because I cannot use lateinit here
    var ddu: DDU by Delegates.observable(DDU(circles = emptyList()))
        { _, _, value ->
            circleGroup = PrimitiveCircles(value.circles.toMutableList(), paint)
            motion.value.reset()
            drawTrace = value.drawTrace ?: defaultDrawTrace
            redrawTraceOnce = drawTrace
            updating = defaultUpdating
            value.file?.let { file ->
                editing { putString("recent_ddu", file.name) }
            }
            clearMinorSharedPreferences()
            nUpdates = 0
            last20NUpdates = nUpdates
            lastUpdateTime = System.currentTimeMillis()
            last20UpdateTime = lastUpdateTime
            centerize(value)
            invalidate()
        }
    private lateinit var circleGroup: CircleGroup
    val sharedPreferences: SharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(context) }
    var nUpdatesView: TextView? = null
    var time20UpdatesView: TextView? = null
    var drawTrace: Boolean by Delegates.observable(defaultDrawTrace) { _, _, _ ->
        if (width > 0) retrace()
    }

    private var redrawTraceOnce: Boolean = defaultDrawTrace
    var updating = defaultUpdating
    private var lastUpdateTime: Long = 0L
    private var updateOnce = false
    private val paint = Paint(defaultPaint)
    private val trace: Trace = Trace(Paint(defaultPaint))
    private var nUpdates: Long = 0L

    var showStat = true // MainActivity should set up this whenever SharedPreference/show_stat changes
    private var last20NUpdates: Long = 0L
    private var last20UpdateTime: Long = 0L

    // ddu:r -> motion -> visible:r
    private val motion = object : SharedPreference<Matrix>(Matrix()) {
        override fun peek(sharedPreferences: SharedPreferences): Matrix {
            with(sharedPreferences) {
                val dx = getFloat("dx", 0f)
                val dy = getFloat("dy", 0f)
                val scale = getFloat("scale", 1f)
                return Matrix().apply { postTranslate(dx, dy); postScale(scale, scale) }
            }
        }
        override fun put(editor: SharedPreferences.Editor) {
            with(editor) {
                putFloat("dx", value.dx)
                putFloat("dy", value.dy)
                putFloat("scale", value.sx) // sx == sy
            }
        }
    }
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
            val filename = if (preferRecentDDU.value && recentDDU != null) recentDDU else "290305_z1_erot2.ddu"
            val dduFile = File(File(context.filesDir, "ddu"), filename)
            this.ddu = DDU.readFile(dduFile)
        } catch (e: Exception) {
            e.printStackTrace()
            this.ddu = exampleDDU
        }
        fixedRateTimer("DodecaView-updater", initialDelay = 1L, period = dt.toLong()) {
            if (updating)
                postInvalidate()
        }
        saveMinorSharedPreferences()
    }

    fun loadSharedPreferences() {
        loadMajorSharedPreferences()
        loadMinorSharedPreferences()
    }

    fun loadMajorSharedPreferences() {
        var updateImmediately = false
        upon(::autocenterOnce) { autocenter() }
        with(sharedPreferences) {
            listOf(redrawTraceOnMove, reverseMotion, speed).forEach { fetch(it) }
            listOf(showAllCircles, /*showCenters,*/ showOutline, /* rotateShapes,*/ shape).forEach {
                fetch(it) { updateImmediately = true }
            }
            fetch(autocenterAlways) { if (it && width > 0) autocenter() }
            fetch(canvasFactor) { if (width > 0) retrace() }
        }
        if (!updating && updateImmediately)
            invalidate()
    }

    private fun loadMinorSharedPreferences() {
        with (sharedPreferences) {
            fetch(motion)
            drawTrace = getBoolean("drawTrace", drawTrace)
            updating = getBoolean("updating", updating)
        }
    }

    private fun saveMinorSharedPreferences() {
        editing {
            put(motion)
            putBoolean("drawTrace", drawTrace)
            putBoolean("updating", updating)
        }
    }

    private fun clearMinorSharedPreferences() {
        editing {
            setOf("dx", "dy", "scale", "drawTrace", "updating").forEach { remove(it) }
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

    private fun autocenter() {
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
                updating -> updateCanvas(canvas)
                updateOnce -> {
                    updateCanvas(canvas)
                    updateOnce = false
                }
                drawTrace -> drawTraceCanvas(canvas)
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
        if (updating && timeToUpdate) {
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
        if (drawTrace) {
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
        if (drawTrace) {
            if (updating && redrawTraceOnMove.value || !updating && shouldRedrawTraceOnMoveWhenPaused)
                retrace()
            else {
                action()
                invalidate()
            }
        } else if (!updating)
            invalidate()
    }

    /* when drawTrace turns on or sizes change */
    fun retrace() {
        trace.retrace(width, height)
        trace.canvas.concat(motion.value)
        redrawTraceOnce = drawTrace
        if (!updating) {
            onTraceCanvas {
                drawBackground(it)
                drawCircles(it)
            }
        }
        invalidate()
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
        updating = false
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

    /* scale and translate all figures in ddu according to current view */
    fun prepareDDUToSave(): DDU {
        return ddu.copy().apply {
            circles.forEach {
                it.center = visible(it.center)
                it.radius *= scale // = visibleRadius(it.radius)
            }
            drawTrace = this@DodecaView.drawTrace
            bestCenter = ComplexFF(centerX, centerY)
        }
    }

    private fun visible(z: Complex): Complex = motion.value.move(z)
    private fun visibleRadius(r: Float): Float = motion.value.mapRadius(r)

    private fun editing(block: SharedPreferences.Editor.() -> Unit) {
        with (sharedPreferences.edit()) {
            this.block()
            apply()
        }
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
        private var redrawTraceOnMove = Option("redraw_trace", false)
        enum class RedrawOnMoveWhenPaused { ALWAYS, NEVER, RESPECT_REDRAW_TRACE_ON_MOVE }
        private var redrawTraceOnMoveWhenPaused =
            Option("redraw_trace_when_paused", RedrawOnMoveWhenPaused.RESPECT_REDRAW_TRACE_ON_MOVE)
        private val shouldRedrawTraceOnMoveWhenPaused get() = when (redrawTraceOnMoveWhenPaused.value) {
            RedrawOnMoveWhenPaused.ALWAYS -> true
            RedrawOnMoveWhenPaused.NEVER -> false
            RedrawOnMoveWhenPaused.RESPECT_REDRAW_TRACE_ON_MOVE -> redrawTraceOnMove.value
        }
        private var showAllCircles = Option("show_all_circles", false)
//        var showCenters = Option("show_centers", false)
        private var showOutline = Option("show_outline", false)
        private var reverseMotion = Option("reverse_motion", false)
        private var shape = object : Option<Shapes>("shape", Shapes.CIRCLE) {
            override fun peek(sharedPreferences: SharedPreferences): Shapes =
                sharedPreferences.getString(key, default.toString())?.toUpperCase()?.let {
                    if (Shapes.values().map { it.toString() }.contains(it))
                        Shapes.valueOf(it)
                    else default
                } ?: default
            override fun put(editor: SharedPreferences.Editor) {
                editor.putString(key, toString().capitalize())
            }
        }
//        var rotateShapes = Option("rotate_shapes", false)
        var autocenterAlways = Option("autocenter_always", false)
        var autocenterOnce = false
        var speed = ParsedIntOption("speed", 1)
        // maybe: add load random
        var canvasFactor = ParsedIntOption("canvas_factor", 2)
        var preferRecentDDU = Option("prefer_recent_ddu", true) // TODO: add to preferences
        const val defaultDrawTrace = true
        const val defaultUpdating = true
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
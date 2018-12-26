package com.pierbezuhoff.dodeca

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
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

class DodecaView(context: Context, attributeSet: AttributeSet? = null) : View(context, attributeSet) {
    var ddu: DDU = DDU(circles = emptyList()) // dummy, actual from init(), because I cannot use lateinit here
        set(value) {
            field = value
            circles = value.circles.toMutableList()
            motion.value.reset()
            drawTrace = value.trace ?: defaultDrawTrace
            redrawTraceOnce = drawTrace
            updating = defaultUpdating
            value.file?.let { file ->
                editing { putString("recent_ddu", file.name) }
            }
            clearMinorSharedPreferences()
            if (autocenterAlways.value && width > 0) // we know sizes
                autocenter()
            invalidate()
            nUpdates = 0
            last20NUpdates = nUpdates
            lastUpdateTime = System.currentTimeMillis()
            last20UpdateTime = lastUpdateTime
        }
    private lateinit var circles: MutableList<CircleFigure>
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

    private var last20NUpdates: Long = 0L
    private var last20UpdateTime: Long = 0L

    // ddu:r -> motion -> visilbe:r
    private val motion = object : Option<Matrix>("matrix", Matrix()) {
        override fun fetchPreference(sharedPreferences: SharedPreferences): Matrix {
            with(sharedPreferences) {
                val dx = getFloat("dx", 0f)
                val dy = getFloat("dy", 0f)
                val scale = getFloat("scale", 1f)
                return Matrix().apply { postTranslate(dx, dy); postScale(scale, scale) }
            }
        }
        override fun putPreference(editor: SharedPreferences.Editor) {
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
            if (preferRecentDDU.value && recentDDU == null) {
                val exampleDDUFile = File(File(context.filesDir, "ddu"), "290305_z1_erot2.ddu")
                this.ddu = DDU.readFile(exampleDDUFile)
            } else {
                val dduFile = File(File(context.filesDir, "ddu"), recentDDU)
                this.ddu = DDU.readFile(dduFile)
            }
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
        listOf(redrawTraceOnMove, reverseMotion).forEach { it.getPreference(sharedPreferences) }
        listOf(showAllCircles, showCenters, showOutline, rotateShapes, shape).forEach {
            it.getPreference(sharedPreferences) { updateImmediately = true }
        }
        // load FPS/UPS
        // NOTE: restart/rotate screen to update FPS
        autocenterAlways.getPreference(sharedPreferences) {
            if (it && width > 0)
                autocenter()
        }
        if (!updating && updateImmediately)
            invalidate()
    }

    private fun loadMinorSharedPreferences() {
        with (sharedPreferences) {
            motion.getPreference(sharedPreferences)
            drawTrace = getBoolean("drawTrace", drawTrace)
            updating = getBoolean("updating", updating)
        }
    }

    private fun saveMinorSharedPreferences() {
        editing {
            motion.putPreference(this)
            putBoolean("drawTrace", drawTrace)
            putBoolean("updating", updating)
        }
    }

    private fun clearMinorSharedPreferences() {
        editing {
            setOf("dx", "dy", "scale", "drawTrace", "updating").forEach { remove(it) }
        }
    }

    private fun autocenter() {
        val center = ComplexFF(centerX, centerY)
        val visibleCenters = circles.filter(CircleFigure::show).map { visible(center) }
        val (dx, dy) = (center - mean(visibleCenters)).asFF()
        updateScroll(dx, dy)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!trace.initialized)
            retrace()
        if (autocenterAlways.value)
            autocenter()
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        canvas?.let {
            when {
                updating -> updateCanvas(it)
                updateOnce -> {
                    updateCanvas(it)
                    updateOnce = false
                }
                drawTrace -> drawTraceCanvas(it)
                else -> onCanvas(it) { // pause and no trace
                    drawBackground(it)
                    drawCircles(it)
                }
            }
        }
    }

    private fun updateCanvas(canvas: Canvas) {
        val timeToUpdate by lazy { System.currentTimeMillis() - lastUpdateTime >= updateDt }
        if (updating && timeToUpdate) {
            updateCircles()
            lastUpdateTime = System.currentTimeMillis()
        }
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

    fun updateScroll(ddx: Float, ddy: Float) {
        motion.value.postTranslate(ddx, ddy)
        updatingTrace { trace.translate(ddx, ddy) }
    }

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

    private fun onCanvas(canvas: Canvas, draw: (Canvas) -> Unit) {
        canvas.withMatrix(motion.value) { draw(this) }
    }

    private fun onTraceCanvas(draw: (Canvas) -> Unit) {
        draw(trace.canvas)
    }

    /* draw trace canvas on DodecaView canvas */
    private fun drawTraceCanvas(canvas: Canvas) {
        canvas.drawBitmap(
            trace.bitmap,
            trace.blitMatrix,
            trace.paint)
    }

    /* draw background color on [canvas] */
    private fun drawBackground(canvas: Canvas) = canvas.drawColor(ddu.backgroundColor)

    /* draw `circles` on [canvas] */
    private fun drawCircles(canvas: Canvas) {
        circles.filter { it.show || showAllCircles.value }.forEach { drawCircle(canvas, it) }
    }

    /* draw shape from [circle] on [canvas] */
    private fun drawCircle(canvas: Canvas, circle: CircleFigure) {
        val (c, r) = circle
        paint.color = circle.borderColor
        paint.style = if (circle.fill) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
        val (x, y) = c.asFF()
        val (pX, pY) = (c + r * circle.point).asFF()
        shape.value.draw(canvas, x, y, r.toFloat(), pX, pY, circle.point, showOutline.value, paint)
    }

    fun pickColor(x: Float, y: Float) {
        // TODO: color picker/changer or discard
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // layout(left, top, right, bottom)
        draw(canvas)
        // NOTE: sometimes traceBitmap can be used
        pickedColor = bitmap.getPixel(x.toInt(), y.toInt())
        Log.i(TAG, pickedColor.toString())
    }

    /* Change all circles with `pickedColor` -> `newColor` */
    fun changeColor(newColor: Int) {
        // maybe: also change DDU?
        pickedColor?.let {
            circles.filter { it.borderColor == pickedColor }.forEach { it.borderColor = newColor }
        }
        pickedColor = newColor
        postInvalidate()
    }

    fun oneStep() {
        updateCircles()
        lastUpdateTime = System.currentTimeMillis()
        updateOnce = true
        updating = false
        postInvalidate()
    }

    private fun updateCircles() {
        nUpdates += if (reverseMotion.value) -1 else 1
        nUpdatesView?.text = context.getString(R.string.stat_n_updates_text).format(nUpdates)
        if (nUpdates - last20NUpdates >= 20) {
            time20UpdatesView?.text = context.getString(R.string.stat_20_updates_per_text).format((lastUpdateTime - last20UpdateTime) / 1000f)
            last20NUpdates = nUpdates
            last20UpdateTime = lastUpdateTime
        }
        val oldCircles = circles.map { it.copy(newRule = null) }
        val n = circles.size
        oldCircles.forEachIndexed { i, circle ->
            circle.rule?.let { rule ->
                val theRule = if (rule.startsWith("n")) rule.drop(1) else rule
                val chars = if (reverseMotion.value) theRule.reversed() else theRule
                chars.forEach { ch ->
                    val j = Integer.parseInt(ch.toString())
                    if (j >= n) {
                        Log.e(TAG, "updateCircles: index $j >= $n out of `circles` bounds (from rule $rule for $circle)")
                    }
                    else {
                        circles[i].invert(oldCircles[j])
                    }
                }
            }
        }
    }

    /* scale and translate all circles in ddu according to current view */
    fun prepareDDUToSave(): DDU {
        return ddu.copy().apply {
            circles.forEach {
                it.center = visible(it.center)
                it.radius *= scale
            }
            trace = this@DodecaView.drawTrace
        }
    }

    private fun visible(z: Complex): Complex = motion.value.move(z)

    private fun editing(block: SharedPreferences.Editor.() -> Unit) {
        with (sharedPreferences.edit()) {
            this.block()
            apply()
        }
    }

    open class Option<T>(val key: String, val default: T) where T : Any {
        var value: T = default

        open fun fetchPreference(sharedPreferences: SharedPreferences): T = when (default) {
                is Boolean -> sharedPreferences.getBoolean(key, default) as T
                is String -> sharedPreferences.getString(key, default) as T
                is Float -> sharedPreferences.getFloat(key, default) as T
                is Int -> sharedPreferences.getInt(key, default) as T
                is Long -> sharedPreferences.getLong(key, default) as T
                else -> throw Exception("Unsupported type: ${default.javaClass.name}")
            }

        fun getPreference(sharedPreferences: SharedPreferences, onChange: (T) -> Unit = {}): T {
            val newValue: T = fetchPreference(sharedPreferences)
            if (value != newValue)
                onChange(newValue)
            value = newValue
            return value
        }

        open fun putPreference(editor: SharedPreferences.Editor) {
            when (value) {
                is Boolean -> editor.putBoolean(key, value as Boolean)
                is String -> editor.putString(key, value as String)
                is Float -> editor.putFloat(key, value as Float)
                is Int -> editor.putInt(key, value as Int)
                is Long -> editor.putLong(key, value as Long)
                else -> throw Exception("Unsupported type: ${value.javaClass.name}")
            }
        }
    }

    companion object {
        val defaultPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
        }
        // FIX: changing FPS and UPS does not work properly, not in preferences now
        private var FPS = Option("fps", 100)
        private var UPS = Option("ups", 100) // updates per second
        val dt get() = 1000f / FPS.value
        val updateDt get() = 1000f / UPS.value
        private var redrawTraceOnMove = Option("redraw_trace", true)
        enum class RedrawOnMoveWhenPaused { ALWAYS, NEVER, RESPECT_REDRAW_TRACE_ON_MOVE }
        private var redrawTraceOnMoveWhenPaused =
            Option("redraw_trace_when_paused", RedrawOnMoveWhenPaused.RESPECT_REDRAW_TRACE_ON_MOVE)
        private val shouldRedrawTraceOnMoveWhenPaused get() = when (redrawTraceOnMoveWhenPaused.value) {
            RedrawOnMoveWhenPaused.ALWAYS -> true
            RedrawOnMoveWhenPaused.NEVER -> false
            RedrawOnMoveWhenPaused.RESPECT_REDRAW_TRACE_ON_MOVE -> redrawTraceOnMove.value
        }
        private var showAllCircles = Option("show_all_circles", false)
        var showCenters = Option("show_centers", false)
        private var showOutline = Option("show_outline", false)
        private var reverseMotion = Option("reverse_motion", false)
        private var shape = object : Option<Shapes>("shape", Shapes.CIRCLE) {
            override fun fetchPreference(sharedPreferences: SharedPreferences): Shapes =
                sharedPreferences.getString(key, default.toString())?.toUpperCase()?.let { Shapes.valueOf(it) } ?: default
        }
        var rotateShapes = Option("rotate_shapes", false)
        var autocenterAlways = Option("autocenter_always", true)
        var autocenterOnce = false
        // maybe: add load random
        var preferRecentDDU = Option("prefer_recent_ddu", true) // TODO: add to preferences
        const val defaultDrawTrace = true
        const val defaultUpdating = true
        private const val TAG = "DodecaView"
    }
}

enum class Shapes {
    CIRCLE, POINTED_CIRCLE, SQUARE, CROSS, VERTICAL_BAR, HORIZONTAL_BAR;

    fun draw(canvas: Canvas, x: Float, y: Float, halfWidth: Float, pointX: Float, pointY: Float, point: Complex, drawOutline: Boolean, paint: Paint) {
        val center by lazy { Complex(x.toDouble(), y.toDouble()) }
        fun trueRotated(x: Float, y: Float): PointF =
            (center + (Complex(x.toDouble(), y.toDouble()) - center) * point).run { PointF(real.toFloat(), imaginary.toFloat()) }
        val rotated: (Float, Float) -> PointF =
            if (DodecaView.rotateShapes.value) { x, y -> trueRotated(x, y) } else { x, y -> PointF(x, y) }
        val top by lazy { rotated(x, y - halfWidth) }
        val bottom by lazy { rotated(x, y + halfWidth) }
        val left by lazy { rotated(x - halfWidth, y) }
        val right by lazy { rotated(x + halfWidth, y) }
        when(this) {
            CIRCLE -> canvas.drawCircle(x, y, halfWidth, paint)
            POINTED_CIRCLE -> {
                canvas.drawCircle(x, y, halfWidth, paint)
                canvas.drawPoint(pointX, pointY, pointPaint)
            }
            SQUARE -> canvas.drawRect( // how to rotate rect?
                x - halfWidth, y - halfWidth,
                x + halfWidth, y + halfWidth,
                paint
            )
            CROSS -> canvas.drawLines(
                floatArrayOf(
                    top.x, top.y, bottom.x, bottom.y,
                    left.x, left.y, right.x, right.y
                ), paint
            )
            VERTICAL_BAR -> canvas.drawLine(top.x, top.y, bottom.x, bottom.y, paint)
            HORIZONTAL_BAR -> canvas.drawLine(left.x, left.y, right.x, right.y, paint)
        }
        if (drawOutline)
            when(this) {
                CIRCLE, POINTED_CIRCLE -> canvas.drawCircle(x, y, halfWidth, outlinePaint)
                SQUARE -> canvas.drawRect( // how to rotate rect?
                    x - halfWidth, y - halfWidth,
                    x + halfWidth, y + halfWidth,
                    outlinePaint
                )
            }
        if (DodecaView.showCenters.value)
            canvas.drawPoint(x, y, pointPaint)
    }

    companion object {
        private val pointPaint = Paint(DodecaView.defaultPaint).apply {
            color = Color.MAGENTA
            strokeWidth = 3.0f
        }
        val outlinePaint = Paint(DodecaView.defaultPaint).apply {
            style = Paint.Style.STROKE
            color = Color.BLACK
        }
    }
}

internal inline fun upon(prop: KMutableProperty0<Boolean>, action: () -> Unit) {
    if (prop.get()) {
        prop.set(false)
        action()
    }
}

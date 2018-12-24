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
import org.apache.commons.math3.complex.Complex
import java.io.File
import kotlin.concurrent.fixedRateTimer
import kotlin.reflect.KMutableProperty0

// TODO: enlarge traceBitmap (as much as possible)
// BUG: when redrawTraceOnMove, scale and translate -- some shifts occur
// even wrong radius, watch closely when continuous scroll/scale too
// maybe because of center-scaling?
class DodecaView(context: Context, attributeSet: AttributeSet? = null) : View(context, attributeSet) {
    var ddu: DDU = DDU(circles = emptyList()) // dummy, actual from init(), because I cannot use lateinit here
        set(value) {
            field = value
            circles = value.circles.toMutableList()
            dx = defaultDx
            dy = defaultDy
            scale = defaultScale
            trace = value.trace ?: defaultTrace
            redrawTrace = trace
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
    var trace: Boolean = defaultTrace
    set(value) {
        field = value
        if (width > 0) // we know sizes
            retrace()
    }

    private var redrawTrace: Boolean = defaultTrace // once, draw background
    var updating = defaultUpdating
    private var lastUpdateTime: Long = 0L
    private var updateOnce = false
    private val paint = Paint(defaultPaint)
    private val tracePaint = Paint(defaultPaint)
    private lateinit var traceBitmap: Bitmap
    private lateinit var traceCanvas: Canvas
    private val traceMatrix = Matrix()
    private var traceDx: Float = 0f // `traceBitmap` top-left corner - screen top-left corner
    private var traceDy: Float = 0f // now don't work, set traceBitmapFactor to 2 and see
    private var nUpdates: Long = 0L

    private var last20NUpdates: Long = 0L
    private var last20UpdateTime: Long = 0L

    var dx: Float = defaultDx // not scaled
    private var ddx: Float = 0f
    var dy: Float = defaultDy // not scaled
    private var ddy: Float = 0f
    var scale: Float = defaultScale
    private var dscale: Float = 1f

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
            if (!::traceCanvas.isInitialized && width > 0)
                retrace()
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
            dx = getFloat("dx", dx)
            dy = getFloat("dy", dy)
            scale = getFloat("scale", scale)
            trace = getBoolean("trace", trace)
            updating = getBoolean("updating", updating)
        }
    }

    private fun saveMinorSharedPreferences() {
        editing {
            putFloat("dx", dx)
            putFloat("dy", dy)
            putFloat("scale", scale)
            putBoolean("trace", trace)
            putBoolean("updating", updating)
        }
    }

    private fun clearMinorSharedPreferences() {
        editing {
            setOf("dx", "dy", "scale", "trace", "updating").forEach { remove(it) }
        }
    }

    private fun autocenter() {
        val center = Complex(centerX.toDouble(), centerY.toDouble())
        val (dx, dy) = center - mean(circles.filter(CircleFigure::show).map(::visibleCenter))
        updateScroll(dx.toFloat(), dy.toFloat())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (trace)
            retrace()
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
                trace -> drawTraceCanvas(it)
                else -> {
                    // not bitmap for better scrolling & scaling
                    drawBackground(canvas)
                    drawCircles(canvas)
                }
            }
        }
    }

    private fun updateCanvas(canvas: Canvas) {
        if (updating && System.currentTimeMillis() - lastUpdateTime >= updateDt) {
            updateCircles()
            lastUpdateTime = System.currentTimeMillis()
        }
        if (trace) {
            upon(::redrawTrace) { drawBackground(traceCanvas) }
            drawCircles(traceCanvas)
            drawTraceCanvas(canvas)
        } else {
            drawBackground(canvas)
            drawCircles(canvas)
        }
    }

    fun updateScroll(ddx: Float, ddy: Float) {
        this.ddx = ddx / scale
        this.ddy = ddy / scale
        traceCanvas.translate(-this.ddx, -this.ddy)
        dx += this.ddx
        dy += this.ddy
        updatingTrace { traceMatrix.postTranslate(ddx, ddy) }
        this.ddx = 0f
        this.ddy = 0f
    }

    fun updateScale(dscale: Float, focusX: Float? = null, focusY: Float? = null) {
        this.dscale = scale
        traceCanvas.scale(1 / dscale, 1 / dscale, centerX, centerY)
        scale *= dscale
        updatingTrace { traceMatrix.postScale(dscale, dscale, centerX, centerY) }
        this.dscale = 0f
        val ddx = if (focusX ?: 0f == 0f) 0f else (1 - dscale) * (focusX!! - centerX)
        val ddy = if (focusY ?: 0f == 0f) 0f else (1 - dscale) * (focusY!! - centerY)
        updateScroll(ddx, ddy)
    }

    private fun updatingTrace(action: () -> Unit) {
        if (trace) {
            if (updating && redrawTraceOnMove.value || !updating && shouldRedrawTraceOnMoveWhenPaused)
                retrace()
            else {
                action()
                invalidate()
            }
        } else if (!updating)
            invalidate()
    }

    /* when trace turns on or sizes change */
    fun retrace() {
        // TODO: test when traceBitmapFactor != 1
        traceBitmap = Bitmap.createBitmap(
            traceBitmapFactor * width, traceBitmapFactor * height,
            Bitmap.Config.ARGB_8888)
        traceDx = (1f - traceBitmapFactor) * width / 2
        traceDx = (1f - traceBitmapFactor) * height / 2
        traceCanvas = Canvas(traceBitmap)
        traceMatrix.reset()
        traceMatrix.preTranslate(traceDx, traceDy)
        redrawTrace = trace
        if (!updating) {
            drawBackground(traceCanvas)
            drawCircles(traceCanvas)
        }
        invalidate()
    }

    private fun drawTraceCanvas(canvas: Canvas) {
        drawBackground(canvas)
        canvas.drawBitmap(traceBitmap, traceMatrix, tracePaint)
    }

    private fun drawBackground(canvas: Canvas) = canvas.drawColor(ddu.backgroundColor)

    private fun drawCircles(canvas: Canvas) {
        circles.filter { it.show || showAllCircles.value }.forEach { drawCircle(canvas, it) }
    }

    /* if `shape` != CIRCLE draw `shape` instead of circle */
    private fun drawCircle(canvas: Canvas, circle: CircleFigure) {
        val (c, r) = circle
        paint.color = circle.borderColor
        paint.style = if (circle.fill) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
        val (x, y) = visibleComplex(c)
        val halfWidth = visibleR(r.toFloat())
        val (pointX, pointY) = visibleComplex(c + r * circle.point)
        if (showOutline.value)
            shape.value.draw(canvas, x, y, halfWidth, pointX, pointY, circle.point, outlinePaint)
        shape.value.draw(canvas, x, y, halfWidth, pointX, pointY, circle.point, paint)
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
            translateAndScale(
                dx.toDouble(), dy.toDouble(),
                scale.toDouble(),
                Complex(centerX.toDouble(), centerY.toDouble())
            )
            trace = this@DodecaView.trace
        }
    }

    private inline fun visibleX(x: Float): Float = scale * (x + dx - centerX) + centerX
    private inline fun visibleY(y: Float): Float = scale * (y + dy - centerY) + centerY
    private fun visibleComplex(z: Complex): Pair<Float, Float> = Pair(visibleX(z.real.toFloat()), visibleY(z.imaginary.toFloat()))
    private inline fun visibleCenter(circle: Circle): Complex = Complex(
        visibleX(circle.x.toFloat()).toDouble(),
        visibleY(circle.y.toFloat()).toDouble())
    private inline fun visibleR(r: Float): Float = scale * r

    private fun editing(block: SharedPreferences.Editor.() -> Unit) {
        with (sharedPreferences.edit()) {
            this.block()
            apply()
        }
    }

    open class Option<T>(val key: String, val default: T) where T: Any {
        var value: T = default
        init {
            // WARNING: assign Option<T> even for inherited classes
            // effectively forgetting overwritten methods, etc.
            options[key] = this
        }
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
        companion object {
            val options: MutableMap<String, Option<*>> = mutableMapOf()
        }
    }

    companion object {
        private const val traceBitmapFactor = 1 // traceBitmap == traceBitmapFactor ^ 2 * screens
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
        private val outlinePaint = Paint(defaultPaint).apply { style = Paint.Style.STROKE; color = Color.BLACK }
        private var showOutline = Option("show_outline", false)
        private var reverseMotion = Option("reverse_motion", false)
        private var shape = object : Option<Shapes>("shape", Shapes.CIRCLE) {
            override fun fetchPreference(sharedPreferences: SharedPreferences): Shapes =
                sharedPreferences.getString(key, default.toString()) ?.toUpperCase()?.let { Shapes.valueOf(it) } ?: default
        }
        var rotateShapes = Option("rotate_shapes", false)
        var autocenterAlways = Option("autocenter_always", true)
        var autocenterOnce = false
        // maybe: add load random
        var preferRecentDDU = Option("prefer_recent_ddu", true) // TODO: add to preferences
        const val defaultTrace = true
        const val defaultUpdating = true
        const val defaultDx = 0f
        const val defaultDy = 0f
        const val defaultScale = 1f
        private const val TAG = "DodecaView"
    }
}

enum class Shapes {
    CIRCLE, POINTED_CIRCLE, SQUARE, CROSS, VERTICAL_BAR, HORIZONTAL_BAR;
    private val pointPaint = Paint(DodecaView.defaultPaint).apply {
        color = Color.MAGENTA
        strokeWidth = 3.0f
    }
    fun draw(canvas: Canvas, x: Float, y: Float, halfWidth: Float, pointX: Float, pointY: Float, point: Complex, paint: Paint) {
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
        if (DodecaView.showCenters.value)
            canvas.drawPoint(x, y, pointPaint)
    }
}

internal inline fun upon(prop: KMutableProperty0<Boolean>, action: () -> Unit) {
    if (prop.get()) {
        prop.set(false)
        action()
    }
}
//
//fun <T> f(a: T): T = when (a) {
//    is Boolean -> true
//    is Int -> 0
//    else -> a
//}

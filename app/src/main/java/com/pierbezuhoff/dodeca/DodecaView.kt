package com.pierbezuhoff.dodeca

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.preference.PreferenceManager
import android.util.AttributeSet
import android.util.Log
import android.view.TextureView
import android.view.View
import android.widget.TextView
import androidx.core.graphics.withMatrix
import org.apache.commons.math3.complex.Complex
import java.io.File
import java.util.Timer
import java.util.TimerTask
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
            if (autocenterAlways && width > 0) // we know sizes
                autocenter()
            invalidate()
            nUpdates = 0
        }
    private lateinit var circles: MutableList<CircleFigure>
    val sharedPreferences: SharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(context) }
    var trace: Boolean = defaultTrace
    set(value) {
        field = value
        if (width > 0) // we know sizes
            retrace()
    }

    private var redrawTrace: Boolean = defaultTrace // once, draw background
    var updating = defaultUpdating
    private val timer = Timer()
    private val timerTask = object : TimerTask() {
            override fun run() {
                if (!::traceCanvas.isInitialized && width > 0)
                    retrace()
                if (updating)
                    postInvalidate()
            }
        }
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
            if (preferRecentDDU && recentDDU == null) {
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
        with(paint) {
            color = Color.BLUE
            style = Paint.Style.STROKE
        }
        timer.scheduleAtFixedRate(timerTask, 1, dt.toLong())
        saveMinorSharedPreferences()
    }

    fun loadSharedPreferences() {
        loadMajorSharedPreferences()
        loadMinorSharedPreferences()
    }

    fun loadMajorSharedPreferences() {
        var updateImmediately = false
        upon(::autocenterOnce) { autocenter() }
        with (sharedPreferences) {
            redrawTraceOnMove = getBoolean("redraw_trace", defaultRedrawTraceOnMove)
            val newShowAllCircles = getBoolean("show_all_circles", defaultShowAllCircles)
            if (newShowAllCircles != showAllCircles) {
                updateImmediately = true
                showAllCircles = newShowAllCircles
            }
            reverseMotion = getBoolean("reverse_motion", defaultReverseMotion)
//            UPS = getInt("ups", defaultUPS)
//            getString("ups", defaultUPS.toString())?.toIntOrNull()?.let {
//                UPS = it
//            }
            // NOTE: restart/rotate screen to update FPS
//            getString("fps", defaultFPS.toString())?.toIntOrNull()?.let {
//                FPS = it
//            }
            val autocenterAlways0 = autocenterAlways
            autocenterAlways = getBoolean("autocenter_always", defaultAutocenterAlways)
            if (autocenterAlways && !autocenterAlways0 && width > 0)
                autocenter()
            getString("shape", defaultShape.toString())?.toUpperCase()?.let {
                val newShape = Shapes.valueOf(it)
                if (newShape != shape) {
                    updateImmediately = true
                    shape = newShape
                }
            }
            if (!updating && updateImmediately)
                invalidate()
        }
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

    fun updateScale(dscale: Float) {
        this.dscale = scale
        traceCanvas.scale(1 / dscale, 1 / dscale, centerX, centerY)
        scale *= dscale
        updatingTrace { traceMatrix.postScale(dscale, dscale, centerX, centerY) }
        this.dscale = 0f
    }

    private fun updatingTrace(action: () -> Unit) {
        if (trace) {
            if (updating && redrawTraceOnMove || !updating && shouldRedrawTraceOnMoveWhenPaused)
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
        circles.filter { it.show || showAllCircles }.forEach { drawCircle(canvas, it) }
    }

    /* if `shape` != CIRCLE draw `shape` instead of circle */
    private fun drawCircle(canvas: Canvas, circle: CircleFigure) {
        val (c, r) = circle
        paint.color = circle.borderColor
        paint.style = if (circle.fill) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
        val (x, y) = visibleComplex(c)
        val halfWidth = visibleR(r.toFloat())
        val matrix = Matrix().apply {
            postTranslate(x, y)
//            postRotate(-circle.point.degrees.toFloat())
        }
        canvas.withMatrix(matrix) {
            shape.draw(this, halfWidth, paint)
        }
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
        updateOnce = true
        updating = false
        postInvalidate()
    }

    private fun updateCircles() {
        nUpdates += if (reverseMotion) -1 else 1
        nUpdatesView?.text = "%d updates".format(nUpdates)
        val oldCircles = circles.map { it.copy(newRule = null) }
        val n = circles.size
        oldCircles.forEachIndexed { i, circle ->
            circle.rule?.let { rule ->
                val theRule = if (rule.startsWith("n")) rule.drop(1) else rule
                val chars = if (reverseMotion) theRule.reversed() else theRule
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

    companion object {
        var nUpdatesView: TextView? = null
        private const val traceBitmapFactor = 1 // traceBitmap == traceBitmapFactor ^ 2 * screens
        val defaultPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private const val defaultFPS = 100
        // FIX: changing FPS and UPS does not work properly
        private var FPS = defaultFPS
        private const val defaultUPS = 100
        private var UPS = defaultUPS // updates per second
        val dt get() = 1000f / FPS
        val updateDt get() = 1000f / UPS
        private const val defaultRedrawTraceOnMove = true
        private var redrawTraceOnMove = defaultRedrawTraceOnMove
        enum class RedrawOnMoveWhenPaused { ALWAYS, NEVER, RESPECT_REDRAW_TRACE_ON_MOVE }
        private val defaultRedrawTraceOnMoveWhenPaused = RedrawOnMoveWhenPaused.RESPECT_REDRAW_TRACE_ON_MOVE
        var redrawTraceOnMoveWhenPaused = defaultRedrawTraceOnMoveWhenPaused // TODO: add to preferences
        private val shouldRedrawTraceOnMoveWhenPaused get() = when (redrawTraceOnMoveWhenPaused) {
            RedrawOnMoveWhenPaused.ALWAYS -> true
            RedrawOnMoveWhenPaused.NEVER -> false
            RedrawOnMoveWhenPaused.RESPECT_REDRAW_TRACE_ON_MOVE -> redrawTraceOnMove
        }
        private const val defaultShowAllCircles = false
        private var showAllCircles = defaultShowAllCircles
        private const val defaultReverseMotion = false
        private var reverseMotion = defaultReverseMotion
        private val defaultShape = Shapes.CIRCLE
        private var shape = defaultShape
        private const val defaultRotateShapes = true
        var rotateShapes = defaultRotateShapes // TODO: add to preferences
        private const val defaultAutocenterAlways = false
        var autocenterAlways = defaultAutocenterAlways
        var autocenterOnce = false
        private const val defaultPreferRecentDDU = true
        var preferRecentDDU = defaultPreferRecentDDU // TODO: add to preferences
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
    fun draw(canvas: Canvas, halfWidth: Float, paint: Paint) {
        when(this) {
            CIRCLE -> canvas.drawCircle(0f, 0f, halfWidth, paint)
            POINTED_CIRCLE -> {
                canvas.drawCircle(0f, 0f, halfWidth, paint)
                canvas.drawPoint(halfWidth, 0f, pointPaint)
            }
            SQUARE -> canvas.drawRect(
                -halfWidth, -halfWidth,
                halfWidth, halfWidth,
                paint)
            CROSS -> canvas.drawLines(floatArrayOf(
                0f, -halfWidth, 0f, halfWidth,
                halfWidth, 0f, -halfWidth, 0f
            ), paint)
            VERTICAL_BAR -> canvas.drawLine(
                0f, -halfWidth,
                0f, halfWidth,
                paint)
            HORIZONTAL_BAR -> canvas.drawLine(
                -halfWidth, 0f,
                halfWidth, 0f,
                paint)
        }
    }
}

internal inline fun upon(prop: KMutableProperty0<Boolean>, action: () -> Unit) {
    if (prop.get()) {
        prop.set(false)
        action()
    }
}

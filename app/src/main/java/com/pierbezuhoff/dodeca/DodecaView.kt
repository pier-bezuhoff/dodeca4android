package com.pierbezuhoff.dodeca

import android.content.Context
import android.content.res.AssetManager
import android.graphics.*
import android.preference.PreferenceManager
import android.util.AttributeSet
import android.util.Log
import android.view.View
import org.apache.commons.math3.complex.Complex
import java.io.File
import java.lang.Exception
import java.util.*

// TODO: enlarge traceBitmap (as much as possible)
// TODO: when screen rotated, restart THE SAME ddu
// BUG: when redrawTraceOnMove, scale and translate -- some shifts occur
// even wrong radius, watch closely when continuous scroll/scale too
// maybe because of center-scaling?
class DodecaView(context: Context, attributeSet: AttributeSet? = null) : View(context, attributeSet) {
    var ddu: DDU = DDU(circles = emptyList()) // dummy, actual from init()
        set(value) {
            field = value
            circles = value.circles.toMutableList()
            dx = defaultDx
            dy = defaultDy
            scale = defaultScale
            trace = defaultTrace
            redrawTrace = trace
            updating = defaultUpdating
            clearMinorSharedPreferences()
            invalidate()
        }
    private lateinit var circles: MutableList<Circle>
    private val sharedPreferences get() = PreferenceManager.getDefaultSharedPreferences(context)
    var trace: Boolean = defaultTrace
    set(value) {
        field = value
        if (width > 0) // we know sizes
            retrace()
    }

    private var redrawTrace: Boolean = defaultTrace // once, draw background
    var updating = defaultUpdating
    set(value) {
        field = value
        // don't know, if it's bad...
        // we have 'clear' button...
        if (trace && value && redrawTraceOnMove && width > 0) // we know sizes
            retrace()
    }
    private val timer = Timer()
    private val timerTask = object : TimerTask() {
            override fun run() {
                if (updating)
                    postInvalidate()
            }
        }
    private var lastUpdateTime = 0L
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val tracePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private lateinit var traceBitmap: Bitmap
    private lateinit var traceCanvas: Canvas
    private val traceMatrix: Matrix = Matrix()
    private var nUpdates: Long = 0

    var dx: Float = defaultDx // not scaled
    private var ddx: Float = 0f
    var dy: Float = defaultDy // not scaled
    private var ddy: Float = 0f
    var scale: Float = defaultScale
    private var dscale: Float = 1f

    val centerX: Float get() = x + width / 2
    val centerY: Float get() = y + height / 2

    init {
        loadSharedPreferences()
        try {
            this.ddu = DDU.readStream(context.assets.open("ddu/290305_z1_erot2.ddu"))
        } catch (e: Exception) {
            e.printStackTrace()
            this.circles = mutableListOf() // dummy, actual from ddu.set
            this.ddu = run {
                val circle = Circle(Complex(300.0, 400.0), 200.0, Color.BLUE, rule = "12")
                val circle1 = Circle(Complex(450.0, 850.0), 300.0, Color.LTGRAY)
                val circle2 = Circle(Complex(460.0, 850.0), 300.0, Color.DKGRAY)
                val circle0 = Circle(Complex(0.0, 0.0), 100.0, Color.GREEN)
                val circles = listOf(
                    circle,
                    circle1,
                    circle2,
                    circle0,
                    circle0.invert(circle),
                    circle1.invert(circle),
                    Circle(Complex(600.0, 900.0), 10.0, Color.RED, fill = true)
                )
                DDU(Color.WHITE, circles)
            }
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
        with (sharedPreferences) {
            redrawTraceOnMove = getBoolean("redraw_trace", defaultRedrawTraceOnMove)
            val newShowAllCircles = getBoolean("show_all_circles", defaultShowAllCircles)
            if (newShowAllCircles != showAllCircles) {
                updateImmediately = true
                showAllCircles = newShowAllCircles
            }
            reverseMotion = getBoolean("reverse_motion", defaultReverseMotion)
            UPS = getInt("ups", defaultUPS)
//            getString("ups", defaultUPS.toString())?.toIntOrNull()?.let {
//                UPS = it
//            }
            // NOTE: restart/rotate screen to update FPS
            getString("fps", defaultFPS.toString())?.toIntOrNull()?.let {
                FPS = it
                Log.i(TAG, "FPS: $FPS")
            }
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
        with (sharedPreferences.edit()) {
            putFloat("dx", dx)
            putFloat("dy", dy)
            putFloat("scale", scale)
            putBoolean("trace", trace)
            putBoolean("updating", updating)
            commit()
        }
    }

    private fun clearMinorSharedPreferences() {
        with (sharedPreferences.edit()) {
            setOf("dx", "dy", "scale", "trace", "updating").forEach { key ->
                remove(key)
            }
            commit()
        }
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
            nUpdates++
            lastUpdateTime = System.currentTimeMillis()
        }
        if (trace) {
            if (redrawTrace) {
                drawBackground(traceCanvas)
            }
            drawCircles(traceCanvas)
            drawTraceCanvas(canvas)
            redrawTrace = false
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
        if (trace) {
            if (updating && redrawTraceOnMove)
                retrace()
            else {
                traceMatrix.postTranslate(ddx, ddy)
                invalidate()
            }
        } else if (!updating)
            invalidate()
        this.ddx = 0f
        this.ddy = 0f
    }

    fun updateScale(dscale: Float) {
        this.dscale = scale
        traceCanvas.scale(1 / dscale, 1 / dscale, centerX, centerY)
        scale *= dscale
        if (trace) {
            if (updating && redrawTraceOnMove)
                retrace()
            else {
                traceMatrix.postScale(dscale, dscale, centerX, centerY)
                invalidate()
            }
        } else if (!updating)
            invalidate()
        this.dscale = 0f
    }

    /* when trace turns on or sizes change */
    fun retrace() {
        traceBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        traceCanvas = Canvas(traceBitmap)
        traceMatrix.reset()
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

    private fun drawBackground(canvas: Canvas) {
        canvas.drawColor(ddu.backgroundColor)
    }

    private fun drawCircles(canvas: Canvas) {
        for (circle in circles)
            if (circle.show || showAllCircles)
                drawCircle(canvas, circle)
    }

    /* if `shape` != CIRCLE draw `shape` instead of circle */
    private fun drawCircle(canvas: Canvas, circle: Circle) {
        val (c, r) = circle
        paint.color = circle.borderColor
        paint.style = if (circle.fill) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
        val x = visibleX(c.real.toFloat())
        val y = visibleY(c.imaginary.toFloat())
        val halfWidth = visibleR(r.toFloat())
        when (shape) {
            Shapes.CIRCLE ->
                canvas.drawCircle(x, y, halfWidth, paint)
            Shapes.SQUARE ->
                canvas.drawRect(
                    x - halfWidth, y - halfWidth,
                    x + halfWidth, y + halfWidth,
                    paint
                )
            Shapes.CROSS ->
                canvas.drawLines(floatArrayOf(
                    x, y - halfWidth, x, y + halfWidth,
                    x + halfWidth, y, x - halfWidth, y
                ), paint)
            Shapes.VERTICAL_BAR ->
                canvas.drawLine(
                    x, y - halfWidth,
                    x, y + halfWidth,
                    paint
                )
            Shapes.HORIZONTAL_BAR ->
                canvas.drawLine(
                    x - halfWidth, y,
                    x + halfWidth, y,
                    paint
                )
        }
    }

    private fun updateCircles() {
        val oldCircles = circles.toList()
        val n = circles.size
        oldCircles.forEachIndexed { i, circle ->
            circle.rule?.let { rule ->
                val theRule = if (rule.startsWith("n")) rule.drop(1) else rule
                val chars = if (reverseMotion) theRule.reversed() else theRule
                chars.forEach { ch ->
                    val j = Integer.parseInt(ch.toString()) // NOTE: Char.toInt() is ord()
                    if (j >= n)
                        Log.e(TAG, "updateCircles: index $j >= $n out of `circles` bounds (from rule $rule for $circle)")
                    else {
                        // QUESTION: maybe should be inverted with respect to new `circles[j]`
                        // maybe it doesn't matter
                        circles[i] = circles[i].invert(oldCircles[j])
                    }
                }
            }
        }
    }

    fun save(file: File? = null) {
        // ATTENTION: ?scale relative to view center? => check!
        ddu.translateAndScale(dx.toDouble(), dy.toDouble(), scale.toDouble(), Complex(centerX.toDouble(), centerY.toDouble()))
        ddu.save(file)
    }

    private inline fun visibleX(x: Float): Float = scale * (x + dx - centerX) + centerX
    private inline fun visibleY(y: Float): Float = scale * (y + dy - centerY) + centerY
    private inline fun visibleR(r: Float): Float = scale * r

    companion object {
        private const val defaultFPS = 100
        private var FPS = defaultFPS
        private const val defaultUPS = 100
        private var UPS = defaultUPS // updates per second
        val dt get() = 1000f / FPS
        val updateDt get() = 1000f / UPS
        private const val defaultRedrawTraceOnMove = true
        private var redrawTraceOnMove = defaultRedrawTraceOnMove
        private const val defaultShowAllCircles = false
        private var showAllCircles = defaultShowAllCircles
        private const val defaultReverseMotion = false
        private var reverseMotion = defaultReverseMotion
        private val defaultShape = Shapes.CIRCLE
        private var shape = defaultShape
        const val defaultTrace = true
        const val defaultUpdating = true
        const val defaultDx = 0f
        const val defaultDy = 0f
        const val defaultScale = 1f
        private const val TAG = "DodecaView"
    }
}

enum class Shapes {
    CIRCLE, SQUARE, CROSS, VERTICAL_BAR, HORIZONTAL_BAR
}
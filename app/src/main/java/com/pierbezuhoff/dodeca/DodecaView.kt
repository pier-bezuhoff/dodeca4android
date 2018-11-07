package com.pierbezuhoff.dodeca

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.View
import org.apache.commons.math3.complex.Complex
import java.util.*

// TODO: proper [feature] scaling (with traceMatrix)
// TODO: usual translation and scaling when stopped and not trace
// TODO: enlarge traceBitmap (as much as possible)
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
            invalidate()
        }
    private var circles: MutableList<Circle>
    var trace: Boolean = defaultTrace
    set(value) {
        field = value
        if (trace && width > 0) // we know sizes
            retrace()
    }

    private var redrawTrace: Boolean = defaultTrace // once, draw background
    var updating = defaultUpdating
    set(value) {
        field = value
        if (trace && value && width > 0) // we know sizes
            retrace()
    }
    var translating = false
    var scaling = false
    private var lastUpdateTime = 0L
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val tracePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private lateinit var traceBitmap: Bitmap
    private lateinit var traceCanvas: Canvas
    private val traceMatrix: Matrix = Matrix()
    private var nUpdates: Long = 0

    var dx: Float = defaultDx
    private var ddx: Float = 0f
    var dy: Float = defaultDy
    private var ddy: Float = 0f
    var scale: Float = defaultScale
    private var dscale: Float = 1f

    val centerX: Float get() = x + width / 2
    val centerY: Float get() = y + height / 2

    init {
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
        with(paint) {
            color = Color.BLUE
            style = Paint.Style.STROKE
        }
        val timer = Timer()
        timer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                if (updating)
                    postInvalidate()
            }
        }, 1, dt.toLong())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (trace)
            retrace()
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        canvas?.let {
            if (translating)
                translateCanvas(it)
            if (scaling)
                scaleCanvas(it)
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

    private fun translateCanvas(canvas: Canvas) {
        translating = false
        ddx = 0f
        ddy = 0f
    }

    private fun scaleCanvas(canvas: Canvas) {
        scaling = false
        dscale = 1f
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
            // without rule => draw only once
            drawCircles(traceCanvas, !redrawTrace)
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
        translating = true
        if (trace)
            if (updating)
                retrace()
            else {
                traceMatrix.postTranslate(ddx, ddy)
                invalidate()
            }
        else if (!updating)
            invalidate()
    }

    fun updateScale(dscale: Float) {
        this.dscale = scale
        traceCanvas.scale(1 / dscale, 1 / dscale, centerX, centerY)
        scale *= dscale
        scaling = true
        if (trace)
            if (updating)
                retrace()
            else {
                traceMatrix.postScale(dscale, dscale, centerX, centerY)
                invalidate()
            }
        else if (!updating)
            invalidate()
    }

    /* when trace turns on or sizes change */
    private fun retrace() {
        Log.i(TAG, "retrace")
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

    private fun drawCircles(canvas: Canvas, onlyWithRules: Boolean = false) {
        if (onlyWithRules) {
            for (circle in circles)
                if (circle.rule != null && circle.rule!!.isNotBlank() && !circle.rule!!.startsWith("n"))
                    drawCircle(canvas, circle)
        }
        else
            for (circle in circles)
                if (circle.rule != null && !circle.rule!!.startsWith("n"))
                    drawCircle(canvas, circle)
    }

    private fun drawCircle(canvas: Canvas, circle: Circle) {
        val (c, r) = circle
        paint.color = circle.borderColor
        paint.style = if (circle.fill) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
        canvas.drawCircle(
            visibleX(c.real.toFloat()),
            visibleY(c.imaginary.toFloat()),
            visibleR(r.toFloat()),
            paint)
    }

    private fun updateCircles() {
        val oldCircles = circles.toList()
        val n = circles.size
        oldCircles.forEachIndexed { i, circle ->
            circle.rule?.let { rule ->
                val theRule = if (rule.startsWith("n")) rule.drop(1) else rule
                val chars = if (true) theRule else theRule.reversed()
                chars.forEach { ch -> // drop first 'n' letter
                    val j = Integer.parseInt(ch.toString()) // NOTE: Char.toInt() is ord()
                    if (j >= n)
                        Log.e(TAG, "updateCircles: index $j >= $n out of `circles` bounds (from rule $rule for $circle)")
                    else {
                        // Q: maybe should be inverted with respect to new `circles[j]`
                        // maybe it doesn't matter
                        circles[i] = circles[i].invert(oldCircles[j])
                    }
                }
            }
        }
    }

    private inline fun visibleX(x: Float): Float = scale * (x + dx - centerX) + centerX
    private inline fun visibleY(y: Float): Float = scale * (y + dy - centerY) + centerY
    private inline fun visibleR(r: Float): Float = scale * r

    companion object {
        private const val FPS = 300
        private const val UPS = 100 // updates per second
        const val dt = 1000f / FPS
        const val updateDt = 1000f / UPS
        const val defaultTrace = true
        const val defaultUpdating = true
        const val defaultDx = 0f
        const val defaultDy = 0f
        const val defaultScale = 1f
        private const val TAG = "DodecaView"
    }
}
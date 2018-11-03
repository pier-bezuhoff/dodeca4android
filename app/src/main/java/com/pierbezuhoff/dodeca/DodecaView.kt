package com.pierbezuhoff.dodeca

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.View
import org.apache.commons.math3.complex.Complex
import java.util.*

class DodecaView(context: Context, attributeSet: AttributeSet) : View(context, attributeSet) {
    var ddu: DDU = DDU(circles = emptyList()) // dummy, actual from init()
        set(value) {
            field = value
            circles = value.circles.toMutableList()
            dx = defaultDx
            dy = defaultDy
            scale = defaultScale
            trace = defaultTrace
            redraw = true
            invalidate()
        }
    var circles: MutableList<Circle>
    var trace = defaultTrace

    var redraw = false // once, total, with background
    var updating = true
    var translating = false
    var scaling = false
    private var lastDrawTime = 0L
    private var lastUpdateTime = 0L
    private val paint = Paint(Paint.HINTING_ON)

    var dx: Float = defaultDx
    var ddx: Float = 0f
    var dy: Float = defaultDy
    var ddy: Float = 0f
    var scale: Float = defaultScale
    var dscale: Float = 1f

    val centerX: Float get() = x + width / 2
    val centerY: Float get() = y + height / 2

    var t = 0f

    init {
        this.circles = mutableListOf() // dummy, actual from ddu.set
        this.ddu = run {
            val circle = Circle(Complex(300.0, 400.0), 200.0)
            val circle0 = Circle(Complex(0.0, 0.0), 100.0, Color.GREEN)
            val circle1 = Circle(Complex(450.0, 850.0), 300.0, fill = true)
            val circles = listOf(
                circle,
                circle0,
                circle1,
                circle0.invert(circle),
                circle1.invert(circle)
            )
            DDU(circles = circles)
        }
        with(paint) {
            color = Color.BLUE
            style = Paint.Style.STROKE
        }
        setWillNotDraw(false)
        val timer = Timer()
        timer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                if (updating)
                    postInvalidate()
            }
        }, 0, dt.toLong())
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
//        Log.i("I", "translating: $translating, redraw: $redraw, updating: $updating, trace: $trace, lastUpdateTime: $lastUpdateTime")
        canvas?.let {
            canvas.save()
            if (translating)
                translateCanvas(it)
            if (scaling)
                scaleCanvas(it)
            if (redraw)
                redrawCanvas(it)
            else if (updating)
                updateCanvas(it)
            canvas.restore()
        }
    }

    private fun translateCanvas(canvas: Canvas) {
        canvas.translate(ddx, ddy)
        translating = false
        ddx = 0f
        ddy = 0f
    }

    private fun scaleCanvas(canvas: Canvas) {
        canvas.scale(dscale, dscale, centerX, centerY)
        scaling = false
        dscale = 1f

    }

    private fun redrawCanvas(canvas: Canvas) {
        drawBackground(canvas)
        drawCircles(canvas)
        redraw = false
        lastDrawTime = System.currentTimeMillis()
    }

    private fun updateCanvas(canvas: Canvas) {
        if (updating && System.currentTimeMillis() - lastUpdateTime >= updateDt) {
            updateCircles()
            lastUpdateTime = System.currentTimeMillis()
        }
        if (!trace)
            drawBackground(canvas)
        drawCircles(canvas)
        lastDrawTime = System.currentTimeMillis()
    }

    fun updateScroll(ddx: Float, ddy: Float) {
        this.ddx = scale * ddx
        this.ddy = scale * ddy
        dx += this.ddx
        dy += this.ddy
//        translating = true
//        invalidate()
    }

    fun updateScale(dscale: Float) {
        this.dscale = scale
        scale *= dscale
        dx *= dscale
        dy *= dscale
//        scaling = true
//        invalidate()
    }

    private fun drawBackground(canvas: Canvas) {
        canvas.drawColor(ddu.backgroundColor)
    }

    private fun drawCircles(canvas: Canvas) {
        for (circle in circles)
            drawCircle(canvas, circle)
    }

    private fun drawCircle(canvas: Canvas, circle: Circle) {
        val (c, r) = circle
        paint.color = circle.borderColor
        paint.style = if (circle.fill) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
        canvas.drawCircle(
            scale * (c.real.toFloat() + dx - centerX) + centerX,
            scale * (c.imaginary.toFloat() + dy - centerY) + centerY,
            scale * r.toFloat(),
            paint)
    }

    private fun updateCircles() {
        val oldCircles = circles.toList()
        oldCircles.forEachIndexed { i, circle ->
            circle.rule?.let { rule ->
                rule.drop(1).forEach { ch -> // drop first 'n' letter
                    val j = Integer.parseInt(ch.toString()) // NOTE: Char.toInt() is ord()
                    if (j >= circles.size)
                        Log.e("DodecaView", "updateCircles: index $j >= ${oldCircles.size} out of `circles` bounds (from rule $rule for $circle)")
                    else {
                        circles[i] = circle.invert(circles[j])
                    }
                }
            }
        }
    }

    companion object {
        private const val FPS = 300
        private const val UPS = 10 // updates per second
        const val dt = 1000f / FPS
        const val updateDt = 1000f / UPS
        const val defaultTrace = false
        const val defaultDx = 0f
        const val defaultDy = 0f
        const val defaultScale = 1f
    }
}
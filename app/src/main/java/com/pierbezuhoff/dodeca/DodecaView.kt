package com.pierbezuhoff.dodeca

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.Toast
import org.apache.commons.math3.complex.Complex
import java.util.*
import org.jetbrains.anko.toast

class DodecaView(context: Context, attributeSet: AttributeSet? = null) : View(context, attributeSet) {
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
    private var circles: MutableList<Circle>
    var trace = defaultTrace

    var redraw = false // once, total, with background
    var updating = true
    var translating = false
    var scaling = false
    private var lastDrawTime = 0L
    private var lastUpdateTime = 0L
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    var dx: Float = defaultDx
    private var ddx: Float = 0f
    var dy: Float = defaultDy
    private var ddy: Float = 0f
    var scale: Float = defaultScale
    private var dscale: Float = 1f

    val centerX: Float get() = x + width / 2
    val centerY: Float get() = y + height / 2

    var t = 0 // tmp

    init {
        this.circles = mutableListOf() // dummy, actual from ddu.set
        this.ddu = run {
            val circle = Circle(Complex(300.0, 400.0), 200.0, Color.BLUE, rule = "n12")
            val circle0 = Circle(Complex(0.0, 0.0), 100.0, Color.GREEN)
            val circle1 = Circle(Complex(450.0, 850.0), 300.0, Color.LTGRAY)
            val circle2 = Circle(Complex(460.0, 850.0), 300.0, Color.DKGRAY)
            val circles = listOf(
                circle,
                circle1,
                circle2,
                circle0,
                circle0.invert(circle),
                circle1.invert(circle),
                Circle(Complex(600.0, 900.0), 10.0, Color.RED, fill = true)
            )
            DDU(Color.YELLOW, circles)
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

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        canvas?.let {
            if (translating)
                translateCanvas(it)
            if (scaling)
                scaleCanvas(it)
            if (redraw)
                redrawCanvas(it)
            else if (updating)
                updateCanvas(it)
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
        this.ddx = ddx / scale
        this.ddy = ddy / scale
        dx += this.ddx
        dy += this.ddy
//        translating = true
//        invalidate()
    }

    fun updateScale(dscale: Float) {
        this.dscale = scale
        scale *= dscale
//        scaling = true
//        invalidate()
    }

    private fun drawBackground(canvas: Canvas) {
        Log.i("draw", "bg: ${ddu.backgroundColor}")
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
        val n = circles.size
        oldCircles.forEachIndexed { i, circle ->
            circle.rule?.let { rule ->
                val theRule = if (rule.startsWith("n")) rule.drop(1) else rule
                val chars = if (true) theRule else theRule.reversed()
                chars.forEach { ch -> // drop first 'n' letter
                    val j = Integer.parseInt(ch.toString()) // NOTE: Char.toInt() is ord()
                    if (j >= n)
                        Log.e("DodecaView", "updateCircles: index $j >= $n out of `circles` bounds (from rule $rule for $circle)")
                    else {
                        // Q: maybe should be inverted with respect to new `circles[j]`
                        // maybe it doesn't matter
                        circles[i] = circles[i].invert(oldCircles[j])
                    }
                }
            }
        }
    }

    companion object {
        private const val FPS = 300
        private const val UPS = 100 // updates per second
        const val dt = 1000f / FPS
        const val updateDt = 1000f / UPS
        const val defaultTrace = true
        const val defaultDx = 0f
        const val defaultDy = 0f
        const val defaultScale = 1f
    }
}
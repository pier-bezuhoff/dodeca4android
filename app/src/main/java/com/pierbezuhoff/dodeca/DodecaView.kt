package com.pierbezuhoff.dodeca

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import org.apache.commons.math3.complex.Complex
import org.jetbrains.anko.getStackTraceString

class DodecaView(context: Context, attributes: AttributeSet) : SurfaceView(context, attributes), SurfaceHolder.Callback {
    var ddu: DDU = DDU(circles = emptyList()) // dummy, actual from init()
    set(value) {
        field = value
        circles = value.circles.toMutableList()
        dx = defaultDx
        dy = defaultDy
        scale = defaultScale
        trace = defaultTrace
    }
    var circles: MutableList<Circle>
    var paint: Paint
    lateinit var thread: DodecaThread
    val centerX: Float get() = x + width / 2
    val centerY: Float get() = y + height / 2

    var trace = defaultTrace
    var dx: Float = defaultDx
    var dy: Float = defaultDy
    var ddx: Float = 0f
    var ddy: Float = 0f
    var scale: Float = defaultScale
    var dscale: Float = 1f

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
        this.paint = Paint(Paint.HINTING_ON)
        paint.setARGB(255, 0, 255, 255)
        paint.style = Paint.Style.STROKE
        holder.addCallback(this)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
//        update()
    }

    override fun surfaceCreated(p0: SurfaceHolder?) {
        thread = DodecaThread(this)
        thread.start()
        update()
    }

    override fun surfaceChanged(p0: SurfaceHolder?, p1: Int, p2: Int, p3: Int) {
         update()
    }

    override fun surfaceDestroyed(p0: SurfaceHolder?) {
        var retry = true
        while (retry) {
            try {
                thread.running = false
                thread.join()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            retry = false
        }
    }

    /* don't redraw, just translate bitmap
     * change dx, dy, scale */
    fun updateScroll() {
        thread.translate = true
        dx += ddx
        dy += ddy
        scale *= dscale
    }

    fun update() {
        thread.redraw = true
    }

    fun drawBackground(canvas: Canvas) {
        canvas.drawColor(ddu.backgroundColor)
    }

    fun drawCircles(canvas: Canvas) {
        for (circle in circles)
            drawCircle(canvas, circle)
    }

    fun drawCircle(canvas: Canvas, circle: Circle) {
        val (c, r) = circle
        paint.color = circle.borderColor
        paint.style = if (circle.fill) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
        canvas.drawCircle(
            dx + c.real.toFloat(),
            dy + c.imaginary.toFloat(),
            r.toFloat(),
            paint)
    }

    inline fun withCanvas(action: (Canvas) -> Unit) {
        if (holder.surface.isValid) {
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                canvas?.let {
                    synchronized(holder) {
                        action(canvas)
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "DodecaView > withCanvas > holder.lockCanvas:\n${e.getStackTraceString()}")
                e.printStackTrace()
            } finally {
                canvas?.let {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (e: Exception) {
                        Log.e("MainActivity", "DodecaView > withCanvas > holder.unlockCanvasAndPost:\n${e.getStackTraceString()}")
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    companion object {
        const val defaultTrace = false
        const val defaultDx = 0f
        const val defaultDy = 0f
        const val defaultScale = 1f
    }
}
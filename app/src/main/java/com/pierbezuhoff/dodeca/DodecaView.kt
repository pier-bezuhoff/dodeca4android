package com.pierbezuhoff.dodeca

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import org.apache.commons.math3.complex.Complex

class DodecaView(context: Context, attributes: AttributeSet) : SurfaceView(context, attributes), SurfaceHolder.Callback {
    val circles: Set<Circle>
    init {
        val circle = Circle(Complex(300.0, 400.0), 200.0)
        val circle0 = Circle(Complex(0.0, 0.0), 100.0)
        val circle1 = Circle(Complex(450.0, 850.0), 300.0)
        circles = setOf(
            circle,
            circle0,
            circle1,
            circle.invert(circle0),
            circle.invert(circle1)
        )
    }
    var paint: Paint = Paint(Paint.HINTING_ON or Paint.ANTI_ALIAS_FLAG)
    init {
        paint.setARGB(255, 0, 255, 255)
        paint.style = Paint.Style.STROKE
    }
    private val thread: DodecaThread
    init {
        holder.addCallback(this)
        thread = DodecaThread(holder, this)
    }

    override fun surfaceCreated(p0: SurfaceHolder?) {
        thread.running = true
        thread.start()
    }

    override fun surfaceChanged(p0: SurfaceHolder?, p1: Int, p2: Int, p3: Int) {
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


    /**
     * Function to update the positions of player and game objects
     */
    fun update() {

    }

    /**
     * Everything that has to be drawn on Canvas
     */
    override fun draw(canvas: Canvas) {
        super.draw(canvas)
    }

    fun drawCircle(canvas: Canvas, circle: Circle) {
        val (c, r) = circle
        canvas.drawPoint(c.real.toFloat(), c.imaginary.toFloat(), paint)
        canvas.drawCircle(c.real.toFloat(), c.imaginary.toFloat(), r.toFloat(), paint)
    }
}
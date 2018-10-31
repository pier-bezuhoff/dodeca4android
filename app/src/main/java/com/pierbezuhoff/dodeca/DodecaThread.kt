package com.pierbezuhoff.dodeca

import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import android.view.SurfaceHolder
import org.apache.commons.math3.complex.Complex

class DodecaThread(private val dodecaView: DodecaView, private val holder: SurfaceHolder) : Thread() {
    var running: Boolean = false
    var redraw = false
    var ddx = 0f
    var ddy = 0f
    var translate = false
    private val targetFPS = 30

    override fun run() {
        var startTime: Long
        var timeMillis: Long
        var waitTime: Long
        val targetTime = (1000 / targetFPS).toLong()
        val circles = dodecaView.circles.toList()
        running = true
        while (running) {
            startTime = System.currentTimeMillis()
            if (true || translate && !redraw) {
//                Log.i("translate", "${dodecaView.ddx} ${dodecaView.ddy}")
                dodecaView.translateCanvas()
                translate = false // auto
            }
            if (redraw) {
//                Log.i("redraw", "trace: ${dodecaView.trace}+")
                dodecaView.withCanvas { canvas ->
                    if (!dodecaView.trace)
                        dodecaView.drawBackground(canvas)
                    dodecaView.drawCircles(canvas)
                }
                circles.forEachIndexed { i, circle ->
                    circle.rule?.let { rule ->
                        rule.drop(1).forEach { ch -> // drop first 'n' letter
                            val j = Integer.parseInt(ch.toString()) // NOTE: Char.toInt() is ord()
                            if (j >= circles.size)
                                Log.e("MainActivity", "index $j out of `circle` bounds (${circles.size}")
                            else {
                                dodecaView.circles[i] = dodecaView.circles[i].invert(dodecaView.circles[j])
                                // Log.i("MainActivity", "$i: $oldCircle -> ${dodecaView.circles[i]}")
                            }
                        }
                    }
                }
            }
            timeMillis = System.currentTimeMillis() - startTime
            waitTime = targetTime - timeMillis

            try {
                sleep(Math.max(0, waitTime))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

}
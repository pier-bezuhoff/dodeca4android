package com.pierbezuhoff.dodeca

import android.graphics.Color
import android.util.Log
import org.apache.commons.math3.complex.Complex

class DodecaThread(private val dodecaView: DodecaView) : Thread() {
    var running: Boolean = false
    var redraw = false
    var ddx = 0f
    var ddy = 0f
    var translate = false
    private val targetFPS = 300

    override fun run() {
        var startTime: Long
        var timeMillis: Long
        var waitTime: Long
        val targetTime = (1000 / targetFPS).toLong()
        val circles = dodecaView.circles.toList()
        var t = 0.0
        running = true
        while (running) {
            startTime = System.nanoTime()
            if (redraw || translate) {
                dodecaView.withCanvas { canvas ->
                    Log.i("MainActivity", "redraw: $redraw, translate: $translate")
                    if (redraw || translate) {
                        if (!dodecaView.trace)
                            dodecaView.drawBackground(canvas)
                        dodecaView.drawCircles(canvas)
                        translate = false
                    }
                    /*
                    if (translate) {
                        // don't work
                        canvas.translate(dodecaView.ddx, dodecaView.ddy)
                        translate = false
                    }
                    */
                }
                t += 0.1
                if (redraw) {
                    circles.forEachIndexed { i, circle ->
                        dodecaView.circles[i].center = Complex(circle.x + 10 * Math.cos(t), circle.y + 10 * Math.sin(t))
                        /*
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
                        */
                    }
                }
            }
            timeMillis = (System.nanoTime() - startTime) / 1_000_000
            waitTime = targetTime - timeMillis

            try {
                sleep(Math.max(0, waitTime))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

}
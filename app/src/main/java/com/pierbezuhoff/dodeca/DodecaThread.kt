package com.pierbezuhoff.dodeca

import android.graphics.Color
import android.util.Log

class DodecaThread(private val dodecaView: DodecaView) : Thread() {
    var running: Boolean = false
    var redraw = false
    var ddx = 0f
    var ddy = 0f
    var translate = false
    private val targetFPS = 3000

    override fun run() {
        var startTime: Long
        var timeMillis: Long
        var waitTime: Long
        val targetTime = (1000 / targetFPS).toLong()
        var n = 0 // hmm...
        val circles = dodecaView.circles.toList()
        while (running) {
            startTime = System.nanoTime()
            if (redraw || n < 10 || translate) {
                dodecaView.withCanvas { canvas ->
                    if (translate) {
                        // useless
                        canvas.translate(dodecaView.ddx, dodecaView.ddy)
                        translate = false
                    }
                    if (redraw || n++ < 10) {
                        if (n < 10) canvas.drawColor(Color.WHITE)
                        dodecaView.drawCircles(canvas)
                    }
                }
                if (redraw) {
                    circles.forEachIndexed() { i, circle ->
                        circle.rule?.let {
                            it.drop(1).forEach {
                                val j = Integer.parseInt(it.toString())
                                if (j >= circles.size)
                                    Log.e("MainActivity", "index $j out of `circle` bounds (${circles.size}")
                                else {
                                    val oldCircle = dodecaView.circles[i]
                                    dodecaView.circles[i] = circles[j].invert(oldCircle)
//                                    Log.i("MainActivity", "$i: $oldCircle -> ${dodecaView.circles[i]}")
                                }
                            }
                        }
                    }
                }
            }
            timeMillis = (System.nanoTime() - startTime) / 1000000
            waitTime = targetTime - timeMillis

            try {
                sleep(Math.max(0, waitTime))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

}
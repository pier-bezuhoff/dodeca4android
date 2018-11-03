package com.pierbezuhoff.dodeca

import android.util.Log
import android.view.SurfaceHolder

class DodecaThread(private val dodecaSurfaceView: DodecaSurfaceView, private val holder: SurfaceHolder) : Thread() {
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
        val circles = dodecaSurfaceView.circles.toList()
        running = true
        while (running) {
            startTime = System.currentTimeMillis()
            if (true || translate && !redraw) {
//                Log.i("translate", "${dodecaSurfaceView.ddx} ${dodecaSurfaceView.ddy}")
                dodecaSurfaceView.translateCanvas()
                translate = false // auto
            }
            if (redraw) {
//                Log.i("redraw", "trace: ${dodecaSurfaceView.trace}+")
                dodecaSurfaceView.withCanvas { canvas ->
                    if (!dodecaSurfaceView.trace)
                        dodecaSurfaceView.drawBackground(canvas)
                    dodecaSurfaceView.drawCircles(canvas)
                }
                circles.forEachIndexed { i, circle ->
                    circle.rule?.let { rule ->
                        rule.drop(1).forEach { ch -> // drop first 'n' letter
                            val j = Integer.parseInt(ch.toString()) // NOTE: Char.toInt() is ord()
                            if (j >= circles.size)
                                Log.e("MainActivity", "index $j out of `circle` bounds (${circles.size}")
                            else {
                                dodecaSurfaceView.circles[i] = dodecaSurfaceView.circles[i].invert(dodecaSurfaceView.circles[j])
                                // Log.i("MainActivity", "$i: $oldCircle -> ${dodecaSurfaceView.circles[i]}")
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
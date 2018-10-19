package com.pierbezuhoff.dodeca

import android.graphics.Canvas
import android.view.SurfaceHolder

class DodecaThread(private val surfaceHolder: SurfaceHolder, private val dodecaView: DodecaView) : Thread() {
    var running: Boolean = false
    private val targetFPS = 50 // frames per second, the rate at which you would like to refresh the Canvas

    override fun run() {
        var startTime: Long
        var timeMillis: Long
        var waitTime: Long
        val targetTime = (1000 / targetFPS).toLong()
        canvas = surfaceHolder.lockCanvas()
        synchronized(surfaceHolder) {
            for (circle in dodecaView.circles)
                dodecaView.drawCircle(canvas!!, circle)
        }
        surfaceHolder.unlockCanvasAndPost(canvas)
        /*
        while (running) {
            startTime = System.nanoTime()
            canvas = null

            try {
                // locking the canvas allows us to draw on to it
                canvas = this.surfaceHolder.lockCanvas()
                synchronized(surfaceHolder) {
                    this.dodecaView.update()
                    this.dodecaView.draw(canvas!!)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                canvas.let {
                    try {
                        surfaceHolder.unlockCanvasAndPost(canvas)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            timeMillis = (System.nanoTime() - startTime) / 1000000
            waitTime = targetTime - timeMillis

            try {
                sleep(waitTime)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        */
    }

    companion object {
        private var canvas: Canvas? = null
    }

}
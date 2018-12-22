package com.pierbezuhoff.dodeca

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

class DodecaGestureDetector(
    activity: MainActivity,
    val view: DodecaView,
    val onSingleTap: (MotionEvent?) -> Unit = {}
) : GestureDetector.SimpleOnGestureListener(), View.OnTouchListener {
    private val gestureDetector = GestureDetector(activity, this)
    private val scaleListener = ScaleListener(view)
    private val scaleDetector = ScaleGestureDetector(activity, scaleListener)

    init {
        gestureDetector.setOnDoubleTapListener(this)
        view.setOnTouchListener(this)
    }

    override fun onTouch(view: View?, event: MotionEvent?): Boolean {
        gestureDetector.onTouchEvent(event)
        scaleDetector.onTouchEvent(event)
        view?.performClick()
        return true
    }

    override fun onDown(e: MotionEvent?): Boolean {
        super.onDown(e)
        return true
    }

    override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
        onSingleTap(e)
        return super.onSingleTapConfirmed(e)
    }

    override fun onDoubleTap(e: MotionEvent?): Boolean {
        e?.let {
            // view.pickColor(e.x, e.y)
            // activity.openColorPicker()
            // newColor <- open color chooser
            // view.changeColor(newColor)
            // see: https://github.com/martin-stone/hsv-alpha-color-picker-android

            // view.updateScroll(view.centerX - e.x, view.centerY - e.y) // centering
        }
        return super.onDoubleTap(e)
    }

    override fun onScroll(e1: MotionEvent?, e2: MotionEvent?, distanceX: Float, distanceY: Float): Boolean {
        view.updateScroll(-distanceX, -distanceY)
        return super.onScroll(e1, e2, distanceX, distanceY)
    }
}

class ScaleListener(val view: DodecaView) : ScaleGestureDetector.SimpleOnScaleGestureListener() {
    override fun onScale(detector: ScaleGestureDetector?): Boolean {
        detector?.let {
            view.updateScale(detector.scaleFactor)
        }
        super.onScale(detector)
        return true
    }
}

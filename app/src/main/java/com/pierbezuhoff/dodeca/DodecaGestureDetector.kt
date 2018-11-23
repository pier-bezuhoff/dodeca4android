package com.pierbezuhoff.dodeca

import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import com.pierbezuhoff.dodeca.R.id.bar

class DodecaGestureDetector(activity: MainActivity, val view: DodecaView, val onSingleTap: (MotionEvent?) -> Unit = {}) : GestureDetector.SimpleOnGestureListener() {

    init {
        val gestureDetector = GestureDetector(activity, this)
        gestureDetector.setOnDoubleTapListener(this)
        val scaleListener = ScaleListener(view)
        val scaleDetector = ScaleGestureDetector(activity, scaleListener)
        view.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            scaleDetector.onTouchEvent(event)
        }
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
            // change to choose color
            // see: https://github.com/martin-stone/hsv-alpha-color-picker-android
            view.updateScroll(view.centerX - e.x, view.centerY - e.y)
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

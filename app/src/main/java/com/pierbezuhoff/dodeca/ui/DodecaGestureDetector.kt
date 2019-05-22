package com.pierbezuhoff.dodeca.ui

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

/** Listen to single tap, scroll and scale gestures */
class DodecaGestureDetector(context: Context) : GestureDetector.SimpleOnGestureListener(), View.OnTouchListener {
    interface SingleTapListener { fun onSingleTap(e: MotionEvent?) }
    interface ScrollListener { fun onScroll(dx: Float, dy: Float) }
    interface ScaleListener { fun onScale(scale: Float, focusX: Float, focusY: Float) }

    private val gestureDetector =
        GestureDetector(context, this).also {
            it.setOnDoubleTapListener(this)
        }
    private val scaleGestureListener = ScaleGestureListener()
    private val scaleDetector = ScaleGestureDetector(context, scaleGestureListener)

    private var singleTapListener: SingleTapListener? = null
    private var scrollListener: ScrollListener? = null

    fun registerSingleTapListener(listener: SingleTapListener) { singleTapListener = listener }
    fun registerScrollListener(listener: ScrollListener) { scrollListener = listener }
    fun registerScaleListener(listener: ScaleListener) { scaleGestureListener.registerScaleListener(listener) }

    fun registerAsOnTouchListenerFor(view: View) {
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
        singleTapListener?.onSingleTap(e)
        return super.onSingleTapConfirmed(e)
    }

    override fun onScroll(e1: MotionEvent?, e2: MotionEvent?, distanceX: Float, distanceY: Float): Boolean {
        scrollListener?.onScroll(distanceX, distanceY)
        return super.onScroll(e1, e2, distanceX, distanceY)
    }
}

internal class ScaleGestureListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
    private var scaleListener: DodecaGestureDetector.ScaleListener? = null

    fun registerScaleListener(listener: DodecaGestureDetector.ScaleListener) {
        scaleListener = listener
    }

    override fun onScale(detector: ScaleGestureDetector?): Boolean {
        detector?.apply {
            scaleListener?.onScale(scaleFactor, focusX, focusY)
        }
        super.onScale(detector)
        return true
    }
}

package com.pierbezuhoff.dodeca.ui.dodecaview

import android.annotation.SuppressLint
import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.pierbezuhoff.dodeca.ui.meta.MetaDodecaView
import com.pierbezuhoff.dodeca.ui.meta.ScaleListener
import com.pierbezuhoff.dodeca.ui.meta.ScrollListener
import com.pierbezuhoff.dodeca.ui.meta.SingleTapListener
import com.pierbezuhoff.dodeca.utils.Connection

/** Listen to single tap, scroll and scale gestures */
class DodecaViewGestureDetector private constructor(
    context: Context
) : GestureDetector.SimpleOnGestureListener()
    , View.OnTouchListener
    , MetaDodecaView.AttachableGestureDetector
{
    private val gestureDetector = GestureDetector(context.applicationContext, this)
    private val scaleGestureListener = ScaleGestureListener()
    private val scaleDetector = ScaleGestureDetector(context.applicationContext, scaleGestureListener)

    private val singleTapConnection = Connection<SingleTapListener>()
    private val scrollConnection = Connection<ScrollListener>()
    val onSingleTapSubscription = singleTapConnection.subscription
    val onScrollSubscription = scrollConnection.subscription
    val onScaleSubscription = scaleGestureListener.onScaleSubscription

    override fun registerAsOnTouchListenerFor(view: View) {
        view.setOnTouchListener(this)
        gestureDetector.setOnDoubleTapListener(this)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(view: View?, event: MotionEvent?): Boolean {
        gestureDetector.onTouchEvent(event)
        scaleDetector.onTouchEvent(event)
        return true
    }

    override fun onDown(e: MotionEvent?): Boolean {
        super.onDown(e)
        return true
    }

    override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
        singleTapConnection.send { onSingleTap() }
        return super.onSingleTapConfirmed(e)
    }

    override fun onDoubleTap(e: MotionEvent?): Boolean {
        return super.onDoubleTap(e)
    }

    override fun onScroll(e1: MotionEvent?, e2: MotionEvent?, distanceX: Float, distanceY: Float): Boolean {
        scrollConnection.send { onScroll(distanceX, distanceY) }
        return super.onScroll(e1, e2, distanceX, distanceY)
    }

    private class ScaleGestureListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        private val scaleConnection = Connection<ScaleListener>()
        val onScaleSubscription = scaleConnection.subscription

        override fun onScale(detector: ScaleGestureDetector?): Boolean {
            detector?.apply {
                scaleConnection.send { onScale(scaleFactor, focusX, focusY) }
            }
            super.onScale(detector)
            return true
        }
    }

    companion object {
        @Volatile private var instance: DodecaViewGestureDetector? = null

        /** Thread-safe via double-checked locking */
        fun get(context: Context): DodecaViewGestureDetector =
            instance ?: synchronized(this) {
                instance
                    ?: DodecaViewGestureDetector(context).also {
                    instance = it
                }
            }
    }
}


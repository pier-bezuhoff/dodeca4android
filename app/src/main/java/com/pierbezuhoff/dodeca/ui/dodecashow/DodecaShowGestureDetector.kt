package com.pierbezuhoff.dodeca.ui.dodecashow

import android.annotation.SuppressLint
import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import com.pierbezuhoff.dodeca.utils.Connection

/** Listen to single tap, scroll and scale gestures, [context] should be applicationContext */
class DodecaShowGestureDetector private constructor(
    context: Context
) : GestureDetector.SimpleOnGestureListener()
    , View.OnTouchListener
{
    interface SingleTapListener { fun onSingleTap(e: MotionEvent?) }
    interface DoubleTapListener { fun onDoubleTap(e: MotionEvent?) }
    interface ScrollListener { fun onScroll(dx: Float, dy: Float) }

    private val gestureDetector = GestureDetector(context.applicationContext, this)

    private val singleTapConnection = Connection<SingleTapListener>()
    private val doubleTapConnection = Connection<DoubleTapListener>()
    private val scrollConnection = Connection<ScrollListener>()
    val onSingleTapSubscription = singleTapConnection.subscription
    val onDoubleTapSubscription = doubleTapConnection.subscription
    val onScrollSubscription = scrollConnection.subscription

    fun registerAsOnTouchListenerFor(view: View) {
        view.setOnTouchListener(this)
        gestureDetector.setOnDoubleTapListener(this)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(view: View?, event: MotionEvent?): Boolean {
        gestureDetector.onTouchEvent(event)
        return true
    }

    override fun onDown(e: MotionEvent?): Boolean {
        super.onDown(e)
        return true
    }

    override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
        singleTapConnection.send { onSingleTap(e) }
        return super.onSingleTapConfirmed(e)
    }

    override fun onDoubleTap(e: MotionEvent?): Boolean {
        return super.onDoubleTap(e)
    }

    override fun onScroll(e1: MotionEvent?, e2: MotionEvent?, distanceX: Float, distanceY: Float): Boolean {
        scrollConnection.send { onScroll(distanceX, distanceY) }
        return super.onScroll(e1, e2, distanceX, distanceY)
    }

    companion object {
        private const val TAG = "DodecaViewGestureDetector"
        @Volatile private var instance: DodecaShowGestureDetector? = null

        /** Thread-safe via double-checked locking */
        fun get(context: Context): DodecaShowGestureDetector =
            instance ?: synchronized(this) {
                instance
                    ?: DodecaShowGestureDetector(context).also {
                        instance = it
                    }
            }
    }
}


package com.pierbezuhoff.dodeca.ui.dodecashow

import android.annotation.SuppressLint
import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import com.pierbezuhoff.dodeca.ui.meta.MetaDodecaView
import com.pierbezuhoff.dodeca.utils.Connection

/** Listen to single tap, scroll and scale gestures, [context] should be applicationContext */
class DodecaShowGestureDetector private constructor(
    context: Context
) : GestureDetector.SimpleOnGestureListener()
    , View.OnTouchListener
    , MetaDodecaView.AttachableGestureDetector
{
    interface SingleTapListener { fun onSingleTap() }
    interface DoubleTapListener { fun onDoubleTap() }
    interface SwipeListener { fun onSwipe(velocityX: Float, velocityY: Float) }

    private val gestureDetector = GestureDetector(context.applicationContext, this)

    private val singleTapConnection = Connection<SingleTapListener>()
    private val doubleTapConnection = Connection<DoubleTapListener>()
    private val swipeConnection = Connection<SwipeListener>()
    val onSingleTapSubscription = singleTapConnection.subscription
    val onDoubleTapSubscription = doubleTapConnection.subscription
    val onSwipeSubscription = swipeConnection.subscription

    override fun registerAsOnTouchListenerFor(view: View) {
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
        singleTapConnection.send { onSingleTap() }
        return super.onSingleTapConfirmed(e)
    }

    override fun onDoubleTap(e: MotionEvent?): Boolean {
        doubleTapConnection.send { onDoubleTap() }
        return super.onDoubleTap(e)
    }

    override fun onFling(e1: MotionEvent?, e2: MotionEvent?, velocityX: Float, velocityY: Float): Boolean {
        swipeConnection.send { onSwipe(velocityX, velocityY) }
        return super.onFling(e1, e2, velocityX, velocityY)
    }

    companion object {
        private const val TAG = "DodecaShowGD"
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


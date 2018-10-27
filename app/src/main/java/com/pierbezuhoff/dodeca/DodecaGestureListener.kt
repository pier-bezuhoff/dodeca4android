package com.pierbezuhoff.dodeca

import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent

class DodecaGestureListener(val view: DodecaView) : GestureDetector.SimpleOnGestureListener() {

    override fun onDown(e: MotionEvent?): Boolean {
        super.onDown(e)
        return true
    }

    override fun onDoubleTap(e: MotionEvent?): Boolean {
        e?.let {
            // TODO: consider view.scale
            // centering tapped pos
            view.ddx = view.centerX - e.x
            view.ddy = view.centerY - e.y
            view.updateScroll()
        }
        return super.onDoubleTap(e)
    }

    override fun onScroll(e1: MotionEvent?, e2: MotionEvent?, distanceX: Float, distanceY: Float): Boolean {
        view.ddx = -distanceX / view.scale
        view.ddy = -distanceY / view.scale
        view.updateScroll()
        return super.onScroll(e1, e2, distanceX, distanceY)
    }
}

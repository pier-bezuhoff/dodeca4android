package com.pierbezuhoff.dodeca.ui.meta

interface SingleTapListener { fun onSingleTap() }
interface DoubleTapListener { fun onDoubleTap() }
interface ScrollListener { fun onScroll(dx: Float, dy: Float) }
interface ScaleListener { fun onScale(scale: Float, focusX: Float, focusY: Float) }
interface SwipeListener { fun onSwipe(velocityX: Float, velocityY: Float) }

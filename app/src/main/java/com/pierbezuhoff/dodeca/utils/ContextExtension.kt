package com.pierbezuhoff.dodeca.utils

import android.content.Context
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.annotation.AnimRes

fun Context.animate(view: View, @AnimRes id: Int, andThen: () -> Unit = {}) {
    val animation = AnimationUtils.loadAnimation(this, id)
    animation.setAnimationListener(object : Animation.AnimationListener {
        override fun onAnimationStart(animation: Animation?) { }
        override fun onAnimationEnd(animation: Animation?) { andThen() }
        override fun onAnimationRepeat(animation: Animation?) { }
    })
    view.startAnimation(animation)
}
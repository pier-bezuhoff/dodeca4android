package com.pierbezuhoff.dodeca.utils

import android.content.Context
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.annotation.AnimRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer

fun Context.animate(view: View, @AnimRes id: Int, andThen: () -> Unit = {}) {
    val animation = AnimationUtils.loadAnimation(this, id)
    animation.setAnimationListener(object : Animation.AnimationListener {
        override fun onAnimationStart(animation: Animation?) { }
        override fun onAnimationEnd(animation: Animation?) { andThen() }
        override fun onAnimationRepeat(animation: Animation?) { }
    })
    view.startAnimation(animation)
}

fun AppCompatActivity.bindSupportActionBar(
    toolbar: Toolbar,
    toolbarShown: LiveData<Boolean>,
    @AnimRes hideAnimationId: Int, @AnimRes showAnimationId: Int
) {
    setSupportActionBar(toolbar)
    toolbarShown.observe(this, Observer { shown: Boolean ->
        if (shown && supportActionBar?.isShowing != true)
            animate(toolbar, showAnimationId) {
                supportActionBar?.show()
            }
        else if (!shown && supportActionBar?.isShowing == true)
            animate(toolbar, hideAnimationId) {
                supportActionBar?.hide()
            }
    })
}
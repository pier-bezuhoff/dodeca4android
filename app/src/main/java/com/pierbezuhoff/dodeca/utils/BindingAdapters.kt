package com.pierbezuhoff.dodeca.utils

import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.view.View
import android.view.animation.Animation
import android.widget.ImageView
import androidx.appcompat.widget.TooltipCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.databinding.BindingAdapter
import com.pierbezuhoff.dodeca.R

@BindingAdapter("showWhen")
fun showWhen(view: View, shown: Boolean?) {
    view.visibility = if (shown != false) View.VISIBLE else View.GONE
}

@BindingAdapter("tintEnabled")
fun tintEnabled(view: View, enabled: Boolean) {
    ViewCompat.setBackgroundTintList(
        view,
        ColorStateList.valueOf(
            ContextCompat.getColor(view.context,
                if (enabled) R.color.darkerToolbarColor
                else R.color.toolbarColor
            )
        )
    )
}

@BindingAdapter(value = ["switchImageWhen", "trueDrawable", "falseDrawable"], requireAll = true)
fun switchImageWhen(imageView: ImageView, enabled: Boolean, trueDrawable: Drawable, falseDrawable: Drawable) {
    imageView.setImageDrawable(if (enabled) trueDrawable else falseDrawable)
}

@BindingAdapter(
    value = ["switchFadingImagesWhen", "trueDrawable", "falseDrawable", "trueAnimation", "falseAnimation"],
    requireAll = true
)
fun switchFadingImagesWhen(
    imageView: ImageView,
    enabled: Boolean,
    trueDrawable: Drawable, falseDrawable: Drawable,
    trueAnimation: Animation, falseAnimation: Animation
) {
    val drawable = if (enabled) trueDrawable else falseDrawable
    val animation = if (enabled) trueAnimation else falseAnimation
    imageView.setImageDrawable(drawable)
    animation.setAnimationListener(object : Animation.AnimationListener {
        override fun onAnimationRepeat(animation: Animation?) { }
        override fun onAnimationStart(animation: Animation?) { }
        override fun onAnimationEnd(animation: Animation?) {
            imageView.visibility = View.GONE
        }
    })
    imageView.visibility = View.VISIBLE
    imageView.startAnimation(animation)
}

@BindingAdapter("android:contentDescription")
fun setContentDescription(view: View, contentDescription: String) {
    TooltipCompat.setTooltipText(view, contentDescription)
    view.contentDescription = contentDescription
}

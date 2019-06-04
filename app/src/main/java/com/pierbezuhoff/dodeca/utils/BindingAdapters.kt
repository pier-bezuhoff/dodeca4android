package com.pierbezuhoff.dodeca.utils

import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.view.View
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

@BindingAdapter("android:contentDescription")
fun setContentDescription(view: View, contentDescription: String) {
    TooltipCompat.setTooltipText(view, contentDescription)
    view.contentDescription = contentDescription
}

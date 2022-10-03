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

@BindingAdapter(value = ["tintEnabled", "defaultTint", "darkerTint"], requireAll = false)
fun tintEnabled(view: View, enabled: Boolean?, defaultColor: Int?, darkerColor: Int?) {
    ViewCompat.setBackgroundTintList(
        view,
        ColorStateList.valueOf(
            if (enabled == true) (darkerColor ?: ContextCompat.getColor(view.context, R.color.darkerToolbarColor))
            else (defaultColor ?: ContextCompat.getColor(view.context, R.color.toolbarColor))
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


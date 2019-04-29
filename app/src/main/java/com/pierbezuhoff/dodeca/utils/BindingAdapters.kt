package com.pierbezuhoff.dodeca.utils

import android.content.res.ColorStateList
import android.view.View
import androidx.appcompat.widget.TooltipCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.databinding.BindingAdapter
import com.pierbezuhoff.dodeca.R

@BindingAdapter("app:showWhen")
fun showWhen(view: View, shown: Boolean) {
    view.visibility = if (shown) View.VISIBLE else View.GONE
}

@BindingAdapter("app:tintEnabled")
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

@BindingAdapter("app:description")
fun description(view: View, description: String) {
    TooltipCompat.setTooltipText(view, description)
    view.contentDescription = description

}
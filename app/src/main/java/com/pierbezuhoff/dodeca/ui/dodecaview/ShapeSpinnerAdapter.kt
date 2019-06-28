package com.pierbezuhoff.dodeca.ui.dodecaview

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.pierbezuhoff.dodeca.R
import com.pierbezuhoff.dodeca.data.Shape
import org.jetbrains.anko.layoutInflater

class ShapeSpinnerAdapter(private val context: Context) : BaseAdapter() {
    private val shapeDrawableResources: Map<Shape, Int> = mapOf(
        Shape.CIRCLE to R.drawable.ic_circle,
        Shape.SQUARE to R.drawable.ic_square,
        Shape.CROSS to R.drawable.ic_cross,
        Shape.VERTICAL_BAR to R.drawable.ic_vertical_bar,
        Shape.HORIZONTAL_BAR to R.drawable.ic_horizontal_bar
    )
    private val shapes: Array<Shape> = Shape.values()
    private class SpinnerViewHolder(val imageView: ImageView)

    override fun getItem(position: Int): Shape = shapes[position]
    override fun getItemId(position: Int): Long = position.toLong()
    override fun getCount(): Int = shapes.size

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val itemView: View = convertView
            ?: context.layoutInflater
                .inflate(R.layout.shape_spinner_row, parent, false).apply {
                    tag = SpinnerViewHolder(
                        findViewById(R.id.shape_spinner_image)
                    )
                }
        val shapeDrawableResource: Int = shapeDrawableResources.getValue(shapes[position])
        (itemView.tag as SpinnerViewHolder).imageView.setImageDrawable(
            ContextCompat.getDrawable(context, shapeDrawableResource)
        )
        return itemView
    }
}
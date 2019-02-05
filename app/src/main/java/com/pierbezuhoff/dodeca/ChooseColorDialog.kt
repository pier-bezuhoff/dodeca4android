package com.pierbezuhoff.dodeca

import android.app.Dialog
import android.content.Context
import android.graphics.PorterDuff
import android.graphics.drawable.LayerDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rarepebble.colorpicker.ColorPickerView
import kotlinx.android.synthetic.main.choose_color_row.view.*
import kotlinx.android.synthetic.main.edit_circle.view.*
import org.jetbrains.anko.alert
import org.jetbrains.anko.cancelButton
import org.jetbrains.anko.customView
import org.jetbrains.anko.include

class ChooseColorDialog(val activity: MainActivity, private val circleGroup: CircleGroup)  {
    private var listener: ChooseColorListener = activity
    interface ChooseColorListener {
        fun onChooseColorClosed()
    }

    fun build(): Dialog {
        val builder = AlertDialog.Builder(activity)
        val inflater = activity.layoutInflater
        val layout = inflater.inflate(R.layout.choose_color_dialog, null)
        builder.setView(layout)
        val manager = LinearLayoutManager(activity)
        val circleAdapter = CircleAdapter(activity, circleGroup)
        layout.findViewById<RecyclerView>(R.id.color_groups)!!.apply {
            layoutManager = manager
            adapter = circleAdapter
        }
        val dialog = builder.apply {
            setMessage("Choose circle to change color of")
            setPositiveButton("Change") { _, _ -> onChooseColor() }
            setNegativeButton("Cancel") { _, _ -> listener.onChooseColorClosed() }
        }.create()
        dialog.setOnDismissListener { listener.onChooseColorClosed() }
        return dialog
    }

    private fun onChooseColor() {
        // https://github.com/martin-stone/hsv-alpha-color-picker-android
        // notify adapter on change
    }
}

class CircleAdapter(
    private val context: Context,
    private val circleGroup: CircleGroup
) : RecyclerView.Adapter<CircleAdapter.ViewHolder>() {
    class ViewHolder(val row: View) : RecyclerView.ViewHolder(row)

    private val rows: Array<CircleRow> =
        circleGroup.figures
            .mapIndexed { i, figure -> CircleRow(figure, i) }
            .filter { it.figure.show } // maybe: also show invisible circles in end + options.showAllCircles
            .sortedBy { it.figure.color }
            .toTypedArray()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val row = LayoutInflater.from(parent.context)
            .inflate(R.layout.choose_color_row, parent, false)
        return ViewHolder(row)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val row = rows[position]
        val figure = row.figure
        with(holder.row) {
            this.circle_name.text = "${row.id}"
            val circleImage =
                ResourcesCompat.getDrawable(resources, R.drawable.circle_image, null) as LayerDrawable
            circleImage.mutate()
            val border = circleImage.getDrawable(0)
            val inner = circleImage.getDrawable(1)
            if (figure.fill) {
                inner.setColorFilter(figure.color, PorterDuff.Mode.SRC_ATOP)
                border.setColorFilter(figure.borderColor ?: figure.color, PorterDuff.Mode.SRC_ATOP)
            } else {
                inner.alpha = 0
                border.setColorFilter(figure.color, PorterDuff.Mode.SRC_ATOP)
            }
            this.circle_image.setImageDrawable(circleImage)
            setOnClickListener {
                editCircle(row, position)
            }
        }
    }

    private fun editCircle(row: CircleRow, position: Int) {
        val figure = row.figure
        var color: Int = figure.color
        var fill: Boolean = figure.fill
        var borderColor: Int? = figure.borderColor
        context.alert("Edit circle ${row.id}") {
            customView {
                include<LinearLayout>(R.layout.edit_circle).also { layout ->
                    layout.circle_color.apply {
                        setColorFilter(color)
                        setOnClickListener {
                            colorPickerDialog(color) { newColor ->
                                color = newColor
                                layout.circle_color.setColorFilter(newColor)
                                if (borderColor == null)
                                    layout.circle_border_color.setColorFilter(newColor)
                            }.show()
                        }
                    }
                    layout.circle_fill.apply {
                        isChecked = fill
                        setOnCheckedChangeListener { _, checked -> fill = checked }
                    }
                    layout.circle_has_border_color.apply {
                        isChecked = borderColor != null
                        setOnCheckedChangeListener { _, checked ->
                            layout.circle_border_color?.isEnabled = checked
                            if (!checked && borderColor != null)
                                borderColor = null
                            layout.circle_border_color.setColorFilter(color)
                        }
                    }
                    layout.circle_border_color.apply {
                        if (borderColor == null)
                            isEnabled = false
                        setColorFilter(borderColor ?: color)
                        setOnClickListener {
                            colorPickerDialog(borderColor ?: color) { newColor ->
                                borderColor = newColor
                                layout.circle_border_color.setColorFilter(newColor)
                            }.show()
                        }
                    }
                }
            }
            positiveButton("Apply") {
                val newRow = row.copy(color, fill, borderColor)
                rows[position] = newRow
                circleGroup[row.id] = newRow.figure
                notifyDataSetChanged()
            }
            negativeButton("Cancel") { }
        }.show()
    }

    private inline fun colorPickerDialog(color: Int, crossinline onChosen: (newColor: Int) -> Unit) =
        context.alert("Choose color") {
            val colorPicker = ColorPickerView(context)
            colorPicker.color = color
            colorPicker.showAlpha(false)
            customView {
                addView(colorPicker, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
            positiveButton("Ok") {
                onChosen(colorPicker.color)
            }
            cancelButton { }
        }

    override fun getItemCount(): Int = rows.size
}

class CircleRow(val figure: CircleFigure, val id: Int) {
    private val equivalence: List<Any?> get() = listOf(figure.color, figure.fill, figure.borderColor)
    fun equivalent(other: CircleRow): Boolean = equivalence == other.equivalence
    fun copy(newColor: Int?, newFill: Boolean?, newBorderColor: Int?): CircleRow =
        CircleRow(figure.copy(newColor = newColor, newFill = newFill, newBorderColor = newBorderColor), id)
}

class CirclesRow(val circles: Array<CircleRow>)
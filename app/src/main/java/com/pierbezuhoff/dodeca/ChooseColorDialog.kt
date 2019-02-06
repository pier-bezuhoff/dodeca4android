package com.pierbezuhoff.dodeca

import android.app.Dialog
import android.content.Context
import android.graphics.PorterDuff
import android.graphics.drawable.LayerDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Switch
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rarepebble.colorpicker.ColorPickerView
import kotlinx.android.synthetic.main.choose_color_dialog.view.*
import kotlinx.android.synthetic.main.choose_color_row.view.*
import kotlinx.android.synthetic.main.edit_circle.view.*
import org.jetbrains.anko.AlertBuilder
import org.jetbrains.anko.alert
import org.jetbrains.anko.cancelButton
import org.jetbrains.anko.customView
import org.jetbrains.anko.include

class ChooseColorDialog(val activity: MainActivity, private val circleGroup: CircleGroup) {
    private var listener: ChooseColorListener = activity
    private lateinit var rowAdapter: CircleAdapter

    interface ChooseColorListener {
        fun onChooseColorClosed()
    }

    fun build(): Dialog {
        val builder = AlertDialog.Builder(activity)
        val inflater = activity.layoutInflater
        val layout = inflater.inflate(R.layout.choose_color_dialog, null)
        builder.setView(layout)
        val manager = LinearLayoutManager(activity)
        rowAdapter = CircleAdapter(activity, circleGroup)
        val recyclerView = layout.findViewById<RecyclerView>(R.id.color_groups)!!.apply {
            layoutManager = manager
            adapter = rowAdapter
        }
        layout.all_circles_checkbox.setOnCheckedChangeListener { _, checked ->
            rowAdapter.onCheckAll(checked,
                (0 until recyclerView.childCount).map {
                    recyclerView.getChildViewHolder(recyclerView.getChildAt(it))
                        .itemView.findViewById<CheckBox>(R.id.circle_checkbox)
                })
        }
        layout.sort_by_color.setOnCheckedChangeListener { _, checked -> rowAdapter.onSortByColor(checked) }
        layout.sort_by_name.setOnCheckedChangeListener { _, checked -> rowAdapter.onSortByName(checked) }
        val dialog = builder.apply {
            setMessage("Choose circle to edit")
            setPositiveButton("Edit") { _, _ -> Unit } // will be set later
            setNegativeButton("Cancel") { _, _ -> listener.onChooseColorClosed() }
        }.create()
        dialog.setOnDismissListener { listener.onChooseColorClosed() }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (rowAdapter.checkedRows.isNotEmpty())
                    rowAdapter.editCheckedCircles()
                else
                    dialog.dismiss()
            }
        }
        return dialog
    }
}


class CircleAdapter(
    private val context: Context,
    private val circleGroup: CircleGroup
) : RecyclerView.Adapter<CircleAdapter.ViewHolder>() {
    class ViewHolder(val row: View) : RecyclerView.ViewHolder(row)

    private val rows: MutableList<CircleRow> =
        circleGroup.figures
            .asSequence()
            .mapIndexed { i, figure -> CircleRow(figure, i) }
            .filter { it.figure.show } // maybe: also show invisible circles in the end + options.showAllCircles
            .sortedBy { it.figure.color }
            .mapIndexed { i, row -> row.apply { position = i } }
            .toMutableList()
    val checkedRows: MutableSet<CircleRow> = mutableSetOf()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val row = LayoutInflater.from(parent.context)
            .inflate(R.layout.choose_color_row, parent, false)
        return ViewHolder(row) // .apply { setIsRecyclable(false) }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val row = rows[position]
        val figure = row.figure
        with(holder.row) {
            circle_name.text = "${row.id}"
            circle_image.setImageDrawable(circleImageFor(figure))
            circle_checkbox.setOnCheckedChangeListener { _, checked ->
                row.checked = checked
                if (checked) {
                    checkedRows.add(row)
                } else {
                    checkedRows.remove(row)
                }
            }
            circle_checkbox.isChecked = row.checked
            circle_layout.setOnClickListener {
                editCircle(row, position)
            }
        }
    }

    private fun circleImageFor(figure: CircleFigure): LayerDrawable {
        val circleImage = ContextCompat.getDrawable(context, R.drawable.circle_image) as LayerDrawable
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
        return circleImage
    }

    private fun editCircle(row: CircleRow, position: Int) {
        editCircleDialog(row.figure, "Edit circle ${row.id}") { color, fill, borderColor ->
            val newRow = row.copy(newColor = color, newFill = fill, newBorderColor = borderColor)
            rows[position] = newRow
            circleGroup[row.id] = newRow.figure
            notifyDataSetChanged()
        }.show()
    }

    private inline fun editCircleDialog(
        figure: CircleFigure,
        message: String,
        crossinline onApply: (color: Int?, fill: Boolean?, borderColor: Int?) -> Unit
    ): AlertBuilder<*> {
        var color: Int = figure.color
        var fill: Boolean = figure.fill
        var borderColor: Int? = figure.borderColor
        var colorChanged = false
        var fillChanged = false
        var borderColorChanged = false
        val dialog = context.alert(message) {
            customView {
                include<LinearLayout>(R.layout.edit_circle).also { layout ->
                    val colorButton: ImageButton = layout.circle_color
                    val fillSwitch: Switch = layout.circle_fill
                    val borderColorButton: ImageButton = layout.circle_border_color
                    val borderColorSwitch: Switch = layout.circle_has_border_color
                    colorButton.apply {
                        setColorFilter(color)
                        setOnClickListener {
                            colorPickerDialog(color) { newColor ->
                                color = newColor
                                colorChanged = true
                                colorButton.setColorFilter(newColor)
                                if (borderColor == null)
                                    borderColorButton.setColorFilter(newColor)
                            }.show()
                        }
                    }
                    fillSwitch.apply {
                        isChecked = fill
                        setOnCheckedChangeListener { _, checked -> fill = checked; fillChanged = true }
                    }
                    borderColorSwitch.apply {
                        isChecked = borderColor != null
                        setOnCheckedChangeListener { _, checked ->
                            if (!checked && borderColor != null) {
                                borderColor = null
                                borderColorButton.setColorFilter(color)
                            } else if (checked) {
                                borderColor = borderColor ?: color
                                borderColorButton.setColorFilter(borderColor!!)
                            }
                            borderColorChanged = true
                        }
                    }
                    borderColorButton.apply {
                        setColorFilter(borderColor ?: color)
                        setOnClickListener {
                            colorPickerDialog(borderColor ?: color) { newColor ->
                                borderColorSwitch.apply {
                                    if (!isChecked)
                                        isChecked = true // NOTE: may change borderColor
                                }
                                borderColor = newColor
                                borderColorChanged = true
                                borderColorButton.setColorFilter(newColor)
                            }.show()
                        }
                    }
                }
            }
            positiveButton("Apply") { onApply(
                if (colorChanged) color else null,
                if (fillChanged) fill else null,
                if (borderColorChanged) borderColor else null
            ) }
            negativeButton("Cancel") { }
        }
        return dialog
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

    fun editCheckedCircles() {
        val blueprint = checkedRows.toList()[0]
        val message = when(checkedRows.size) {
            1 -> "Edit circle ${blueprint.id}"
            2, 3 -> "Edit circles " + checkedRows.take(3).joinToString { it.id.toString() }
            else -> "Edit circles " + checkedRows.take(3).joinToString { it.id.toString() } + "..."
        }
        editCircleDialog(blueprint.figure, message) { color, fill, borderColor ->
            checkedRows.forEach { row ->
                val newRow = row.copy(newColor = color, newFill = fill, newBorderColor = borderColor)
                rows[row.position!!] = newRow
                circleGroup[row.id] = newRow.figure
            }
            notifyDataSetChanged()
            checkedRows.clear()
        }.show()
    }

    override fun getItemCount(): Int = rows.size

    fun onCheckAll(checked: Boolean, checkboxes: List<CheckBox>) {
        if (checked) {
            checkedRows.addAll(rows)
            rows.forEach { it.checked = true }
            checkboxes.forEach {
                it.isChecked = true
            }
        } else {
            checkedRows.clear()
            rows.forEach { it.checked = false }
            checkboxes.forEach {
                it.isChecked = false
            }
        }
    }

    fun onSortByColor(ascending: Boolean) {
        if (ascending)
            rows.sortBy { it.figure.color }
        else
            rows.sortByDescending { it.figure.color }
        notifyDataSetChanged()
    }

    fun onSortByName(ascending: Boolean) {
        if (ascending)
            rows.sortBy { it.id }
        else
            rows.sortByDescending { it.id }
        notifyDataSetChanged()
    }
}

data class CircleRow(val figure: CircleFigure, val id: Int, var position: Int? = null, var checked: Boolean = false) {
    private val equivalence: List<Any?> get() = listOf(figure.color, figure.fill, figure.borderColor)
    fun equivalent(other: CircleRow): Boolean = equivalence == other.equivalence
    fun copy(newColor: Int?, newFill: Boolean?, newBorderColor: Int?, newChecked: Boolean? = null): CircleRow =
        CircleRow(
            figure.copy(newColor = newColor, newFill = newFill, newBorderColor = newBorderColor),
            id, position, newChecked ?: checked)
}

class CircleGroupRow(val circles: Array<CircleRow>)
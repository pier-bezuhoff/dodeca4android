package com.pierbezuhoff.dodeca

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.PorterDuff
import android.graphics.drawable.LayerDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Switch
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.TooltipCompat
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
        val recyclerView = layout.findViewById<RecyclerView>(R.id.circle_rows)!!.apply {
            layoutManager = manager
            adapter = rowAdapter
        }
        layout.all_circles_checkbox.setOnCheckedChangeListener { _, checked -> rowAdapter.onCheckAll(checked) }
        layout.sort_by_color.setOnCheckedChangeListener { _, checked -> rowAdapter.onSortByColor(checked) }
        layout.sort_by_name.setOnCheckedChangeListener { _, checked -> rowAdapter.onSortByName(checked) }
        setOf(layout.all_circles_checkbox, layout.sort_by_color, layout.sort_by_name).forEach {
            TooltipCompat.setTooltipText(it, it.contentDescription)
        }
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

    private val rows: MutableList<Row> =
        circleGroup.figures
            .mapIndexed { i, figure -> CircleRow(figure, i) }
            .filter { it.figure.show } // maybe: also show invisible circles in the end + options.showAllCircles
            .sortedBy { it.figure.color }
            .groupBy { it.equivalence } // hope we don't lose CircleRow::id order
            .values
            .mapIndexed { i, list -> CircleGroupRow(list.toMutableList(), position = i) }
            .toMutableList()
    // store rows with checked checkboxes, they may be collapsed though
    val checkedRows: MutableSet<Row> = mutableSetOf()

    private inline fun CircleRow.persist() {
        circleGroup[id] = this.figure
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val row = LayoutInflater.from(parent.context)
            .inflate(R.layout.choose_color_row, parent, false)
        return ViewHolder(row)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val row = rows[position]
        when(row) {
            is CircleRow -> onBindCircleVH(holder, row, position)
            is CircleGroupRow -> onBindCircleGroupVH(holder, row, position)
        }
    }

    private fun onBindCircleVH(holder: ViewHolder, row: CircleRow, position: Int) {
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

    private fun onBindCircleGroupVH(holder: ViewHolder, row: CircleGroupRow, position: Int) {
        val blueprint = row.circles[0]
        with(holder.row) {
            circle_name.text = row.name
            circle_name.setOnClickListener {
                row.expanded = !row.expanded
                if (row.expanded) { // expand
                    rows.addAll(1 + row.position!!, row.circles)
                    rows.slice(1 + row.position!! .. row.lastChildPosition!!).forEachIndexed { i, circleRow ->
                        circleRow.position = i
                    }
                } else { // collapse
                    // Q: but what if some has changed?
                    repeat(row.circles.size) {
                        rows[row.position!! + 1].position = null
                        rows.removeAt(row.position!! + 1)
                    }
                }
                notifyDataSetChanged()
            }
            circle_image.setImageDrawable(circleImageFor(blueprint.figure))
            circle_checkbox.setOnCheckedChangeListener { _, checked ->
                if (row.expanded) {
                    row.circles.forEach { circleRow -> circleRow.checked = checked }
                    // use position or row.position!! in notify?
                    notifyItemRangeChanged(position, 1 + row.circles.size)
                }
                if (checked)
                    checkedRows.addAll(row.circles)
                else
                    checkedRows.removeAll(row.circles)
            }
            circle_checkbox.isChecked = row.checked
            circle_image.setOnClickListener {
                editCirclesDialog(row.circles) { color, fill, borderColor ->
                    row.circles.forEachIndexed { i, circleRow ->
                        val newRow =
                            circleRow.copy(newColor = color, newFill = fill, newBorderColor = borderColor)
                        row.circles[i] = newRow
                        if (row.expanded)
                            rows[row.position!! + 1 + i] = newRow
                        newRow.persist()
                    }
                    if (row.expanded) {
                        notifyItemRangeChanged(position, 1 + row.circles.size)
                    } else {
                        notifyItemChanged(position)
                    }
                }
            }
        }
    }

    private fun circleImageFor(figure: CircleFigure): LayerDrawable {
        val circleImage = ContextCompat.getDrawable(context, R.drawable.circle_image)
            as LayerDrawable
        circleImage.mutate() // without it ALL circleImage layers will change
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
            newRow.persist()
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
        val builder = context.alert(message) {
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
        return builder
    }

    private inline fun colorPickerDialog(
        color: Int,
        crossinline onChosen: (newColor: Int) -> Unit
    ): AlertBuilder<DialogInterface> =
        context.alert("Choose color") {
            val colorPicker = ColorPickerView(context)
            colorPicker.color = color
            colorPicker.showAlpha(false)
            customView {
                addView(colorPicker, ViewGroup.LayoutParams.MATCH_PARENT.let { ViewGroup.LayoutParams(it, it) })
            }
            positiveButton("Ok") { onChosen(colorPicker.color) }
            cancelButton { }
        }

    private inline fun editCirclesDialog(
        circles: List<CircleRow>,
        crossinline onApply: (color: Int?, fill: Boolean?, borderColor: Int?) -> Unit = {_, _, _ -> }
    ): AlertBuilder<DialogInterface> {
        val blueprint = circles[0]
        val message = when(circles.size) {
            1 -> "Edit circle ${blueprint.id}"
            2, 3 -> "Edit circles " + circles.take(3).joinToString { it.id.toString() }
            else -> "Edit circles " + circles.take(2).joinToString { it.id.toString() } + ", ..., " + circles.last().id.toString()
        }
        return editCircleDialog(blueprint.figure, message, onApply)
    }

    fun editCheckedCircles() {
        val circles = checkedRows.filterIsInstance<CircleRow>()
        editCirclesDialog(circles) { color, fill, borderColor ->
            circles.forEach { row ->
                val newRow = row.copy(newColor = color, newFill = fill, newBorderColor = borderColor)
                newRow.persist()
                row.position?.let { rows[it] = newRow }
            }
            notifyDataSetChanged()
        }.show()
    }

    override fun getItemCount(): Int = rows.size

    fun onCheckAll(checked: Boolean) {
        if (checked) {
            checkedRows.addAll(rows)
            rows.forEach { it.checked = true }
        } else {
            checkedRows.clear()
            rows.forEach { it.checked = false }
        }
        notifyDataSetChanged()
    }

    fun onSortByColor(ascending: Boolean) {
        // what a shame
        // I cannot lift function application
//        if (ascending)
//            rows.sortBy { it.figure.color }
//        else
//            rows.sortByDescending { it.figure.color }
//        notifyDataSetChanged()
    }

    fun onSortByName(ascending: Boolean) {
//        if (ascending)
//            rows.sortBy { it.id }
//        else
//            rows.sortByDescending { it.id }
//        notifyDataSetChanged()
    }
}

sealed class Row(
    var position: Int? = null,
    var checked: Boolean = false
)

class CircleRow(
    val figure: CircleFigure,
    val id: Int,
    position: Int? = null,
    checked: Boolean = false
) : Row(position, checked) {
    val equivalence: List<Any?> get() = listOf(figure.color, figure.fill, figure.borderColor)
    fun equivalent(other: CircleRow): Boolean = equivalence == other.equivalence
    fun copy(newColor: Int?, newFill: Boolean?, newBorderColor: Int?, newChecked: Boolean? = null): CircleRow =
        CircleRow(
            figure.copy(newColor = newColor, newFill = newFill, newBorderColor = newBorderColor),
            id, position, newChecked ?: checked)
}

class CircleGroupRow(
    val circles: MutableList<CircleRow>, // non-empty
    var expanded: Boolean = false,
    position: Int? = null,
    checked: Boolean = false
) : Row(position, checked) {
    val childPositions: List<Int>? get() = position?.let { (it + 1 .. it + circles.size).toList() }
    val lastChildPosition: Int? get() = position?.let { it + circles.size + 1 }
    val name: String get() = when(circles.size) {
        1 -> "${circles[0].id}"
        2, 3 -> circles.take(3).joinToString { it.id.toString() }
        else -> circles.take(2).joinToString { it.id.toString() } + ", ..., " + circles.last().id.toString()
    }
}


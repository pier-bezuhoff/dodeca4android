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
        val recyclerView = layout.circle_rows.apply {
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

    private val circleRows: List<CircleRow> =
        circleGroup.figures
            .mapIndexed { i, figure -> CircleRow(figure, i) }
            .filter { it.figure.show } // maybe: also show invisible circles in the end + options.showAllCircles
            .sortedBy { it.figure.color }
    private var rows: MutableList<Row> = factorByEquivalence(circleRows).toMutableList()

    // store rows with checked checkboxes, they may be collapsed though
    val checkedRows: MutableSet<Row> = mutableSetOf()
    private var binding: Boolean = false

    private inline fun CircleRow.persist() { circleGroup[id] = this.figure }
    private inline fun Row.remove() {
        position?.let {
            rows.removeAt(it)
            position = null
        }
        // NOTE: reassign position should be done later
    }
    private inline fun CircleRow.check() { checked = true; checkedRows.add(this) }
    private inline fun CircleRow.uncheck() { checked = false; checkedRows.remove(this) }
    private inline fun CircleGroupRow.check() {
        checked = true
        checkedRows.add(this)
        circles.forEach { it.check() }
    }
    private inline fun CircleGroupRow.uncheck() {
        checked = false
        checkedRows.remove(this)
        circles.forEach { it.uncheck() }
    }

    private fun factorByEquivalence(circles: List<CircleRow>): List<Row> =
        circles.groupBy { it.equivalence } // hope we don't lose CircleRow::id order
            .values
            .mapIndexed { i, list ->
                CircleGroupRow(
                    list.toMutableList().apply { forEach { it.position = null } },
                    position = i,
                    checked = list.all { it.checked }
                )
            }
            .map { if (it.size == 1) it.blueprint.apply { position = it.position } else it } // drop singletons

    private fun reassignPositions() {
        rows.forEachIndexed { i, row -> row.position = i }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val row = LayoutInflater.from(parent.context)
            .inflate(R.layout.choose_color_row, parent, false)
        return ViewHolder(row)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        binding = true
        val row = rows[position]
        when(row) {
            is CircleRow -> onBindCircleVH(holder, row, position)
            is CircleGroupRow -> onBindCircleGroupVH(holder, row, position)
        }
        binding = false
    }

    private fun onBindCircleVH(holder: ViewHolder, row: CircleRow, position: Int) {
        val figure = row.figure
        with(holder.row) {
            // increase left margin
            (circle_layout.layoutParams as ViewGroup.MarginLayoutParams).apply {
                leftMargin = 40
            }
            circle_name.text = "${row.id}"
            circle_image.setImageDrawable(circleImageFor(figure.equivalence))
            circle_checkbox.setOnCheckedChangeListener { _, checked -> if (checked) row.check() else row.uncheck() }
            circle_checkbox.isChecked = row.checked
            circle_layout.setOnClickListener { editCircle(row, position) }
            expand_group.visibility = View.GONE
            expand_group.setOnCheckedChangeListener { _, _ -> }
            expand_group.isChecked = false
        }
    }

    private fun onBindCircleGroupVH(holder: ViewHolder, row: CircleGroupRow, position: Int) {
        with(holder.row) {
            (circle_layout.layoutParams as ViewGroup.MarginLayoutParams).apply {
                leftMargin = 8
            }
            circle_name.text = row.name // should change whenever .circles changes
            circle_image.setImageDrawable(circleImageFor(row.equivalence))
            circle_checkbox.setOnCheckedChangeListener { _, checked -> if (!binding) checkGroup(row, checked) }
            circle_checkbox.isChecked = row.checked
            circle_layout.setOnClickListener {
                editCirclesDialog(row.circles) { color, fill, borderColor ->
                    row.circles.forEach { circleRow ->
                        circleRow.figure =
                            circleRow.figure.copy(newColor = color, newFill = fill, newBorderColor = borderColor)
                        circleRow.persist()
                    }
                    row.equivalence = row.blueprint.equivalence
                    notifyDataSetChanged()
                }.show()
            }
            expand_group.visibility = View.VISIBLE
            expand_group.setOnCheckedChangeListener { _, checked -> if (!binding) expandOrCollapseGroup(checked, row) }
            if (expand_group.isChecked != row.expanded)
                expand_group.isChecked = row.expanded
        }
    }

    private fun checkGroup(row: CircleGroupRow, checked: Boolean) {
        // NOTE: can be called only when !binding
        if (checked) row.check() else row.uncheck()
        if (row.expanded) { // check children
            notifyDataSetChanged()
        }
    }

    private fun expandOrCollapseGroup(expand: Boolean, row: CircleGroupRow) {
        // NOTE: should be called only when !binding
        row.expanded = expand
        if (expand) {
            expandGroup(row)
        } else {
            collapseGroup(row)
        }
        notifyDataSetChanged()
    }

    private fun expandGroup(row: CircleGroupRow) {
        rows.addAll(1 + row.position!!, row.circles)
        reassignPositions()
        // notifyDataSetChanged() should be called afterwards
    }

    private fun collapseGroup(row: CircleGroupRow) {
        val newCircles = circleRows.filter { it.equivalence == row.equivalence }
        when(newCircles.size) {
            0 -> row.remove()
            1 -> {
                val singleRow = newCircles[0]
                if (!singleRow.shown)
                    rows.add(row.position!! + 1, singleRow)
                row.remove()
            }
            else -> {
                row.circles.clear()
                row.circles.addAll(newCircles)
                rows.removeAll(newCircles)
                newCircles.forEach { it.position = null }
            }
        }
        reassignPositions()
        // notifyDataSetChanged() should be called afterwards
    }

    private fun circleImageFor(equivalence: Equivalence): LayerDrawable {
        val (color, fill, borderColor) = equivalence
        val circleImage = ContextCompat.getDrawable(context, R.drawable.circle_image)
            as LayerDrawable
        circleImage.mutate() // without it ALL circleImage layers will change
        val border = circleImage.getDrawable(0)
        val inner = circleImage.getDrawable(1)
        if (fill) {
            inner.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)
            border.setColorFilter(borderColor ?: color, PorterDuff.Mode.SRC_ATOP)
        } else {
            inner.alpha = 0
            border.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)
        }
        return circleImage
    }

    private fun editCircle(row: CircleRow, position: Int) {
        editCircleDialog(row.figure, "Edit circle ${row.id}") { color, fill, borderColor ->
            row.figure = row.figure.copy(newColor = color, newFill = fill, newBorderColor = borderColor)
            row.persist()
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
            else -> "Edit circles " + circlesNumbers(circles)
        }
        return editCircleDialog(blueprint.figure, message, onApply)
    }

    fun editCheckedCircles() {
        val circles = checkedRows.filterIsInstance<CircleRow>()
        editCirclesDialog(circles) { color, fill, borderColor ->
            circles.forEach { row ->
                row.figure = row.figure.copy(newColor = color, newFill = fill, newBorderColor = borderColor)
                row.persist()
            }
            // group cannot shrink
            checkedRows
                .filterIsInstance<CircleGroupRow>()
                .forEach {
                    it.equivalence = it.blueprint.equivalence
                }
            notifyDataSetChanged()
        }.show()
    }

    override fun getItemCount(): Int = rows.size

    fun onCheckAll(checked: Boolean) {
        if (!binding) {
            if (checked) {
                checkedRows.addAll(circleRows)
                checkedRows.addAll(rows)
            } else {
                checkedRows.clear()
            }
            rows.forEach { it.checked = checked }
            circleRows.forEach { it.checked = checked }
            notifyDataSetChanged()
        }
    }

    fun onSortByColor(ascending: Boolean) {
        val unexpandedGroupRows = rows.filterIsInstance<CircleGroupRow>().filter { !it.expanded }
        // deliberately not lazy stream: collapseGroup mutate rows
        unexpandedGroupRows.forEach { expandGroup(it) }
        val _rows: MutableList<CircleRow> = rows.filterIsInstance<CircleRow>().toMutableList()
        if (ascending)
            _rows.sortBy { it.figure.color }
        else
            _rows.sortByDescending { it.figure.color }
        rows = factorByEquivalence(_rows).toMutableList()
        reassignPositions()
        notifyDataSetChanged()
    }

    fun onSortByName(ascending: Boolean) {
        val unexpandedGroupRows = rows.filterIsInstance<CircleGroupRow>().filter { !it.expanded }
        // deliberately not lazy stream: expandGroup mutate rows
        unexpandedGroupRows.forEach { expandGroup(it) }
        val _rows = rows.filterIsInstance<CircleRow>().toMutableList()
        if (ascending)
            _rows.sortBy { it.id }
        else
            _rows.sortByDescending { it.id }
        // now we have right order, but without groups
        // we will group consequential with same equivalence
        rows = _rows
            .consecutiveGroupBy { it.equivalence }
            .map { (_, list) ->
                if (list.size == 1)
                    list[0]
                else
                    CircleGroupRow(
                        list.toMutableList().apply { forEach { it.position = null } },
                        checked = list.all { it.checked }
                    )
            }
            .toMutableList()
        reassignPositions()
        notifyDataSetChanged()
    }

    companion object {
        const val TAG: String = "CircleAdapter"
    }
}

data class Equivalence(val color: Int, val fill: Boolean, val borderColor: Int?)

val CircleFigure.equivalence get() = Equivalence(color, fill, borderColor)

sealed class Row(
    var position: Int? = null,
    var checked: Boolean = false
) {
    val shown: Boolean get() = position != null
}

class CircleRow(
    var figure: CircleFigure,
    val id: Int,
    position: Int? = null,
    checked: Boolean = false
) : Row(position, checked) {
    val equivalence: Equivalence get() = Equivalence(figure.color, figure.fill, figure.borderColor)
    fun equivalent(other: CircleRow): Boolean = equivalence == other.equivalence
    override fun toString(): String = "row \"$id\" at $position (checked: $checked)"
}

class CircleGroupRow(
    val circles: MutableList<CircleRow>, // size >= 2
    var expanded: Boolean = false,
    position: Int? = null,
    checked: Boolean = false
) : Row(position, checked) {
    var equivalence: Equivalence = circles[0].equivalence
    val size: Int get() = circles.size
    val blueprint: CircleRow get() = circles[0]
    val childPositions: List<Int>? get() = position?.let { (it + 1 .. it + circles.size).toList() }
    val lastChildPosition: Int? get() = position?.let { it + circles.size + 1 }
    val name: String get() = circlesNumbers(circles)
    override fun toString(): String = "group row \"$name\" at $position (expanded: $expanded, checked: $checked)"
}

fun circlesNumbers(circles: List<CircleRow>): String = when(circles.size) {
        1 -> "${circles[0].id}"
        2, 3 -> circles.take(3).joinToString { it.id.toString() }
        else -> circles.take(2).joinToString { it.id.toString() } + ", ..., " +
            circles.last().id.toString() + " [${circles.size}]"
    }

inline fun <E, K> List<E>.consecutiveGroupBy(selector: (E) -> K): List<Pair<K, List<E>>> {
    val lists: MutableList<Pair<K, List<E>>> = mutableListOf()
    var k: K? = null
    var list: MutableList<E> = mutableListOf()
    for (e in this) {
        val newK = selector(e)
        if (k == newK) {
            list.add(e)
        } else {
            k?.let { lists.add(it to list) }
            k = newK
            list = mutableListOf(e)
        }
    }
    if (list.isNotEmpty())
        lists.add(k!! to list)
    return lists
}

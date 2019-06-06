package com.pierbezuhoff.dodeca.ui.dodeca

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
import com.pierbezuhoff.dodeca.R
import com.pierbezuhoff.dodeca.data.CircleFigure
import com.pierbezuhoff.dodeca.data.CircleGroup
import com.pierbezuhoff.dodeca.utils.Maybe
import com.pierbezuhoff.dodeca.utils.None
import com.pierbezuhoff.dodeca.utils.justIf
import com.rarepebble.colorpicker.ColorPickerView
import kotlinx.android.synthetic.main.choose_color_dialog.view.*
import kotlinx.android.synthetic.main.choose_color_row.view.*
import kotlinx.android.synthetic.main.edit_circle.view.*
import org.jetbrains.anko.AlertBuilder
import org.jetbrains.anko.alert
import org.jetbrains.anko.customView
import org.jetbrains.anko.displayMetrics
import org.jetbrains.anko.include
import org.jetbrains.anko.layoutInflater
import kotlin.math.roundToInt
import kotlin.properties.Delegates

// TODO: refactor
class ChooseColorDialog(
    private val context: Context,
    private val chooseColorListener: ChooseColorListener,
    private val circleGroup: CircleGroup
) {
    private lateinit var rowAdapter: CircleAdapter

    interface ChooseColorListener {
        fun onChooseColorClosed()
    }

    fun build(): Dialog {
        val builder = AlertDialog.Builder(context)
        val inflater = context.layoutInflater
        val layout = inflater.inflate(R.layout.choose_color_dialog, null)
        builder.setView(layout)
        val manager = LinearLayoutManager(context)
        rowAdapter = CircleAdapter(context, circleGroup)
        val height: Int = context.displayMetrics.heightPixels
        val recyclerView = layout.circle_rows.apply {
            layoutManager = manager
            adapter = rowAdapter
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (height * 0.5).roundToInt()
            )
        }
        layout.all_circles_checkbox.setOnCheckedChangeListener { _, checked -> rowAdapter.onCheckAll(checked) }
        layout.sort_by_color.setOnCheckedChangeListener { _, checked -> rowAdapter.onSortByColor(checked) }
        layout.sort_by_name.setOnCheckedChangeListener { _, checked -> rowAdapter.onSortByName(checked) }
        setOf(layout.all_circles_checkbox, layout.sort_by_color, layout.sort_by_name).forEach {
            TooltipCompat.setTooltipText(it, it.contentDescription)
        }
        val dialog = builder.apply {
            setMessage(R.string.choose_circle_dialog_message)
            setPositiveButton(R.string.choose_circle_dialog_edit) { _, _ -> Unit } // will be setToIn later
            setNegativeButton(R.string.choose_circle_dialog_cancel) { _, _ -> chooseColorListener.onChooseColorClosed() }
        }.create()
        dialog.setOnDismissListener { chooseColorListener.onChooseColorClosed() }
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


internal typealias OnApply = (
    shown: Maybe<Boolean>,
    color: Maybe<Int>,
    fill: Maybe<Boolean>,
    borderColor: Maybe<Int?>
) -> Unit

class CircleAdapter(
    private val context: Context,
    private val circleGroup: CircleGroup
) : RecyclerView.Adapter<CircleAdapter.ViewHolder>() {
    class ViewHolder(val row: View) : RecyclerView.ViewHolder(row)

    // TODO: show invisible at the end
    private val circleRows: List<CircleRow> =
        circleGroup.figures
            .mapIndexed { i, figure -> CircleRow(figure, i) }
            .filter { it.figure.dynamic } // show only circles with [rule]
            .sortedBy { it.figure.color }
            .sortedByDescending { it.visible }
    private var rows: MutableList<Row> = factorByEquivalence(circleRows).toMutableList()

    // store rows with checked checkboxes, they may be collapsed though
    val checkedRows: MutableSet<Row> = mutableSetOf()
    private var binding: Boolean = false
    var showInvisible: Boolean = true

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

    private inline fun factorByEquivalence(circles: List<CircleRow>): List<Row> =
        circles.groupBy { it.equivalence } // hope we don't lose CircleRow::id order
            .values
            .mapIndexed { i, list ->
                if (list.size == 1)
                    list[0].apply { position = i }
                else
                    CircleGroupRow(
                        list.toSet().apply { forEach { it.position = null } },
                        position = i,
                        checked = list.all { it.checked }
                    )
            }
            .sortedByDescending { it.visible }

    private fun reassignPositions() {
        rows.forEachIndexed { i, row -> row.position = i }
    }

    private fun CircleRow.persistApply(
        visible: Boolean? = null,
        color: Int? = null,
        fill: Boolean? = null,
        borderColor: Maybe<Int?> = None
    ) {
        figure = figure.copy(
            newShown = visible,
            newColor = color, newFill = fill, newBorderColor = borderColor)
        persist()
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
            is CircleRow -> onBindCircleVH(holder, row)
            is CircleGroupRow -> onBindCircleGroupVH(holder, row)
        }
        binding = false
    }

    private fun onBindCircleVH(holder: ViewHolder, row: CircleRow) {
        val figure = row.figure
        with(holder.row) {
            // increase left margin
            (circle_layout.layoutParams as ViewGroup.MarginLayoutParams).apply {
                leftMargin = 8 // 40
            }
            circle_name.text = "${row.id}"
            circle_image.setImageDrawable(circleImageFor(figure.equivalence))
            circle_checkbox.apply {
                setOnCheckedChangeListener { _, checked -> if (checked) row.check() else row.uncheck() }
                isChecked = row.checked
            }
            circle_layout.setOnClickListener { editCircle(row) }
            circle_visibility.apply {
                setOnCheckedChangeListener { _, _ -> }
                isChecked = row.visible
                setOnCheckedChangeListener { _, checked ->
                    row.persistApply(visible = checked)
                }
            }
            expand_group.apply {
                visibility = View.GONE
                setOnCheckedChangeListener { _, _ -> }
                isChecked = false
            }
        }
    }

    private fun onBindCircleGroupVH(holder: ViewHolder, row: CircleGroupRow) {
        with(holder.row) {
            (circle_layout.layoutParams as ViewGroup.MarginLayoutParams).apply {
                leftMargin = 8
            }
            circle_name.text = row.name // should change whenever .circles changes
            circle_image.setImageDrawable(circleImageFor(row.equivalence))
            circle_checkbox.apply {
                setOnCheckedChangeListener { _, checked -> if (!binding) checkGroup(row, checked) }
                isChecked = row.checked
            }
            circle_layout.setOnClickListener {
                editCirclesDialog(row.circles) { (shown), (color), (fill), borderColor ->
                    row.circles.forEach { it.persistApply(shown, color, fill, borderColor) }
                    row.equivalence = row.blueprint.equivalence
                    notifyDataSetChanged()
                }.show()
            }
            circle_visibility.apply {
                setOnCheckedChangeListener { _, _ -> }
                isChecked = row.visible
                setOnCheckedChangeListener { _, checked ->
                    row.equivalence = row.equivalence.copy(visible = checked)
                    row.circles.forEach { it.persistApply(visible = checked) }
                    if (row.expanded && !binding)
                        notifyDataSetChanged()
                }
            }
            expand_group.apply {
                visibility = View.VISIBLE
                setOnCheckedChangeListener { _, checked -> if (!binding) expandOrCollapseGroup(checked, row) }
                if (isChecked != row.expanded)
                    isChecked = row.expanded
            }
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
        if (expand) {
            expandGroup(row)
        } else {
            collapseGroup(row)
        }
        notifyDataSetChanged()
    }

    private fun expandGroup(row: CircleGroupRow) {
        rows.addAll(1 + row.position!!, row.circles)
        row.expanded = true
        reassignPositions()
        // notifyDataSetChanged() should be called afterwards
    }

    private fun collapseGroup(row: CircleGroupRow) {
        val newCircles = circleRows.filter { it.shown && row.equivalent(it) }
        when(newCircles.size) {
            0, 1 -> row.remove()
            else -> {
                row.circles = newCircles.toSet()
                rows.removeAll(newCircles)
                newCircles.forEach { it.position = null }
            }
        }
        row.expanded = false
        reassignPositions()
        // notifyDataSetChanged() should be called afterwards
    }

    private fun circleImageFor(equivalence: Equivalence): LayerDrawable {
        val (shown, color, fill, borderColor) = equivalence
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

    private fun editCircle(row: CircleRow) {
        editCircleDialog(
            row.figure,
            context.getString(R.string.edit_circle_dialog_message_single, row.id.toString())
        ) { (shown), (color), (fill), borderColor ->
            row.persistApply(shown, color, fill, borderColor)
            notifyDataSetChanged()
        }.show()
    }

    private inline fun editCircleDialog(
        figure: CircleFigure,
        message: String,
        crossinline onApply: OnApply
    ): AlertBuilder<*> {
        var shownChanged = false
        var shown: Boolean by Delegates.observable(figure.show) { _, _, _ -> shownChanged = true }
        var colorChanged = false
        var color: Int by Delegates.observable(figure.color) { _, _, _ -> colorChanged = true }
        var fillChanged = false
        var fill: Boolean by Delegates.observable(figure.fill) { _, _, _ -> fillChanged = true }
        var borderColorChanged = false
        var borderColor: Int? by Delegates.observable(figure.borderColor) { _, _, _ -> borderColorChanged = true }
        val builder = context.alert(message) {
            customView {
                include<LinearLayout>(R.layout.edit_circle).also { layout ->
                    val shownButton: Switch = layout.circle_show
                    val colorButton: ImageButton = layout.circle_color
                    val fillSwitch: Switch = layout.circle_fill
                    val borderColorButton: ImageButton = layout.circle_border_color
                    val borderColorSwitch: Switch = layout.circle_has_border_color
                    shownButton.apply {
                        isChecked = shown
                        setOnCheckedChangeListener { _, checked -> shown = checked }
                    }
                    // maybe: disable borderColor if not fill
                    colorButton.apply {
                        setColorFilter(color)
                        setOnClickListener {
                            colorPickerDialog(color) { newColor ->
                                color = newColor
                                colorButton.setColorFilter(newColor)
                                if (borderColor == null)
                                    borderColorButton.setColorFilter(newColor)
                            }.show()
                        }
                    }
                    fillSwitch.apply {
                        isChecked = fill
                        setOnCheckedChangeListener { _, checked ->
                            fill = checked
                            borderColorSwitch.isEnabled = checked
                        }
                    }
                    borderColorSwitch.apply {
                        isEnabled = fill
                        isChecked = borderColor != null
                        setOnCheckedChangeListener { _, checked ->
                            if (!checked && borderColor != null) {
                                borderColor = null
                                borderColorButton.setColorFilter(color)
                            } else if (checked) {
                                borderColor = borderColor ?: color
                                borderColorButton.setColorFilter(borderColor!!)
                            }
                        }
                    }
                    borderColorButton.apply {
                        setColorFilter(borderColor ?: color)
                        setOnClickListener {
                            colorPickerDialog(borderColor ?: color) { newColor ->
                                borderColorSwitch.apply {
                                    if (!isChecked && fill)
                                        isChecked = true // NOTE: may change borderColor
                                }
                                if (!fill) {
                                    color = newColor
                                    colorButton.setColorFilter(newColor)
                                } else {
                                    borderColor = newColor
                                }
                                borderColorButton.setColorFilter(newColor)
                            }.show()
                        }
                    }
                }
            }
            positiveButton(R.string.edit_circle_dialog_apply) { onApply(
                shown justIf shownChanged,
                color justIf colorChanged,
                fill justIf fillChanged,
                borderColor justIf borderColorChanged
            ) }
            negativeButton(R.string.edit_circle_dialog_cancel) { }
        }
        return builder
    }

    private inline fun colorPickerDialog(
        color: Int,
        crossinline onChosen: (newColor: Int) -> Unit
    ): AlertBuilder<DialogInterface> =
        context.alert(R.string.color_picker_dialog_message) {
            // ISSUE: in landscape: ok/cancel are off screen
            val colorPicker = ColorPickerView(context)
            colorPicker.color = color
            colorPicker.showAlpha(false)
            colorPicker.showHex(false) // focusing => pop up keyboard
            customView {
                addView(colorPicker, ViewGroup.LayoutParams.MATCH_PARENT.let { ViewGroup.LayoutParams(it, it) })
            }
            positiveButton(R.string.color_picker_dialog_ok) { onChosen(colorPicker.color) }
            negativeButton(R.string.color_picker_dialog_cancel) { }
        }

    private inline fun editCirclesDialog(
        circles: Collection<CircleRow>, // non-empty!
        crossinline onApply: OnApply = { _, _, _, _ -> }
    ): AlertBuilder<DialogInterface> {
        val blueprint = circles.take(1)[0]
        val message = when(circles.size) {
            1 -> context.getString(R.string.edit_circle_dialog_message_single, blueprint.id.toString())
            else -> context.getString(
                R.string.edit_circle_dialog_message_plural,
                circlesNumbers(circles)
            )
        }
        return editCircleDialog(blueprint.figure, message, onApply)
    }

    fun editCheckedCircles() {
        val circles = checkedRows.filterIsInstance<CircleRow>()
        if (circles.isNotEmpty())
            editCirclesDialog(circles) { (shown), (color), (fill), borderColor ->
                circles.forEach { it.persistApply(shown, color, fill, borderColor) }
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
                        list.toSet().apply { forEach { it.position = null } },
                        checked = list.all { it.checked }
                    )
            }
            .sortedByDescending { it.visible }
            .toMutableList()
        reassignPositions()
        notifyDataSetChanged()
    }

    companion object {
        const val TAG: String = "CircleAdapter"
    }
}

internal data class Equivalence(val visible: Boolean, val color: Int, val fill: Boolean, val borderColor: Int?)

internal val CircleFigure.equivalence get() = Equivalence(
    show,
    color,
    fill,
    borderColor
)

sealed class Row(
    var position: Int? = null, // position in adapter
    var checked: Boolean = false
) {
    val shown: Boolean get() = position != null
    internal abstract val equivalence: Equivalence
    val visible: Boolean get() = equivalence.visible
    fun equivalent(other: Row): Boolean = equivalence == other.equivalence
}

class CircleRow(
    var figure: CircleFigure,
    val id: Int, // number in .ddu file
    position: Int? = null,
    checked: Boolean = false
) : Row(position, checked) {
    override val equivalence: Equivalence get() = figure.equivalence
    override fun toString(): String = "row \"$id\" at $position (checked: $checked)"
}

class CircleGroupRow(
    // hope, order is preserved
    var circles: Set<CircleRow>, // size >= 2
    var expanded: Boolean = false,
    position: Int? = null,
    checked: Boolean = false
) : Row(position, checked) {
    override var equivalence: Equivalence = blueprint.equivalence
    val size: Int get() = circles.size
    val blueprint: CircleRow get() = circles.take(1)[0]
    val name: String get() = circlesNumbers(circles)
    override fun toString(): String = "group row \"$name\" at $position (expanded: $expanded, checked: $checked)"
}

internal fun circlesNumbers(circles: Collection<CircleRow>): String = when(circles.size) {
        1 -> "${circles.take(1)[0].id}"
        2, 3 -> circles.take(3).joinToString { it.id.toString() }
        else -> circles.take(2).joinToString { it.id.toString() } + ", ..., " +
            circles.last().id.toString() + " [${circles.size}]"
    }

inline fun <E, K> Iterable<E>.consecutiveGroupBy(selector: (E) -> K): List<Pair<K, List<E>>> {
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

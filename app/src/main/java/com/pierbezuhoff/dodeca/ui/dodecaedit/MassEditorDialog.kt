package com.pierbezuhoff.dodeca.ui.dodecaedit

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Bitmap
import android.graphics.drawable.LayerDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Switch
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.TooltipCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.core.graphics.ColorUtils
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pierbezuhoff.dodeca.R
import com.pierbezuhoff.dodeca.data.CircleFigure
import com.pierbezuhoff.dodeca.data.Ddu
import com.pierbezuhoff.dodeca.data.circlegroup.CircleGroup
import com.pierbezuhoff.dodeca.databinding.MassEditorDialogBinding
import com.pierbezuhoff.dodeca.databinding.MassEditorRowBinding
import com.pierbezuhoff.dodeca.utils.Maybe
import com.pierbezuhoff.dodeca.utils.None
import com.pierbezuhoff.dodeca.utils.consecutiveGroupBy
import com.pierbezuhoff.dodeca.utils.justIf
import com.rarepebble.colorpicker.ColorPickerView
import org.jetbrains.anko.AlertBuilder
import org.jetbrains.anko.alert
import org.jetbrains.anko.customView
import org.jetbrains.anko.displayMetrics
import org.jetbrains.anko.include
import org.jetbrains.anko.layoutInflater
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.properties.Delegates


internal typealias ColorInt = Int
internal typealias OnApply = (
    shown: Maybe<Boolean>,
    color: Maybe<ColorInt>,
    fill: Maybe<Boolean>,
    borderColor: Maybe<ColorInt?>,
    rule: Maybe<String>,
    texture: Maybe<Bitmap?>
) -> Unit


class MassEditorDialog(
    private val context: Context,
    private val massEditorListener: MassEditorListener,
    private val ddu: Ddu,
    private val circleGroup: CircleGroup
) {
    private lateinit var rowAdapter: CircleAdapter

    interface MassEditorListener {
        fun onMassEditorClosed()
        /* NOT a terminal operation, the dialog is still open */
        fun onMassEditorBackgroundChanged()
        fun onMassEditorCirclesSelected(circleIndices: List<Int>)
    }

    fun build(): Dialog {
        val builder = AlertDialog.Builder(context)
        val inflater = context.layoutInflater
//        val layout: View = inflater.inflate(R.layout.mass_editor_dialog, null)
        val binding: MassEditorDialogBinding =
            MassEditorDialogBinding.inflate(inflater)
        val layout = binding.root
        builder.setView(layout)
        val manager = LinearLayoutManager(context)
        rowAdapter = CircleAdapter(context, ddu, circleGroup, massEditorListener)
        val height: Int = context.displayMetrics.heightPixels
        with(binding) {
            circleRows.apply {
                layoutManager = manager
                adapter = rowAdapter
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (height * 0.5).roundToInt()
                )
            }
            allCirclesCheckbox.setOnCheckedChangeListener { _, checked ->
                rowAdapter.onCheckAll(checked)
            }
            sortByColor.setOnCheckedChangeListener { _, checked ->
                rowAdapter.onSortByColor(checked)
            }
            sortByName.setOnCheckedChangeListener { _, checked ->
                rowAdapter.onSortByName(checked)
            }
            if (CircleAdapter.HIDE_RULES)
                sortByRule.visibility = View.GONE
            else
                binding.sortByRule.setOnCheckedChangeListener { _, checked ->
                    rowAdapter.onSortByRule(checked)
                }
            setOf(
                allCirclesCheckbox, sortByColor, sortByName, sortByRule
            ).forEach {
                TooltipCompat.setTooltipText(it, it.contentDescription)
            }
        }
        val dialog = builder.apply {
            setMessage(R.string.mass_editor_dialog_message)
            setPositiveButton(R.string.mass_editor_dialog_edit) { _, _ -> } // will be set later
            setNegativeButton(R.string.mass_editor_dialog_cancel) { _, _ -> massEditorListener.onMassEditorClosed() }
            setNeutralButton(R.string.mass_editor_dialog_recolor_everything) { _, _ -> } // will be set later
        }.create()
        dialog.setOnDismissListener { massEditorListener.onMassEditorClosed() }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (rowAdapter.checkedRows.isNotEmpty())
                    rowAdapter.editCheckedCircles()
                else
                    dialog.dismiss()
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                //chooseColorListener.onMassEditorCirclesSelected(rowAdapter.getCheckedCircleIndices())
                val selectedIxs = rowAdapter.getCheckedCircleIndices()
                val targetIxs =
                    if (selectedIxs.isEmpty())
                        (0 until circleGroup.figures.size).filter { circleGroup[it].show }
                    else selectedIxs.filter { circleGroup[it].show }
                val avHSL = averageHSL(targetIxs.map { circleGroup[it].color })
                val av = ColorUtils.HSLToColor(avHSL)
                colorPickerDialog(context, av) { newColor ->
                    // HSL-shift all colors of the selected circles by (newColor - av)
                    val hsl = FloatArray(3)
                    ColorUtils.colorToHSL(newColor, hsl)
                    val dH = hsl[0] - avHSL[0]
                    val dS = hsl[1] - avHSL[1]
                    val dL = hsl[2] - avHSL[2]
                    targetIxs.forEach { i ->
                        val figure = circleGroup[i]
                        ColorUtils.colorToHSL(figure.color, hsl)
                        hsl[0] = (hsl[0] + dH).mod(360f)
                        hsl[1] = min(max(0f, hsl[1] + dS), 1f)
                        hsl[2] = min(max(0f, hsl[2] + dL), 1f)
                        val newC = ColorUtils.HSLToColor(hsl)
                        val newBC = figure.borderColor?.let { bc ->
                            ColorUtils.colorToHSL(bc, hsl)
                            hsl[0] = (hsl[0] + dH).mod(360f)
                            hsl[1] = min(max(0f, hsl[1] + dS), 1f)
                            hsl[2] = min(max(0f, hsl[2] + dL), 1f)
                            ColorUtils.HSLToColor(hsl)
                        }
                        circleGroup[i] = circleGroup[i].copy(newColor = newC, newBorderColor = Maybe(newBC))
                    }
                    dialog.dismiss()
                }.show()
            }
        }
        return dialog
    }
}


@Suppress("NOTHING_TO_INLINE")
class CircleAdapter(
    private val context: Context,
    private val ddu: Ddu,
    private val circleGroup: CircleGroup,
    private val massEditorListener: MassEditorDialog.MassEditorListener
) : RecyclerView.Adapter<CircleAdapter.ViewHolder>() {
    class ViewHolder(val binding: MassEditorRowBinding) : RecyclerView.ViewHolder(binding.root)

    private val bgRow = BackgroundRow(ddu.backgroundColor)
    private val circleRows: List<CircleRow> =
        circleGroup.figures
            .mapIndexed { i, figure -> CircleRow(figure, i) }
            .sortedBy { it.figure.color }
            .sortedByDescending { it.visible }
    private var rows: MutableList<Row> =
        (listOf(bgRow) + factorByEquivalence(circleRows)).toMutableList()

    // store rows with checked checkboxes, they might be collapsed though
    val checkedRows: MutableSet<Row> = mutableSetOf()
    private var bindingInProgress: Boolean = false

    private inline fun CircleRow.persist() { circleGroup[id] = this.figure }
    private inline fun Row.remove() {
        position?.let {
            rows.removeAt(it)
            position = null
        }
        // NOTE: reassigning positions should be done later
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

    fun getCheckedCircleIndices(): List<Int> =
        checkedRows.flatMap { row ->
            when (row) {
                is CircleRow ->
                    listOf(row.id)
                is CircleGroupRow ->
                    row.circles.map { it.id }.toList()
                else -> emptyList()
            }
        }

    private inline fun factorByEquivalence(circles: List<CircleRow>): List<Row> =
        circles
            .groupBy { it.equivalence }
            .values // hopefully initial ordering is more-or-less preserved
            .mapIndexed { i, list ->
                if (list.size == 1)
                    list[0].apply { position = i }
                else
                    CircleGroupRow(
                        list.toSet().onEach { it.position = null },
                        position = i,
                        checked = list.all { it.checked }
                    )
            }
            .sortedByDescending { it.visible }

    private inline fun factorByRule(circles: List<CircleRow>): List<Row> =
        circles
            .consecutiveGroupBy { it.figure.rule ?: "" } // enough since it's already sorted by rule
            .mapIndexed { i, kAndList ->
                val (_, list) = kAndList
                if (list.size == 1)
                    list[0].apply { position = i }
                else
                    CircleGroupRow(
                        list.toSet().onEach { it.position = null },
                        position = i,
                        checked = list.all { it.checked }
                    )
            }

    private fun reassignPositions() {
        rows.forEachIndexed { i, row -> row.position = i }
    }

    private fun CircleRow.persistApply(
        visible: Boolean? = null,
        color: ColorInt? = null,
        fill: Boolean? = null,
        borderColor: Maybe<ColorInt?> = None,
        rule: String? = null,
        texture: Bitmap? = null
    ) {
        val ruleWithN =
            if (rule == null || rule.startsWith('n') || visible ?: figure.show)
                rule
            else "n$rule"
        figure = figure.copy(
            newShown = visible,
            newColor = color,
            newFill = fill,
            newRule = ruleWithN,
            newBorderColor = borderColor
        )
        persist()
        texture?.let {
            circleGroup.setTexture(id, texture)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = MassEditorRowBinding.inflate(
            LayoutInflater.from(parent.context),
            parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        bindingInProgress = true
        val row = rows[position]
        row.position = position
        when (row) {
            is BackgroundRow -> onBindBackgroundVH(holder, row)
            is CircleRow -> onBindCircleVH(holder, row)
            is CircleGroupRow -> onBindCircleGroupVH(holder, row)
        }
        bindingInProgress = false
    }

    private fun onBindBackgroundVH(holder: ViewHolder, row: BackgroundRow) {
        with(holder.binding) {
            // increase left margin
            (circleLayout.layoutParams as ViewGroup.MarginLayoutParams).apply {
                leftMargin = ROW_LEFT_MARGIN
            }
            circleName.text = root.resources.getString(R.string.background_color)
            circleRule.visibility = View.GONE
            val bgIcon = ContextCompat.getDrawable(context, R.drawable.background)
                as LayerDrawable
            bgIcon.mutate()
            val bg = bgIcon.getDrawable(0)
            bg.colorFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                row.color,
                BlendModeCompat.SRC_ATOP
            )
            circleImage.setImageDrawable(bgIcon)
            circleCheckbox.visibility = View.INVISIBLE
            listOf(circleImage, circleLayout).forEach {
                it.setOnClickListener {
                    colorPickerDialog(context, row.color) { newColor ->
                        if (row.color != newColor) {
                            row.color = newColor
                            ddu.backgroundColor = newColor
                            notifyItemChanged(0)
                            massEditorListener.onMassEditorBackgroundChanged()
                        }
                    }.show()
                }
            }
            circleVisibility.visibility = View.INVISIBLE
            expandGroup.apply {
                visibility = View.GONE
                setOnCheckedChangeListener { _, _ -> }
                isChecked = false
            }
        }
    }

    private fun onBindCircleVH(holder: ViewHolder, row: CircleRow) {
        val figure = row.figure
        with(holder.binding) {
            // increase left margin
            (circleLayout.layoutParams as ViewGroup.MarginLayoutParams).apply {
                leftMargin = ROW_LEFT_MARGIN
            }
            circleName.text = "${row.id}"
            val rule = figure.rule?.trimStart('n')
            if (HIDE_RULES)
                circleRule.visibility = View.GONE
            else {
                circleRule.text = rule?.ifBlank { "-" } ?: "-"
            }
            circleImage.setImageDrawable(circleImageFor(figure.equivalence))
            circleCheckbox.apply {
                visibility = View.VISIBLE
                setOnCheckedChangeListener { _, checked -> if (checked) row.check() else row.uncheck() }
                isChecked = row.checked
            }
            listOf(circleImage, circleLayout).forEach {
                it.setOnClickListener { editCircle(row) }
            }
            circleVisibility.apply {
                visibility = View.VISIBLE
                setOnCheckedChangeListener { _, _ -> }
                isChecked = row.visible
                setOnCheckedChangeListener { _, checked ->
                    row.persistApply(visible = checked)
                }
            }
            expandGroup.apply {
                visibility = View.GONE
                setOnCheckedChangeListener { _, _ -> }
                isChecked = false
            }
        }
    }

    private fun onBindCircleGroupVH(holder: ViewHolder, row: CircleGroupRow) {
        with(holder.binding) {
            (circleLayout.layoutParams as ViewGroup.MarginLayoutParams).apply {
                leftMargin = ROW_LEFT_MARGIN
            }
            circleName.text = row.name // should change whenever .circles changes
            if (HIDE_RULES)
                circleRule.visibility = View.GONE
            else {
                val firstRule = row.circles.first().figure.rule ?: ""
                circleRule.text = if (row.circles.all { (it.figure.rule ?: "") == firstRule }) {
                    firstRule.trimStart('n').ifBlank { "-" }
                } else {
                    "*"
                }
            }
            circleImage.setImageDrawable(circleImageFor(row.equivalence))
            circleCheckbox.apply {
                visibility = View.VISIBLE
                setOnCheckedChangeListener { _, checked -> if (!bindingInProgress) checkGroup(row, checked) }
                isChecked = row.checked
            }
            listOf(circleImage, circleLayout).forEach {
                it.setOnClickListener {
                    editCirclesDialog(row.circles) { (shown), (color), (fill), borderColor, (rule), _ ->
                        row.circles.forEach { it.persistApply(shown, color, fill, borderColor, rule) }
                        row.equivalence = row.blueprint.equivalence
                        notifyDataSetChanged()
                    }.show()
                }
            }
            circleVisibility.apply {
                visibility = View.VISIBLE
                setOnCheckedChangeListener { _, _ -> }
                isChecked = row.visible
                setOnCheckedChangeListener { _, checked ->
                    row.equivalence = row.equivalence.copy(visible = checked)
                    row.circles.forEach { it.persistApply(visible = checked) }
                    if (row.expanded && !bindingInProgress)
                        notifyDataSetChanged()
                }
            }
            expandGroup.apply {
                visibility = View.VISIBLE
                setOnCheckedChangeListener { _, checked -> if (!bindingInProgress) expandOrCollapseGroup(checked, row) }
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
            inner.colorFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                color, BlendModeCompat.SRC_ATOP
            )
            border.colorFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                borderColor ?: color, BlendModeCompat.SRC_ATOP
            )
        } else {
            inner.alpha = 0
            border.colorFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                color, BlendModeCompat.SRC_ATOP
            )
        }
        return circleImage
    }

    private fun editCircle(row: CircleRow) {
        editCircleDialog(
            row.figure,
            context.getString(R.string.edit_circle_dialog_message_single, row.id.toString())
        ) { (shown), (color), (fill), borderColor, (rule), (texture) ->
            row.persistApply(shown, color, fill, borderColor, rule, texture)
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
        var color: ColorInt by Delegates.observable(figure.color) { _, _, _ -> colorChanged = true }
        var fillChanged = false
        var fill: Boolean by Delegates.observable(figure.fill) { _, _, _ -> fillChanged = true }
        var borderColorChanged = false
        var borderColor: ColorInt? by Delegates.observable(figure.borderColor) { _, _, _ -> borderColorChanged = true }
        var ruleChanged = false
        var rule: String by Delegates.observable(
            figure.rule?.trimStart('n') ?: ""
        ) { _, _, _ -> ruleChanged = true }
        var textureChanged = false
        // TODO: get texture from the CircleGroup instead
        var texture: Bitmap? by Delegates.observable(null) { _, _, _ -> textureChanged = true }
        return context.alert(message) {
            customView {
                include<LinearLayout>(R.layout.edit_circle).also { layout ->
                    val shownButton: Switch = layout.findViewById(R.id.circle_show)
                    val colorButton: ImageButton = layout.findViewById(R.id.circle_color)
                    val fillSwitch: Switch = layout.findViewById(R.id.circle_fill)
                    val borderColorButton: ImageButton = layout.findViewById(R.id.circle_border_color)
                    val borderColorSwitch: Switch = layout.findViewById(R.id.circle_has_border_color)
                    val ruleField: EditText = layout.findViewById(R.id.edit_circle_rule)
//                    val textureButton: ImageButton = layout.circle_texture
//                    val useTextureSwitch: Switch = layout.circle_use_texture
                    shownButton.apply {
                        isChecked = shown
                        setOnCheckedChangeListener { _, checked -> shown = checked }
                    }
                    colorButton.apply {
                        setColorFilter(color)
                        setOnClickListener {
                            colorPickerDialog(context, color) { newColor ->
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
                            if (!checked) {
                                borderColor = null
                                borderColorSwitch.isChecked = false
                                borderColorButton.setColorFilter(color)
                            }
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
                            colorPickerDialog(context, borderColor ?: color) { newColor ->
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
                    ruleField.apply {
                        setText(rule)
                        doOnTextChanged { newRule, _, _, _ ->
                            newRule?.let {
                                rule = it.toString()
                            }
                        }
                    }
//                    useTextureSwitch.apply {
//                        isEnabled = texture != null
//                        isChecked = texture != null
//                        setOnCheckedChangeListener { _, checked ->
//                            if (!checked) {
//                                isEnabled = false
//                                texture = null
//                            }
//                        }
//                    }
//                    textureButton.apply {
//                        setOnClickListener {
//                            TODO()
//                            // load texture
//                            // when loaded
//                            useTextureSwitch.isEnabled = true
//                            useTextureSwitch.isChecked = true
//                        }
//                    }
                }
            }
            positiveButton(R.string.edit_circle_dialog_apply) { onApply(
                shown justIf shownChanged,
                color justIf colorChanged,
                fill justIf fillChanged,
                borderColor justIf borderColorChanged,
                rule justIf ruleChanged,
                texture justIf textureChanged
            ) }
            negativeButton(R.string.edit_circle_dialog_cancel) { }
        }
    }

    private inline fun editCirclesDialog(
        circles: Collection<CircleRow>, // non-empty!
        crossinline onApply: OnApply
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
            editCirclesDialog(circles) { (shown), (color), (fill), borderColor, (rule), (texture) ->
                circles.forEach { it.persistApply(shown, color, fill, borderColor, rule, texture) }
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
        if (!bindingInProgress) {
            val cRows = rows.drop(1)
            if (checked) {
                checkedRows.addAll(circleRows)
                checkedRows.addAll(cRows)
            } else {
                checkedRows.clear()
            }
            cRows.forEach { it.checked = checked }
            circleRows.forEach { it.checked = checked }
            notifyDataSetChanged()
        }
    }

    fun onSortByColor(ascending: Boolean) {
        val unexpandedGroupRows = rows.filterIsInstance<CircleGroupRow>().filter { !it.expanded }
        // deliberately not lazy stream: collapseGroup mutates rows
        unexpandedGroupRows.forEach { expandGroup(it) }
        val _rows: MutableList<CircleRow> = rows.filterIsInstance<CircleRow>().toMutableList()
        if (ascending)
            _rows.sortBy { it.figure.color }
        else
            _rows.sortByDescending { it.figure.color }
        rows = (listOf(bgRow) + factorByEquivalence(_rows)).toMutableList()
        reassignPositions()
        notifyDataSetChanged()
    }

    fun onSortByName(ascending: Boolean) {
        val unexpandedGroupRows = rows.filterIsInstance<CircleGroupRow>().filter { !it.expanded }
        unexpandedGroupRows.forEach { expandGroup(it) }
        val _rows = rows.filterIsInstance<CircleRow>().toMutableList()
        if (ascending)
            _rows.sortBy { it.id }
        else
            _rows.sortByDescending { it.id }
        // now we have the right order, but without groups
        // we will group consequential with the same equivalence
        rows = (listOf(bgRow) + _rows
            .consecutiveGroupBy { it.equivalence }
            .map { (_, list) ->
                if (list.size == 1)
                    list[0]
                else
                    CircleGroupRow(
                        list.toSet().onEach { it.position = null },
                        checked = list.all { it.checked }
                    )
            }
            ).toMutableList()
        reassignPositions()
        notifyDataSetChanged()
    }

    fun onSortByRule(ascending: Boolean) {
        // TODO: sort by ranks & rules
        val unexpandedGroupRows = rows.filterIsInstance<CircleGroupRow>().filter { !it.expanded }
        // deliberately not lazy stream: collapseGroup mutates rows
        unexpandedGroupRows.forEach { expandGroup(it) }
        val _rows: MutableList<CircleRow> = rows.filterIsInstance<CircleRow>().toMutableList()
        _rows.sortBy { it.figure.rule ?: "" }
        if (ascending)
            _rows.sortBy { it.figure.rule?.length ?: 0 }
        else
            _rows.sortByDescending { it.figure.rule?.length ?: 0 }
        rows = (listOf(bgRow) + factorByRule(_rows)).toMutableList()
        reassignPositions()
        notifyDataSetChanged()
    }

    companion object {
        const val TAG: String = "CircleAdapter"
        private const val ROW_LEFT_MARGIN = 8 //40
        internal const val HIDE_RULES = false
    }
}

private fun colorPickerDialog(
    context: Context,
    color: ColorInt,
    onChosen: (newColor: ColorInt) -> Unit
): AlertBuilder<DialogInterface> =
    context.alert(R.string.color_picker_dialog_message) {
        val colorPicker = ColorPickerView(context)
        colorPicker.color = color
        colorPicker.showAlpha(false)
        colorPicker.showHex(false) // focusing => pops up keyboard
        customView {
            addView(colorPicker, ViewGroup.LayoutParams.MATCH_PARENT.let { ViewGroup.LayoutParams(it, it) })
        }
        // BUG: in landscape: ok/cancel are off screen
        positiveButton(R.string.color_picker_dialog_ok) { onChosen(colorPicker.color) }
        negativeButton(R.string.color_picker_dialog_cancel) { }
        onCancelled { onChosen(colorPicker.color) }
    }


internal data class Equivalence(val visible: Boolean, val color: ColorInt, val fill: Boolean, val borderColor: ColorInt?)

internal val CircleFigure.equivalence get() = Equivalence(
    show,
    color,
    fill,
    borderColor,
)

sealed class Row(
    var position: Int? = null, // position in adapter
    var checked: Boolean = false
) {
    val shown: Boolean get() = position != null
    internal abstract val equivalence: Equivalence
    val visible: Boolean get() = equivalence.visible
    open fun equivalent(other: Row): Boolean = equivalence == other.equivalence
}

class BackgroundRow(var color: ColorInt) : Row(null, false) {
    // rule = "0" is never used so we are all good
    override val equivalence get() = Equivalence(true, color, true, 0)
    override fun equivalent(other: Row) =
        other is BackgroundRow && color == other.color
}

class CircleRow(
    var figure: CircleFigure,
    val id: Int, // position of the circle in the .ddu file
    position: Int? = null,
    checked: Boolean = false
) : Row(position, checked) {
    override val equivalence: Equivalence get() = figure.equivalence
    override fun toString(): String = "row \"$id\" at $position (checked: $checked)"
}

class CircleGroupRow(
    // hope the order is preserved
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
        1 -> "${circles.single().id}"
        2, 3, 4 -> circles.joinToString { it.id.toString() }
        else -> circles.take(2).joinToString { it.id.toString() } + ".." +
            circles.last().id.toString() + " [${circles.size}]"
    }

internal fun averageHSL(colors: Iterable<ColorInt>): FloatArray {
    var h = 0f
    var s = 0f
    var l = 0f
    var nColors = 0
    val hsl = FloatArray(3)
    for (color in colors) {
        nColors += 1
        ColorUtils.colorToHSL(color, hsl)
        h += hsl[0]
        s += hsl[1]
        l += hsl[2]
    }
    hsl[0] = h/nColors
    hsl[1] = s/nColors
    hsl[2] = l/nColors
    return hsl
}
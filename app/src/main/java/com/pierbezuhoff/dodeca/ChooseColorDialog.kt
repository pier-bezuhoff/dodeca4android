package com.pierbezuhoff.dodeca

import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.choose_color_row.view.*

class ChooseColorDialog(val activity: MainActivity, dodecaView: DodecaView)  {
    private val data: Array<ColorGroup> by lazy {
        dodecaView.circleGroup.figures
            .groupBy { it.borderColor }
            .toSortedMap()
            .map { (color, circle) -> ColorGroup(color, circle) }
            .toTypedArray()
    }
    var listener: ChooseColorListener = activity
    interface ChooseColorListener {
        fun onChooseColorClosed()
    }

    fun build(): Dialog {
        val builder = AlertDialog.Builder(activity)
        val inflater = activity.layoutInflater
        val layout = inflater.inflate(R.layout.choose_color_dialog, null)
        builder.setView(layout)
        val manager = LinearLayoutManager(activity)
        val colorGroupAdapter = ColorGroupAdapter(data)
        layout.findViewById<RecyclerView>(R.id.color_groups)!!.apply {
            layoutManager = manager
            adapter = colorGroupAdapter
        }
        val dialog = builder.apply {
            setMessage("Choose circle(s) to change color of it (them)")
            setPositiveButton("Change") { _, _ -> onChooseColor() }
            setNegativeButton("Cancel") { dialog, _ -> listener.onChooseColorClosed() }
        }.create()
        dialog.setOnDismissListener { listener.onChooseColorClosed() }
        return dialog
    }

    private fun onChooseColor() {
        // https://github.com/martin-stone/hsv-alpha-color-picker-android
        // notify adapter on change
    }
}

class ColorGroupAdapter(
    private val colorGroups: Array<ColorGroup>
) : RecyclerView.Adapter<ColorGroupAdapter.ViewHolder>() {
    class ViewHolder(val row: LinearLayout) : RecyclerView.ViewHolder(row)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val row = LayoutInflater.from(parent.context)
            .inflate(R.layout.choose_color_row, parent, false) as LinearLayout
        return ViewHolder(row)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val colorGroup = colorGroups[position]
        holder.row.group_color.setImageDrawable(ColorDrawable(colorGroup.color))
        // setup circle group
    }

    override fun getItemCount(): Int = colorGroups.size
}

data class ColorGroup(val color: Int, val circles: List<CircleFigure>)
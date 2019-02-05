package com.pierbezuhoff.dodeca

import android.app.Dialog
import android.content.Context
import android.graphics.PorterDuff
import android.graphics.drawable.LayerDrawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.choose_color_row.view.*
import kotlinx.android.synthetic.main.edit_circle.view.*
import org.jetbrains.anko.alert
import org.jetbrains.anko.customView
import org.jetbrains.anko.include

class ChooseColorDialog(val activity: MainActivity, dodecaView: DodecaView)  {
    private val data: Array<CircleRow> by lazy {
        dodecaView.circleGroup.figures
            .mapIndexed { i, figure -> CircleRow(figure, i) }
            .filter { it.figure.show } // maybe: also show invisible circles in end + options.showAllCircles
            .sortedBy { it.figure.color }
            .toTypedArray()
    }
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
        val colorGroupAdapter = CirclesAdapter(activity.applicationContext, data)
        layout.findViewById<RecyclerView>(R.id.color_groups)!!.apply {
            layoutManager = manager
            adapter = colorGroupAdapter
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

class CirclesAdapter(
    private val context: Context,
    private val rows: Array<CircleRow>
) : RecyclerView.Adapter<CirclesAdapter.ViewHolder>() {
    class ViewHolder(val row: View) : RecyclerView.ViewHolder(row)

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
                border.alpha = 0
            } else {
                inner.alpha = 0
                border.setColorFilter(figure.borderColor ?: figure.color, PorterDuff.Mode.SRC_ATOP)
            }
            this.circle_image.setImageDrawable(circleImage)
            setOnClickListener {
                Log.i("ChooseColorDialog", "pressed circle ${row.id}")
                context.alert("Edit circle ${row.id}") {
                    customView {
                        include<LinearLayout>(R.layout.edit_circle).apply {
                            this.circle_color.apply {
                                setColorFilter(figure.color)
                                setOnClickListener {
                                    // open dialog with ColorPicker
                                }
                            }
                            this.circle_fill.isChecked = figure.fill
                            this.circle_has_border_color.apply {
                                isChecked = figure.borderColor != null
                                this.setOnCheckedChangeListener { _, checked ->
                                    this.circle_border_color?.isEnabled = checked
                                }
                            }
                            this.circle_border_color.apply {
                                if (figure.borderColor == null)
                                    isEnabled = false
                                setColorFilter(figure.borderColor ?: figure.color)
                                setOnClickListener {
                                    // open dialog with color picker
                                }
                            }
                        }

                    }
                    positiveButton("Apply") {
                        // save changes
                    }
                    negativeButton("Cancel") { }
                }.show()
            }
        }
    }

    override fun getItemCount(): Int = rows.size
}

class CircleRow(val figure: CircleFigure, val id: Int) {
    private val equivalence: List<Any?> get() = listOf(figure.color, figure.fill, figure.borderColor)
    fun equivalent(other: CircleRow): Boolean = equivalence == other.equivalence
}

class CirclesRow(val circles: Array<CircleRow>)
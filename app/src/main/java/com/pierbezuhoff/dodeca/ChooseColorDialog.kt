package com.pierbezuhoff.dodeca

import android.app.Dialog
import android.graphics.PorterDuff
import android.graphics.drawable.LayerDrawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.choose_color_row.view.*

class ChooseColorDialog(val activity: MainActivity, dodecaView: DodecaView)  {
    private val data: Array<CircleFigure> by lazy {
        dodecaView.circleGroup.figures
            .filter { it.show } // maybe: also show invisible circles in end + options.showAllCircles
            .sortedBy { it.color }
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
        val colorGroupAdapter = CirclesAdapter(data)
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
    private val circles: Array<CircleFigure>
) : RecyclerView.Adapter<CirclesAdapter.ViewHolder>() {
    class ViewHolder(val row: View) : RecyclerView.ViewHolder(row)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val row = LayoutInflater.from(parent.context)
            .inflate(R.layout.choose_color_row, parent, false)
        return ViewHolder(row)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val circle = circles[position]
        with(holder.row) {
            this.circle_name.text = "$position"
            val circleImage =
                ResourcesCompat.getDrawable(resources, R.drawable.circle_image, null) as LayerDrawable
            circleImage.mutate()
            val border = circleImage.getDrawable(0)
            val inner = circleImage.getDrawable(1)
            if (circle.fill) {
                inner.setColorFilter(circle.color, PorterDuff.Mode.SRC_ATOP)
                border.alpha = 0
            } else {
                inner.alpha = 0
                border.setColorFilter(circle.borderColor ?: circle.color, PorterDuff.Mode.SRC_ATOP)
            }
            this.circle_image.setImageDrawable(circleImage)
            setOnClickListener {
                Log.i("ChooseColorDialog", "pressed circle $position")
            }
        }
    }

    override fun getItemCount(): Int = circles.size
}

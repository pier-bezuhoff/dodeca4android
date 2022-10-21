package com.pierbezuhoff.dodeca.ui.dodecaedit

import android.app.Dialog
import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.pierbezuhoff.dodeca.R
import com.pierbezuhoff.dodeca.data.CircleGroup
import com.pierbezuhoff.dodeca.databinding.AdjustAnglesDialogBinding
import org.apache.commons.math3.fraction.Fraction
import org.jetbrains.anko.layoutInflater

class AdjustAnglesDialog(
    private val context: Context,
    private val adjustAnglesListener: AdjustAnglesListener,
    private val circleGroup: CircleGroup
) {
    interface AdjustAnglesListener {
        fun onAdjustAnglesClosed()
    }

    fun build(): Dialog {
        val builder = AlertDialog.Builder(context)
        val inflater = context.layoutInflater
        val layout = inflater.inflate(R.layout.adjust_angles_dialog, null)
        val binding = AdjustAnglesDialogBinding.inflate(inflater)
        with(binding) {
            angle1 = 1
            angle1Denominator = 1
            angle2 = 1
            angle2Denominator = 1
            angle3 = 1
            angle3Denominator = 1
            val figures = circleGroup.figures
            showAngle3 =
                figures.size >= 5 &&
                !figures[4].show && !figures[5].show
        }
        builder.setView(layout)
        val dialog = builder.apply {
            setMessage(R.string.adjust_angles_dialog_message)
            setPositiveButton(R.string.adjust_angles_dialog_edit) { _, _ -> } // will be set later
            setNegativeButton(R.string.adjust_angles_dialog_cancel) { _, _ -> adjustAnglesListener.onAdjustAnglesClosed() }
        }.create()
        dialog.setOnDismissListener { adjustAnglesListener.onAdjustAnglesClosed() }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val figures = circleGroup.figures
                val f1 = Fraction(binding.angle1, binding.angle1Denominator)
                val f2 = Fraction(binding.angle2, binding.angle2Denominator)
                val f3 = Fraction(binding.angle3, binding.angle3Denominator)
                val engines = mapOf(
                    Pair(0, 1) to f1,
                    Pair(2, 3) to f2,
                    Pair(4, 5) to f3
                )
                for ((ixs, f) in engines) {
                    val (ix1, ix2) = ixs
                    if (
                        f.numerator != f.denominator &&
                        ix1 < figures.size && ix2 < figures.size &&
                        !figures[ix1].show && !figures[ix2].show
                    ) {
                        val c1 = figures[ix1]
                        val c2 = figures[ix2]
                        c2.changeAngle(c1, f.toDouble())
                        circleGroup[ix2] = c2
                    }
                }
                dialog.dismiss()
            }
        }
        return dialog
    }
}

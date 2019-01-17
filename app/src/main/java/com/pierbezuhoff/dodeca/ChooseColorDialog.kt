package com.pierbezuhoff.dodeca

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import java.lang.IllegalStateException

class ChooseColorDialog : DialogFragment() {
    lateinit var dodecaView: DodecaView // should be set from MainActivity before show()
    lateinit var listener: ChooseColorListener
    interface ChooseColorListener {
        fun onChooseColorClosed()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as ChooseColorListener
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.choose_color_dialog, container, false)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            AlertDialog.Builder(it).apply {
                setMessage("Choose circle(s) to change color of it (them)")
                setPositiveButton("Change") { _, _ -> }
                // https://github.com/martin-stone/hsv-alpha-color-picker-android
                setNegativeButton("Cancel") { dialog, _ -> dialog.cancel(); listener.onChooseColorClosed() }
            }.create()
        } ?: throw IllegalStateException("activity cannot be null")
    }
}
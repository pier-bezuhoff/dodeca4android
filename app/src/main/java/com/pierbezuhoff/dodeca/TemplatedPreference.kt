package com.pierbezuhoff.dodeca

import android.content.Context
import android.content.res.TypedArray
import android.preference.EditTextPreference
import android.preference.ListPreference
import android.util.AttributeSet
import android.util.Log

class TemplatedEditTextPreference(context: Context, attributeSet: AttributeSet) : EditTextPreference(context, attributeSet) {
    val template: String

    init {
        val typedArray = context.obtainStyledAttributes(attributeSet, R.styleable.TemplatedEditTextPreference, 0, 0)
        try {
            template = typedArray?.getString(R.styleable.TemplatedListPreference_template) ?: ""
        } finally {
            typedArray.recycle()
        }
        Log.i("Templated", "template = $template")
    }

    override fun onGetDefaultValue(a: TypedArray?, index: Int): Any {
        val default = super.onGetDefaultValue(a, index)
//        template?.let {
//            when (default) {
//                is String -> summary = template.format(default)
//            }
//        }
//        Log.i("Templated", "summary = $summary")
        return default
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        super.onDialogClosed(positiveResult)
        summary = template.format(text)
        Log.i("Templated", "summary = $summary")
    }
}

class TemplatedListPreference(context: Context, attributeSet: AttributeSet? = null) : ListPreference(context, attributeSet) {
    var template: String = ""

    override fun onGetDefaultValue(a: TypedArray?, index: Int): Any {
        val default = super.onGetDefaultValue(a, index)
        when (default) { is String -> summary = template.format(default) }
        return default
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        super.onDialogClosed(positiveResult)
        summary = template.format(entry)
    }
}
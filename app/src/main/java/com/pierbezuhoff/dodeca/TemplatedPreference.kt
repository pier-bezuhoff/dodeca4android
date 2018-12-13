package com.pierbezuhoff.dodeca

import android.content.Context
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import android.util.AttributeSet

/* override standard preferences in order to format current chosen
* value into summary */

class TemplatedEditTextPreference(
    context: Context, attributeSet: AttributeSet? = null
) : EditTextPreference(context, attributeSet) {
    override fun getSummary(): CharSequence? = super.getSummary()?.toString()?.format(text)
}

class TemplatedListPreference(
    context: Context, attributeSet: AttributeSet? = null
) : ListPreference(context, attributeSet) {
    override fun getSummary(): CharSequence? = super.getSummary()?.toString()?.format(entry)
}
package com.pierbezuhoff.dodeca

import android.graphics.Color
import android.graphics.Paint
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel

// ?
// OptionsModel?
class DodecaModel : ViewModel() {
    val ddu: LiveData<DDU> = MutableLiveData<DDU>()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
    }
    val circleGroup: LiveData<CircleGroup> = Transformations.map(ddu) { CircleGroupImpl(it.circles, paint) }
    val trace = MutableLiveData<Trace>()
}
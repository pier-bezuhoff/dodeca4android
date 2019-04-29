package com.pierbezuhoff.dodeca.models

import android.graphics.Color
import android.graphics.Paint
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.pierbezuhoff.dodeca.data.CircleGroup
import com.pierbezuhoff.dodeca.data.DDU
import com.pierbezuhoff.dodeca.data.options
import com.pierbezuhoff.dodeca.data.values
import com.pierbezuhoff.dodeca.utils.dx
import com.pierbezuhoff.dodeca.utils.dy
import com.pierbezuhoff.dodeca.utils.sx

class DodecaViewModel : ViewModel() {
    private val _ddu: MutableLiveData<DDU> = MutableLiveData()
    private val _circleGroup: MutableLiveData<CircleGroup> = MutableLiveData()
    private var _redrawTraceOnce: Boolean = options.drawTrace.default
    private var _updateOnce = false
    private var last20NUpdates: Long = 0L
    private var last20UpdateTime: Long = 0L
    private val dx get() = values.motion.dx
    private val dy get() = values.motion.dy
    private val scale get() = values.motion.sx

    val ddu: LiveData<DDU> = _ddu
    val circleGroup: LiveData<CircleGroup> = _circleGroup
    val redrawTraceOnce: Boolean get() = if (_redrawTraceOnce) { _redrawTraceOnce = false; true } else false
    var lastUpdateTime: Long = 0L
    val updateOnce: Boolean get() = if (_updateOnce) { _updateOnce = false; true } else false
    val paint = Paint(DEFAULT_PAINT)


    init {
        ddu.observeForever {
            // dodecaView.onNewDDU
        }
    }

    companion object {
        val DEFAULT_PAINT = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
        }
    }
}
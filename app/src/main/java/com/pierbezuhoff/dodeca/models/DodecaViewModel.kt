package com.pierbezuhoff.dodeca.models

import android.content.Context
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.pierbezuhoff.dodeca.data.CircleGroup
import com.pierbezuhoff.dodeca.data.DDU
import com.pierbezuhoff.dodeca.data.Shapes
import com.pierbezuhoff.dodeca.data.Trace
import com.pierbezuhoff.dodeca.data.exampleDDU
import com.pierbezuhoff.dodeca.data.values
import com.pierbezuhoff.dodeca.utils.dduDir
import java.io.File

class DodecaViewModel : ViewModel() {
    lateinit var sharedPreferencesModel: SharedPreferencesModel
    private val _ddu: MutableLiveData<DDU> = MutableLiveData()
    private val _circleGroup: MutableLiveData<CircleGroup> = MutableLiveData()
    private var _updateOnce = false
    private var last20NUpdates: Long = 0L
    private var last20UpdateTime: Long = 0L

    val ddu: LiveData<DDU> = _ddu
    val circleGroup: LiveData<CircleGroup> = _circleGroup
    var _redrawTraceOnce: Boolean = DEFAULT_DRAW_TRACE
    val redrawTraceOnce: Boolean get() = if (_redrawTraceOnce) { _redrawTraceOnce = false; true } else false
    var lastUpdateTime: Long = 0L
    val updateOnce: Boolean get() = if (_updateOnce) { _updateOnce = false; true } else false
    val paint = Paint(DEFAULT_PAINT)
    val trace: Trace = Trace()

    // ddu:r -> motion -> visible:r
    val motion = Matrix()
    var shape: Shapes = Shapes.CIRCLE
    var drawTrace: Boolean = DEFAULT_DRAW_TRACE
    var updating: Boolean = DEFAULT_UPDATING

    init {
        ddu.observeForever {
            // dodecaView.onNewDDU
        }
    }

    // TODO: avoid context
    fun initFrom(context: Context) {
        _ddu.value = try {
            DDU.readFile(File(context.dduDir, values.recentDDU))
        } catch (e: Exception) {
            e.printStackTrace()
            exampleDDU
        }
    }

    companion object {
        const val TAG = "DodecaViewModel"
        private val DEFAULT_PAINT = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
        }
        private const val DEFAULT_DRAW_TRACE = true
        private const val DEFAULT_UPDATING = true
    }
}
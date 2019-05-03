package com.pierbezuhoff.dodeca.models

import android.content.Context
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.pierbezuhoff.dodeca.R
import com.pierbezuhoff.dodeca.data.CircleGroup
import com.pierbezuhoff.dodeca.data.CircleGroupImpl
import com.pierbezuhoff.dodeca.data.DDU
import com.pierbezuhoff.dodeca.data.Shapes
import com.pierbezuhoff.dodeca.data.Trace
import com.pierbezuhoff.dodeca.data.exampleDDU
import com.pierbezuhoff.dodeca.data.options
import com.pierbezuhoff.dodeca.data.values
import com.pierbezuhoff.dodeca.utils.dduDir
import java.io.File
import kotlin.properties.Delegates

class DodecaViewModel : ViewModel() {
    lateinit var sharedPreferencesModel: SharedPreferencesModel
    private val _circleGroup: MutableLiveData<CircleGroup> = MutableLiveData()
    private val _nUpdates: MutableLiveData<Long> = MutableLiveData(0L)
    private val _dTime: MutableLiveData<Float> = MutableLiveData()
    private var _updateOnce = false
    private var statTimeDelta: Int by Delegates.notNull() // n of updates between stat time update
    private var lastTimedUpdate: Long by Delegates.notNull()
    private var lastTimedUpdateTime: Long by Delegates.notNull() // in ms <- System.currentTimeMillis()

    val ddu: MutableLiveData<DDU> = MutableLiveData()
    val circleGroup: LiveData<CircleGroup> = _circleGroup
    val nUpdates: LiveData<Long> = _nUpdates
    val dTime: LiveData<Float> = _dTime
    var lastUpdateTime: Long by Delegates.notNull() // in ms <- System.currentTimeMillis()
    val updateOnce: Boolean get() = if (_updateOnce) { _updateOnce = false; true } else false
    val paint = Paint(DEFAULT_PAINT)

    val trace: Trace = Trace()
    // ddu:r -> motion -> visible:r
    val motion: Matrix = Matrix()
    val shape: MutableLiveData<Shapes> = MutableLiveData(DEFAULT_SHAPE)
    val drawTrace: MutableLiveData<Boolean> = MutableLiveData(DEFAULT_DRAW_TRACE)
    val updating: MutableLiveData<Boolean> = MutableLiveData(DEFAULT_UPDATING)

    init {
        ddu.observeForever { onNewDDU(it) }
    }

    // TODO: avoid context
    fun initFrom(context: Context) {
        ddu.value = try {
            DDU.readFile(
                if (File(values.recentDDU).exists())
                    File(values.recentDDU)
                else
                    File(context.dduDir, values.recentDDU))
        } catch (e: Exception) {
            e.printStackTrace()
            exampleDDU
        }
        statTimeDelta = context.resources.getInteger(R.integer.stat_time_delta)
    }

    fun loadDDU(newDDU: DDU) {
        ddu.value = newDDU
    }

    fun requestUpdateOnce() {
        _updateOnce = true
    }

    fun updateStat(times: Int = 1) {
        val dNUpdates = times * (if (values.reverseMotion) -1 else 1)
        val n: Long = (nUpdates.value ?: 0L) + dNUpdates
        _nUpdates.value = n
        if (values.showStat) {
            val overhead = n - lastTimedUpdate
            if (overhead >= statTimeDelta) {
                _dTime.value = (lastUpdateTime - lastTimedUpdateTime) / (overhead / statTimeDelta.toFloat()) / 1000f
                lastTimedUpdate = n
                lastTimedUpdateTime = lastUpdateTime
            }
        }
    }

    private fun onNewDDU(ddu: DDU) {
        trace.clear()
        motion.reset() // NOTE: go to best center in DodecaView
        shape.value = ddu.shape
        drawTrace.value = ddu.drawTrace ?: DEFAULT_DRAW_TRACE
        updating.value = DEFAULT_UPDATING
        _nUpdates.value = 0
        lastTimedUpdate = 0
        lastUpdateTime = System.currentTimeMillis()
        lastTimedUpdateTime = lastUpdateTime
        _circleGroup.value = CircleGroupImpl(ddu.circles, paint)
        ddu.file?.let { file ->
            sharedPreferencesModel.set(options.recentDDU, file.absolutePath)
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
        private val DEFAULT_SHAPE = Shapes.CIRCLE
    }
}
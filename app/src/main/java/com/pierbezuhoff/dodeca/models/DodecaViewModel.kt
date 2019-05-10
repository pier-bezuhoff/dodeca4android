package com.pierbezuhoff.dodeca.models

import android.content.Context
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.pierbezuhoff.dodeca.R
import com.pierbezuhoff.dodeca.data.CircleGroup
import com.pierbezuhoff.dodeca.data.CircleGroupImpl
import com.pierbezuhoff.dodeca.data.DDU
import com.pierbezuhoff.dodeca.data.Shapes
import com.pierbezuhoff.dodeca.data.Trace
import com.pierbezuhoff.dodeca.data.options
import com.pierbezuhoff.dodeca.data.values
import com.pierbezuhoff.dodeca.ui.DodecaGestureDetector
import com.pierbezuhoff.dodeca.utils.DB
import com.pierbezuhoff.dodeca.utils.DDUFileDao
import com.pierbezuhoff.dodeca.utils.dduDir
import com.pierbezuhoff.dodeca.utils.dduPath
import com.pierbezuhoff.dodeca.utils.insertOrDropPreview
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.toast
import org.jetbrains.anko.uiThread
import java.io.File
import kotlin.properties.Delegates

class DodecaViewModel : ViewModel() {
    lateinit var sharedPreferencesModel: SharedPreferencesModel
    private val dduFileDao: DDUFileDao by lazy { DB.dduFileDao() }
    private val _circleGroup: MutableLiveData<CircleGroup> = MutableLiveData()
    private val _nUpdates: MutableLiveData<Long> = MutableLiveData(0L)
    private val _dTime: MutableLiveData<Float> = MutableLiveData()
    private var _updateOnce = false
    private var lastTimedUpdate: Long by Delegates.notNull()
    private var lastTimedUpdateTime: Long by Delegates.notNull() // <- System.currentTimeMillis()

    private val _oneStepRequest: MutableLiveData<Unit> = MutableLiveData()
    private val _clearRequest: MutableLiveData<Unit> = MutableLiveData()
    private val _autocenterRequest: MutableLiveData<Unit> = MutableLiveData()
    private val _saveDDUAtRequest: MutableLiveData<File?> = MutableLiveData() // TODO: check setting value to null

    val oneStepRequest: LiveData<Unit> = _oneStepRequest
    val clearRequest: LiveData<Unit> = _clearRequest
    val autocenterRequest: LiveData<Unit> = _autocenterRequest
    val saveDDUAtRequest: LiveData<File?> = _saveDDUAtRequest

    val ddu: MutableLiveData<DDU> = MutableLiveData()
    val circleGroup: LiveData<CircleGroup> = _circleGroup
    val nUpdates: LiveData<Long> = _nUpdates
    val dTime: LiveData<Float> = _dTime
    var lastUpdateTime: Long by Delegates.notNull() // <- System.currentTimeMillis()
    var statTimeDelta: Int by Delegates.notNull() // n of updates between stat time update
        private set
    val updateOnce: Boolean get() = if (_updateOnce) { _updateOnce = false; true } else false
    val paint = Paint(DEFAULT_PAINT)

    val trace: Trace = Trace()
    // ddu:r -> motion -> visible:r
    val motion: Matrix = Matrix()
    val shape: MutableLiveData<Shapes> = MutableLiveData(DEFAULT_SHAPE)
    val drawTrace: MutableLiveData<Boolean> = MutableLiveData(DEFAULT_DRAW_TRACE)
    val updating: MutableLiveData<Boolean> = MutableLiveData(DEFAULT_UPDATING)

    private val _gestureDetector: MutableLiveData<DodecaGestureDetector> = MutableLiveData()
    val gestureDetector: LiveData<DodecaGestureDetector> = _gestureDetector

    init {
        ddu.observeForever { onNewDDU(it) }
    }

    /* Set ddu, defaults and ddu-related LiveData-s */
    fun initFrom(context: Context) {
        ddu.value = try {
            DDU.fromFile(getRecentDDUFile(context))
        } catch (e: Exception) {
            e.printStackTrace()
            DDU.exampleDDU
        }
        statTimeDelta = context.resources.getInteger(R.integer.stat_time_delta)
    }

    private fun getRecentDDUFile(context: Context): File {
        val recentInCurrentDir = File(values.recentDDU)
        val recentInDDUDir = File(context.dduDir, values.recentDDU)
        if (recentInCurrentDir.exists())
            return recentInCurrentDir
        else
            return recentInDDUDir
    }

    fun registerGestureDetector(detector: DodecaGestureDetector) {
        _gestureDetector.value = detector
    }

    fun loadDDU(newDDU: DDU) {
        ddu.value = newDDU
    }

    fun requestUpdateOnce() {
        _updateOnce = true
    }

    fun requestOneStep() { _oneStepRequest.value = Unit }
    fun requestClear() { _clearRequest.value = Unit }
    fun requestAutocenter() { _autocenterRequest.value = Unit }
    fun requestSaveDDUAt(file: File? = null) { _saveDDUAtRequest.value = file }

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
        motion.reset()
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

    fun saveDDU(context: Context, ddu: DDU, outputFile: File? = null) {
        val file: File? = outputFile ?: ddu.file
        if (file == null) {
            Log.i(TAG, "saveDDU: ddu has no file")
            // then save as
            context.toast(context.getString(R.string.error_ddu_save_no_file_toast))
        } else {
            context.doAsync {
                try {
                    Log.i(TAG, "Saving ddu at ${context.dduPath(file)}")
                    ddu.saveToFile(file)
                    uiThread {
                        context.toast(context.getString(R.string.ddu_saved_toast, context.dduPath(file)))
                    }
                    dduFileDao.insertOrDropPreview(file.name)
                } catch (e: Throwable) {
                    e.printStackTrace()
                    uiThread {
                        context.toast(context.getString(R.string.error_ddu_save_toast))
                    }
                }
            }
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

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
import com.pierbezuhoff.dodeca.data.Ddu
import com.pierbezuhoff.dodeca.data.Shapes
import com.pierbezuhoff.dodeca.data.SharedPreference
import com.pierbezuhoff.dodeca.data.Trace
import com.pierbezuhoff.dodeca.data.options
import com.pierbezuhoff.dodeca.data.values
import com.pierbezuhoff.dodeca.db.DduFileRepository
import com.pierbezuhoff.dodeca.ui.DodecaGestureDetector
import com.pierbezuhoff.dodeca.utils.dduDir
import com.pierbezuhoff.dodeca.utils.dduPath
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.toast
import org.jetbrains.anko.uiThread
import java.io.File
import kotlin.properties.Delegates

class DodecaViewModel : ViewModel() {
    lateinit var sharedPreferencesModel: SharedPreferencesModel
    private val dduFileRepository: DduFileRepository = DduFileRepository.INSTANCE
    private val _ddu: MutableLiveData<Ddu> = MutableLiveData()
    private val _circleGroup: MutableLiveData<CircleGroup> = MutableLiveData()
    private val _nUpdates: MutableLiveData<Long> = MutableLiveData(0L)
    private val _dTime: MutableLiveData<Float> = MutableLiveData()
    private var _updateOnce = false
    private var lastTimedUpdate: Long by Delegates.notNull()
    private var lastTimedUpdateTime: Long by Delegates.notNull() // <- System.currentTimeMillis()

    private val _oneStepRequest: MutableLiveData<Unit> = MutableLiveData()
    private val _clearRequest: MutableLiveData<Unit> = MutableLiveData()
    private val _autocenterRequest: MutableLiveData<Unit> = MutableLiveData()
    private val _saveDduAtRequest: MutableLiveData<File?> = MutableLiveData() // TODO: check setting value to null
    private var oldUpdating: Boolean = DEFAULT_UPDATING

    val oneStepRequest: LiveData<Unit> = _oneStepRequest
    val clearRequest: LiveData<Unit> = _clearRequest
    val autocenterRequest: LiveData<Unit> = _autocenterRequest
    val saveDduAtRequest: LiveData<File?> = _saveDduAtRequest

    val ddu: LiveData<Ddu> = _ddu
    val circleGroup: LiveData<CircleGroup> = _circleGroup
    val nUpdates: LiveData<Long> = _nUpdates
    val dTime: LiveData<Float> = _dTime
    var lastUpdateTime: Long by Delegates.notNull() // <- System.currentTimeMillis()
    var statTimeDelta: Int by Delegates.notNull() // n of updates between stat time update
        private set
    val updateOnce: Boolean
        get() = if (_updateOnce) { _updateOnce = false; true } else false
    val paint = Paint(DEFAULT_PAINT)

    val trace: Trace = Trace()
    // ddu:r -> motion -> visible:r
    val motion: Matrix = Matrix()
    val shape: MutableLiveData<Shapes> = MutableLiveData()
    val drawTrace: MutableLiveData<Boolean> = MutableLiveData()
    val updating: MutableLiveData<Boolean> = MutableLiveData()

    private val _gestureDetector: MutableLiveData<DodecaGestureDetector> = MutableLiveData()
    val gestureDetector: LiveData<DodecaGestureDetector> = _gestureDetector

    /* Set ddu, defaults and ddu-related LiveData-s */
    fun initFrom(context: Context) {
        loadDdu(context.getInitialDdu())
        statTimeDelta = context.resources.getInteger(R.integer.stat_time_delta)
        sharedPreferencesModel.fetchAll()
    }

    private fun Context.getInitialDdu(): Ddu =
        try {
            Ddu.fromFile(this.getRecentDduFile())
        } catch (e: Exception) {
            e.printStackTrace()
            Ddu.EXAMPLE_DDU
        }

    private fun Context.getRecentDduFile(): File {
        sharedPreferencesModel.fetch(options.recentDdu)
        val recentFromAbsolutePath = File(values.recentDdu) // new format
        val recentInDduDir = File(this.dduDir, values.recentDdu) // deprecated format
        if (recentFromAbsolutePath.exists())
            return recentFromAbsolutePath
        else
            return recentInDduDir
    }

    fun <T : Any> setSharedPreference(sharedPreference: SharedPreference<T>, value: T) {
        sharedPreferencesModel.set(sharedPreference, value)
    }

    fun registerGestureDetector(detector: DodecaGestureDetector) {
        _gestureDetector.value = detector
    }

    fun loadDdu(ddu: Ddu) {
        resetDduParams()
        _nUpdates.value = 0
        _circleGroup.value = CircleGroupImpl(ddu.circles, paint)
        _ddu.value = ddu
        shape.value = ddu.shape
        drawTrace.value = ddu.drawTrace ?: DEFAULT_DRAW_TRACE
        updating.value = DEFAULT_UPDATING
        ddu.file?.let { file ->
            setSharedPreference(options.recentDdu, file.absolutePath)
        }
    }

    private fun resetDduParams() {
        trace.clear()
        motion.reset()
        lastTimedUpdate = 0
        lastUpdateTime = System.currentTimeMillis()
        lastTimedUpdateTime = lastUpdateTime
    }

    fun requestUpdateOnce() {
        _updateOnce = true
    }

    fun requestOneStep() {
        _oneStepRequest.value = Unit
    }

    fun requestClear() {
        _clearRequest.value = Unit
    }

    fun requestAutocenter() {
        _autocenterRequest.value = Unit
    }

    // requestSaveDduAt -> (pause; DodecaView.saveDdu) -> DodecaViewModel.saveDdu -> resume
    fun requestSaveDduAt(file: File? = null) {
        pause()
        _saveDduAtRequest.value = file
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

    fun saveDdu(context: Context, ddu: Ddu, outputFile: File? = null) {
        val file: File? = outputFile ?: ddu.file
        if (file == null) {
            Log.i(TAG, "saveDdu: ddu has no file")
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
                    dduFileRepository.dropPreviewInserting(file.name)
                } catch (e: Throwable) {
                    e.printStackTrace()
                    uiThread {
                        context.toast(context.getString(R.string.error_ddu_save_toast))
                    }
                }
            }
        }
        resume()
    }

    fun resume() =
        updating.postValue(oldUpdating)

    fun pause() {
        if (updating.value != false) {
            oldUpdating = updating.value ?: DEFAULT_UPDATING
            updating.postValue(false)
        }
    }

    fun stop() {
        oldUpdating = true
        updating.postValue(false)
    }

    fun toggleUpdating() {
        updating.value = !(updating.value ?: DEFAULT_UPDATING)
    }

    fun toggleDrawTrace() {
        drawTrace.value = !(drawTrace.value ?: DEFAULT_DRAW_TRACE)
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

package com.pierbezuhoff.dodeca.models

import android.app.Application
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.pierbezuhoff.dodeca.R
import com.pierbezuhoff.dodeca.data.CircleGroup
import com.pierbezuhoff.dodeca.data.Ddu
import com.pierbezuhoff.dodeca.data.Shapes
import com.pierbezuhoff.dodeca.data.SharedPreference
import com.pierbezuhoff.dodeca.data.options
import com.pierbezuhoff.dodeca.data.values
import com.pierbezuhoff.dodeca.db.DduFileRepository
import com.pierbezuhoff.dodeca.ui.DodecaGestureDetector
import com.pierbezuhoff.dodeca.utils.dduDir
import com.pierbezuhoff.dodeca.utils.dduPath
import kotlinx.coroutines.launch
import org.jetbrains.anko.toast
import java.io.File
import kotlin.math.roundToInt
import kotlin.properties.Delegates

class DodecaViewModel(application: Application) : AndroidViewModel(application) {
    lateinit var sharedPreferencesModel: SharedPreferencesModel
    private val dduFileRepository: DduFileRepository = DduFileRepository.INSTANCE
    private val context: Context
        get() = getApplication<Application>().applicationContext

    private val _dduRepresentation: MutableLiveData<DduRepresentation> = MutableLiveData()
    private val _nUpdates: MutableLiveData<Long> = MutableLiveData(0L)
    private val _dTime: MutableLiveData<Float> = MutableLiveData()
    private var _updateOnce = false
    private var lastTimedUpdate: Long by Delegates.notNull()
    private var lastTimedUpdateTime: Long by Delegates.notNull() // <- System.currentTimeMillis()

    private var oldUpdating: Boolean = DEFAULT_UPDATING

    val dduRepresentation: LiveData<DduRepresentation> = _dduRepresentation
    val nUpdates: LiveData<Long> = _nUpdates
    val dTime: LiveData<Float> = _dTime
    var lastUpdateTime: Long by Delegates.notNull() // <- System.currentTimeMillis()
    var statTimeDelta: Int by Delegates.notNull() // n of updates between stat time redraw
        private set
    val updateOnce: Boolean
        get() = if (_updateOnce) { _updateOnce = false; true } else false

    private val _gestureDetector: MutableLiveData<DodecaGestureDetector> = MutableLiveData()
    val gestureDetector: LiveData<DodecaGestureDetector> = _gestureDetector

    init {
        viewModelScope.launch {
            val initialDdu: Ddu = getInitialDdu()
            loadDdu(initialDdu)
        }
        statTimeDelta = context.resources.getInteger(R.integer.stat_time_delta)
        sharedPreferencesModel.fetchAll()
    }

    fun loadDdu(ddu: Ddu) {
        maybeAutosave() // async
        resetDduParams() // maybe: into DduRepresentation
        _nUpdates.value = 0
        _dduRepresentation.value = DduRepresentation(ddu) // invoke DodecaView observer
        ddu.file?.let { file: File ->
            setSharedPreference(options.recentDdu, file.absolutePath)
        }
    }

    fun loadDduFrom(file: File) {
        viewModelScope.launch {
            val ddu: Ddu = Ddu.fromFile(file)
            loadDdu(ddu)
        }
    }

    fun registerGestureDetector(detector: DodecaGestureDetector) {
        _gestureDetector.value = detector
    }

    fun <T : Any> setSharedPreference(sharedPreference: SharedPreference<T>, value: T) {
        sharedPreferencesModel.set(sharedPreference, value)
    }

    private suspend fun getInitialDdu(): Ddu =
        try {
            Ddu.fromFile(getRecentDduFile())
        } catch (e: Exception) {
            e.printStackTrace()
            Ddu.EXAMPLE_DDU
        }

    private fun getRecentDduFile(): File {
        sharedPreferencesModel.fetch(options.recentDdu)
        val recentFromAbsolutePath = File(values.recentDdu) // new format
        val recentInDduDir = File(context.dduDir, values.recentDdu) // deprecated format
        if (recentFromAbsolutePath.exists())
            return recentFromAbsolutePath
        else
            return recentInDduDir
    }

    private fun resetDduParams() {
        lastTimedUpdate = 0
        lastUpdateTime = System.currentTimeMillis()
        lastTimedUpdateTime = lastUpdateTime
    }

    fun requestUpdateOnce() {
        _updateOnce = true
    }

    fun requestOneStep() {
        dduRepresentation.value?.let { dduRepresentation: DduRepresentation ->
            stop()
            val batch = values.speed.roundToInt()
            dduRepresentation.drawTimes(batch)
            requestUpdateOnce()
            dduRepresentation.presenter.redraw()
        }
    }

    fun requestClear() {
        dduRepresentation.value?.clearTrace()
    }

    fun requestAutocenter() {
        dduRepresentation.value?.autocenterize()
    }

    fun requestSaveDdu() {
        viewModelScope.launch {
            saveDdu()
        }
    }

    fun requestSaveDduAt(file: File) {
        viewModelScope.launch {
            saveDdu(file)
        }
    }

    /** async, maybe schedule ddu saving */
    fun maybeAutosave() {
        if (values.autosave && dduRepresentation.value?.ddu?.file != null)
            viewModelScope.launch {
                saveDdu()
            }
    }

    private suspend fun saveDdu(file: File? = null) {
        dduRepresentation.value?.let { dduRepresentation: DduRepresentation ->
            pause()
            dduRepresentation.buildCurrentDdu()?.let { ddu: Ddu ->
                save(ddu, file)
            }
            resume()
        }
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

    private suspend fun save(ddu: Ddu, outputFile: File? = null) {
        val file: File? = outputFile ?: ddu.file
        if (file == null) {
            Log.i(TAG, "save: ddu has no file")
            // then save as
            context.toast(context.getString(R.string.error_ddu_save_no_file_toast))
        } else {
            try {
                Log.i(TAG, "Saving ddu at ${context.dduPath(file)}")
                ddu.saveToFile(file)
                context.toast(context.getString(R.string.ddu_saved_toast, context.dduPath(file)))
                dduFileRepository.dropPreviewInserting(file.name)
            } catch (e: Throwable) {
                e.printStackTrace()
                context.toast(context.getString(R.string.error_ddu_save_toast))
            }
        }
    }

    fun resume() =
        updating.postValue(oldUpdating)

    fun pause() {
        if (updating.value != false) {
            oldUpdating = updating.value ?: DEFAULT_UPDATING
            updating.postValue(false)
            maybeAutosave()
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

    fun onDraw(canvas: Canvas) =
        dduRepresentation.value?.onDraw(canvas)

    fun setShape(shape: Shapes)

    fun getDduFile(): File?

    fun getCircleGroup(): CircleGroup?

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

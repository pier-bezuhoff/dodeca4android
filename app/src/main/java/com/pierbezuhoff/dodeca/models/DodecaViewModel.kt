package com.pierbezuhoff.dodeca.models

import android.app.Application
import android.content.Context
import android.graphics.Canvas
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.pierbezuhoff.dodeca.R
import com.pierbezuhoff.dodeca.data.CircleGroup
import com.pierbezuhoff.dodeca.data.Ddu
import com.pierbezuhoff.dodeca.data.ImmutableCircleGroup
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
import kotlin.properties.Delegates

class DodecaViewModel(application: Application) :
    AndroidViewModel(application),
    DduRepresentation.StatHolder, // by statUpdater
    DduRepresentation.ToastEmitter
{

    lateinit var sharedPreferencesModel: SharedPreferencesModel // inject
    private val dduFileRepository: DduFileRepository = DduFileRepository.INSTANCE
    private val context: Context
        get() = getApplication<Application>().applicationContext

    private val _dduRepresentation: MutableLiveData<DduRepresentation> = MutableLiveData()
    private val _updating: MutableLiveData<Boolean> = MutableLiveData()
    private val _drawTrace: MutableLiveData<Boolean> = MutableLiveData()
    private val _shape: MutableLiveData<Shapes> = MutableLiveData()
    private val _nUpdates: MutableLiveData<Long> = MutableLiveData(0L)
    private val _dTime: MutableLiveData<Float> = MutableLiveData()

    private var oldUpdating: Boolean = DEFAULT_UPDATING

    val dduRepresentation: LiveData<DduRepresentation> = _dduRepresentation
    val updating: LiveData<Boolean> = _updating
    val drawTrace: LiveData<Boolean> = _drawTrace
    val shape: LiveData<Shapes> = _shape

    private val statUpdater: StatUpdater = StatUpdater()
    val nUpdates: LiveData<Long> = _nUpdates
    val dTime: LiveData<Float> = _dTime

    private val _gestureDetector: MutableLiveData<DodecaGestureDetector> = MutableLiveData()
    val gestureDetector: LiveData<DodecaGestureDetector> = _gestureDetector

    init {
        viewModelScope.launch {
            val initialDdu: Ddu = getInitialDdu()
            loadDdu(initialDdu)
        }
        sharedPreferencesModel.fetchAll()
    }

    fun loadDdu(ddu: Ddu) {
        maybeAutosave() // async
        statUpdater.reset()
        DduRepresentation(ddu).let { dduRepresentation: DduRepresentation ->
            dduRepresentation.connectStatHolder(this)
            dduRepresentation.connectToastEmitter(this)
            dduRepresentation.sharedPreferencesModel = sharedPreferencesModel
            updateDduAttributesFrom(dduRepresentation)
            _dduRepresentation.value = dduRepresentation // invoke DodecaView observer
        }
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

    fun requestOneStep() {
        dduRepresentation.value?.let { dduRepresentation: DduRepresentation ->
            stop()
            dduRepresentation.oneStep()
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

    override fun toast(message: CharSequence) {
        context.toast(message)
    }

    override fun formatToast(id: Int, vararg args: Any) {
        context.toast(context.getString(id, *args))
    }

    fun resume() {
        val newUpdating = oldUpdating
        _updating.postValue(newUpdating)
        dduRepresentation.value?.updating = newUpdating
    }

    fun pause() {
        if (updating.value != false) {
            oldUpdating = updating.value ?: DEFAULT_UPDATING
            val newUpdating = false
            _updating.postValue(newUpdating)
            dduRepresentation.value?.updating = newUpdating
            maybeAutosave()
        }
    }

    fun stop() {
        oldUpdating = true
    }

    fun toggleUpdating() {
        val newUpdating: Boolean = !(updating.value ?: DEFAULT_UPDATING)
        _updating.value = newUpdating
        dduRepresentation.value?.updating = newUpdating
    }

    fun toggleDrawTrace() {
        val newDrawTrace: Boolean = !(drawTrace.value ?: DEFAULT_DRAW_TRACE)
        _drawTrace.value = newDrawTrace
        dduRepresentation.value?.drawTrace = newDrawTrace
    }

    fun onDraw(canvas: Canvas) =
        dduRepresentation.value?.draw(canvas)

    fun setShape(shape: Shapes) {
        _shape.value = shape
        dduRepresentation.value?.shape = shape
    }

    fun getDduFile(): File? =
        dduRepresentation.value?.ddu?.file

    fun getCircleGroup(): ImmutableCircleGroup? =
        dduRepresentation.value?.immutableCircleGroup

    fun changeCircleGroup(act: CircleGroup.() -> Unit) {
        dduRepresentation.value?.changeCircleGroup(act)
    }

    fun updateDduAttributesFrom(dduRepresentation: DduRepresentation) {
        _updating.value = dduRepresentation.updating
        _drawTrace.value = dduRepresentation.drawTrace
        _shape.value = dduRepresentation.shape
    }

    override fun updateStat(delta: Int) =
        statUpdater.updateStat(delta)

    private inner class StatUpdater : DduRepresentation.StatHolder {
        private val statTimeDelta: Int = context.resources.getInteger(R.integer.stat_time_delta)
        private var nUpdates: Long by Delegates.observable(0L) { _, _, newNUpdates: Long ->
            _nUpdates.value = newNUpdates
        }
        private var lastUpdateTime: Long = 0
        private var lastTimedUpdate: Long = 0
        private var lastTimedUpdateTime: Long = 0
        private var dTime: Float? by Delegates.observable<Float?>(null) { _, _, newDTime: Float? ->
            _dTime.value = newDTime
        }

        override fun updateStat(delta: Int) {
            lastUpdateTime = System.currentTimeMillis()
            val dNUpdates: Int = delta * (if (values.reverseMotion) -1 else 1)
            nUpdates += dNUpdates
            if (values.showStat) {
                val overhead: Long = nUpdates - lastTimedUpdate
                if (overhead >= statTimeDelta) {
                    dTime = (lastUpdateTime - lastTimedUpdateTime) / (overhead / statTimeDelta.toFloat()) / 1000f
                    lastTimedUpdate = nUpdates
                    lastTimedUpdateTime = lastUpdateTime
                }
            }
        }

        fun reset() {
            nUpdates = 0
            lastUpdateTime = 0
            lastTimedUpdate = 0
            lastTimedUpdateTime = 0
            dTime = null

        }
    }

    companion object {
        const val TAG = "DodecaViewModel"
        private const val DEFAULT_DRAW_TRACE = true
        private const val DEFAULT_UPDATING = true
        private val DEFAULT_SHAPE = Shapes.CIRCLE
    }
}

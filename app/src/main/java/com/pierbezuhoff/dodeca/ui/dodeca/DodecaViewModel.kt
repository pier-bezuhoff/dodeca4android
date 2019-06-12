package com.pierbezuhoff.dodeca.ui.dodeca

import android.app.Application
import android.graphics.Canvas
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.pierbezuhoff.dodeca.R
import com.pierbezuhoff.dodeca.data.CircleGroup
import com.pierbezuhoff.dodeca.data.Ddu
import com.pierbezuhoff.dodeca.data.Option
import com.pierbezuhoff.dodeca.data.Shape
import com.pierbezuhoff.dodeca.data.options
import com.pierbezuhoff.dodeca.data.values
import com.pierbezuhoff.dodeca.models.DduRepresentation
import com.pierbezuhoff.dodeca.models.OptionsManager
import com.pierbezuhoff.dodeca.ui.meta.DodecaAndroidViewModelWithOptionsManager
import com.pierbezuhoff.dodeca.utils.Filename
import com.pierbezuhoff.dodeca.utils.dduDir
import com.pierbezuhoff.dodeca.utils.dduPath
import com.pierbezuhoff.dodeca.utils.div
import com.pierbezuhoff.dodeca.utils.filename
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.anko.toast
import java.io.File
import kotlin.math.abs
import kotlin.properties.Delegates

class DodecaViewModel(
    application: Application,
    optionsManager: OptionsManager
) : DodecaAndroidViewModelWithOptionsManager(application, optionsManager)
    , DduRepresentation.StatHolder // by statUpdater
    , DduRepresentation.ToastEmitter
{
    private val _dduRepresentation: MutableLiveData<DduRepresentation> = MutableLiveData()
    private val _updating: MutableLiveData<Boolean> = MutableLiveData()
    private val _drawTrace: MutableLiveData<Boolean> = MutableLiveData()
    private val _nUpdates: MutableLiveData<Long> = MutableLiveData(0L)
    private val _dTime: MutableLiveData<Float> = MutableLiveData()

    private var oldUpdating: Boolean? = null
    private var dduLoaded = false // mark that MainActivity should not repeat loadInitialDdu

    val dduRepresentation: LiveData<DduRepresentation> = _dduRepresentation
    val updating: LiveData<Boolean> = _updating
    val drawTrace: LiveData<Boolean> = _drawTrace
    val shape: MutableLiveData<Shape> = MutableLiveData(DEFAULT_SHAPE)

    private val statUpdater: StatUpdater = StatUpdater()
    val nUpdates: LiveData<Long> = _nUpdates
    val dTime: LiveData<Float> = _dTime
    val statTimeDelta: Int = statUpdater.statTimeDelta

    val gestureDetector: DodecaGestureDetector = DodecaGestureDetector.get(context)

    init {
        registerOptionsObservers()
        optionsManager.fetchAll()
        shape.observeForever { shape: Shape ->
            dduRepresentation.value?.shape = shape
        }
    }

    fun loadInitialDdu() {
        if (!dduLoaded) {
            dduLoaded = true
            viewModelScope.launch {
                val initialDdu: Ddu = getInitialDdu()
                loadDdu(initialDdu)
            }
        }
    }

    fun loadDdu(ddu: Ddu) {
        dduLoaded = true
        maybeAutosave() // async, but capture current dduRepresentation
        statUpdater.reset()
        DduRepresentation(ddu)
            .let { dduRepresentation: DduRepresentation ->
                dduRepresentation.statHolderSubscription.subscribeFrom(this)
                dduRepresentation.toastEmitterSubscription.subscribeFrom(this)
                dduRepresentation.connectOptionsManager(optionsManager)
                gestureDetector.onScrollSubscription.subscribeFrom(dduRepresentation)
                gestureDetector.onScaleSubscription.subscribeFrom(dduRepresentation)
                updateDduAttributesFrom(dduRepresentation)
                _dduRepresentation.value = dduRepresentation // invoke DodecaView observer
            }
        ddu.file?.let { file: File ->
            setSharedPreference(options.recentDdu, Filename(context.dduPath(file)))
        }
    }

    suspend fun loadDduFrom(file: File) {
        try {
            val ddu: Ddu = Ddu.fromFile(file)
            loadDdu(ddu)
        } catch (e: Exception) {
            e.printStackTrace()
            formatToast(R.string.bad_ddu_format_toast, file.path)
        }
    }

    private fun registerOptionsObservers() {
        options.showAllCircles.observe { dduRepresentation.value?.onShowAllCircles(it) }
        options.autocenterAlways.observe { dduRepresentation.value?.onAutocenterAlways(it) }
        options.canvasFactor.observe { dduRepresentation.value?.onCanvasFactor(it) }
        options.speed.observe { dduRepresentation.value?.onSpeed(it) }
        options.skipN.observe { skipN: Int ->
            dduRepresentation.value?.let { dduRepresentation: DduRepresentation ->
                doSkipN(dduRepresentation, skipN)
            }
        }
    }

    private inline fun <T : Any> Option<T>.observe(crossinline action: (T) -> Unit) {
        liveData.observeForever { action(it) }
    }

    private fun <T : Any> setSharedPreference(option: Option<T>, value: T) {
        optionsManager.set(option, value)
    }

    private fun doSkipN(dduRepresentation: DduRepresentation, n: Int) {
        // TODO: do on cloned CircleGroup
        // FIX: update stat when partial skip
        if (n > 0) {
            viewModelScope.launch {
                Log.i(TAG, "Skipping $n updates... (timeout $SKIP_N_TIMEOUT_SECONDS s)")
                toast("Skipping $n updates... (timeout $SKIP_N_TIMEOUT_SECONDS s)")
                pause()
                val startTime = System.currentTimeMillis()
                withTimeoutOrNull(SKIP_N_TIMEOUT_MILLISECONDS) {
                    dduRepresentation.updateTimes(n)
                    updateStat(n)
                    val skippingTime = (System.currentTimeMillis() - startTime) / 1000f
                    Log.i(TAG, "Skipped $n updates within $skippingTime s")
                    toast("Skipped $n updates within $skippingTime s")
                } ?: run {
                    val skippingTime = (System.currentTimeMillis() - startTime) / 1000f
                    Log.w(TAG, "Skipping aborted due to timeout ($SKIP_N_TIMEOUT_SECONDS s > $skippingTime s)")
                    toast("Skipping aborted due to timeout ($SKIP_N_TIMEOUT_SECONDS s)")
                }
                setSharedPreference(options.skipN, 0)
                resume()
            }
        }
    }

    private suspend fun getInitialDdu(): Ddu =
        try {
            Ddu.fromFile(getRecentDduFile())
        } catch (e: Exception) {
            e.printStackTrace()
            Ddu.EXAMPLE_DDU
        }

    private fun getRecentDduFile(): File =
        context.dduDir/optionsManager.fetched(options.recentDdu)

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

    fun requestUpdateOnce() {
        dduRepresentation.value?.requestUpdateOnce()
    }

    /** Async, maybe schedule ddu saving, capture dduRepresentation */
    fun maybeAutosave() {
        if (values.autosave && dduRepresentation.value?.ddu?.file != null)
            viewModelScope.launch {
                saveDdu()
            }
    }

    private suspend fun saveDdu(file: File? = null) {
        dduRepresentation.value?.let { dduRepresentation: DduRepresentation ->
            pause()
            // BUG: CircleGroup parmas does not save
            val ddu: Ddu? = dduRepresentation.buildCurrentDdu()
            resume()
            ddu?.let {
                save(ddu, file)
            }
        }
    }

    private suspend fun save(ddu: Ddu, outputFile: File? = null) {
        val file: File? = outputFile ?: ddu.file
        if (file == null) {
            Log.i(TAG, "save: ddu has no file")
            // MAYBE: then save as
            context.toast(context.getString(R.string.error_ddu_save_no_file_toast))
        } else {
            try {
                Log.i(TAG, "Saving ddu at ${context.dduPath(file)}")
                ddu.saveToFile(file)
                context.toast(context.getString(R.string.ddu_saved_toast, context.dduPath(file)))
                // TODO: set original filename if inserting
                dduFileRepository.dropPreviewInserting(file.filename)
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
        val newUpdating = oldUpdating ?: DEFAULT_UPDATING
        setUpdating(newUpdating)
    }

    fun pause() {
        oldUpdating = updating.value
        setUpdating(false)
        maybeAutosave()
    }

    fun stop() {
        oldUpdating = null
        setUpdating(false)
        maybeAutosave()
    }

    fun toggleUpdating() {
        val newUpdating: Boolean = !(updating.value ?: DEFAULT_UPDATING)
        setUpdating(newUpdating)
    }

    private fun setUpdating(newUpdating: Boolean) {
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

    fun getDduFile(): File? =
        dduRepresentation.value?.ddu?.file

    fun getCircleGroup(): CircleGroup? =
        dduRepresentation.value?.circleGroup

    private fun updateDduAttributesFrom(dduRepresentation: DduRepresentation) {
        _updating.value = dduRepresentation.updating
        _drawTrace.value = dduRepresentation.drawTrace
        shape.value = dduRepresentation.shape
    }

    override fun updateStat(delta: Int) =
        statUpdater.updateStat(delta)

    private inner class StatUpdater : DduRepresentation.StatHolder {
        internal val statTimeDelta: Int = context.resources.getInteger(R.integer.stat_time_delta)
        private var nUpdates: Long by Delegates.observable(0L) { _, _, newNUpdates: Long ->
            _nUpdates.value = newNUpdates
        }
        private var lastUpdateTime: Long = 0
        private var lastTimedUpdate: Long = 0
        private var lastTimedUpdateTime: Long = System.currentTimeMillis()
        private var dTime: Float? by Delegates.observable<Float?>(null) { _, _, newDTime: Float? ->
            _dTime.value = newDTime
        }

        override fun updateStat(delta: Int) {
            lastUpdateTime = System.currentTimeMillis()
            val dNUpdates: Int = delta * (if (values.reverseMotion) -1 else 1)
            nUpdates += dNUpdates
            if (values.showStat) {
                val overhead: Long = abs(nUpdates - lastTimedUpdate)
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
            lastTimedUpdateTime = System.currentTimeMillis()
            dTime = null

        }
    }

    companion object {
        const val TAG = "DodecaViewModel"
        private const val DEFAULT_DRAW_TRACE = true
        private const val DEFAULT_UPDATING = true
        private val DEFAULT_SHAPE = Shape.CIRCLE
        private const val SKIP_N_TIMEOUT_SECONDS = 60
        private const val SKIP_N_TIMEOUT_MILLISECONDS: Long =
            1000L * SKIP_N_TIMEOUT_SECONDS
    }
}

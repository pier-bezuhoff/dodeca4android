package com.pierbezuhoff.dodeca.ui.dodecaview

import android.app.Application
import android.graphics.Canvas
import android.util.Log
import android.view.MotionEvent
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
import com.pierbezuhoff.dodeca.utils.div
import com.pierbezuhoff.dodeca.utils.filename
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.anko.toast
import java.io.File
import kotlin.math.abs
import kotlin.properties.Delegates

class DodecaViewModel(
    application: Application,
    optionsManager: OptionsManager
) : DodecaAndroidViewModelWithOptionsManager(application, optionsManager)
    , DodecaGestureDetector.SingleTapListener
    , DduRepresentation.StatHolder // by statUpdater
    , DduRepresentation.ToastEmitter
    , BottomBarHider
{
    private val _dduLoading: MutableLiveData<Boolean> = MutableLiveData(false)
    private val _dduRepresentation: MutableLiveData<DduRepresentation> = MutableLiveData()
    private val _updating: MutableLiveData<Boolean> = MutableLiveData()
    private val _drawTrace: MutableLiveData<Boolean> = MutableLiveData()
    private val _nUpdates: MutableLiveData<Long> = MutableLiveData(0L)
    private val _dTime: MutableLiveData<Float> = MutableLiveData()

    private var oldUpdating: Boolean? = null // [updating] before [pause]
    private var dduLoaded = false // mark that MainActivity should not repeat [loadInitialDdu]
    val dduLoading: LiveData<Boolean> = _dduLoading // for ProgressBar

    val dduRepresentation: LiveData<DduRepresentation> = _dduRepresentation
    val updating: LiveData<Boolean> = _updating
    val drawTrace: LiveData<Boolean> = _drawTrace
    val shape: MutableLiveData<Shape> = MutableLiveData(DEFAULT_SHAPE) // [shape] may be changed from DodecaViewActivity

    private val bottomBarHider: BottomBarHider = CoroutineBottomBarHider(viewModelScope)
    override val bottomBarShown: LiveData<Boolean> get() = bottomBarHider.bottomBarShown
    override fun showBottomBar() = bottomBarHider.showBottomBar()
    override fun hideBottomBar() = bottomBarHider.hideBottomBar()

    val showStat: LiveData<Boolean> = options.showStat.liveData
    private val statUpdater: StatUpdater = StatUpdater()
    override fun updateStat(delta: Int) = statUpdater.updateStat(delta)
    // following 3 are used to show stat[istics] in layout/activity_dodeca_view.xml
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
        gestureDetector
            .onSingleTapSubscription
            .subscribeFrom(this)
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
            setSharedPreference(options.recentDdu, dduFileService.dduPathOf(file))
        }
    }

    suspend fun loadDduFrom(file: File) {
        try {
            stop()
            _dduLoading.postValue(true)
            val ddu: Ddu = Ddu.fromFile(file)
            withContext(Dispatchers.Main) {
                loadDdu(ddu)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            formatToast(R.string.bad_ddu_format_toast, file.path)
        } finally {
            _dduLoading.postValue(false)
            resume()
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
                val timeoutSeconds = optionsManager.fetched(options.skipNTimeout)
                val timeoutMilliseconds: Long = timeoutSeconds * 1000L
                Log.i(TAG, "Skipping $n updates... (timeout $timeoutSeconds s)")
                toast("Skipping $n updates... (timeout $timeoutSeconds s)")
                pause()
                _dduLoading.postValue(true)
                val startTime = System.currentTimeMillis()
                withTimeoutOrNull(timeoutMilliseconds) {
                    dduRepresentation.updateTimes(n)
                    updateStat(n)
                    val skippingTime = (System.currentTimeMillis() - startTime) / 1000f
                    Log.i(TAG, "Skipped $n updates within $skippingTime s")
                    toast("Skipped $n updates within $skippingTime s")
                } ?: run {
                    val skippingTime = (System.currentTimeMillis() - startTime) / 1000f
                    Log.w(TAG, "Skipping aborted due to timeout ($timeoutSeconds s > $skippingTime s)")
                    toast("Skipping aborted due to timeout ($timeoutSeconds s)")
                }
                setSharedPreference(options.skipN, 0)
                _dduLoading.postValue(false)
                resume()
            }
        }
    }

    private suspend fun getInitialDdu(): Ddu {
        _dduLoading.postValue(true)
        val ddu = try {
            Ddu.fromFile(getRecentDduFile())
        } catch (e: Exception) {
            e.printStackTrace()
            Ddu.EXAMPLE_DDU
        }
        _dduLoading.postValue(false)
        return ddu
    }

    private fun getRecentDduFile(): File =
        dduFileService.dduDir/optionsManager.fetched(options.recentDdu)

    override fun onSingleTap(e: MotionEvent?) {
        toggleBottomBar()
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
            val ddu: Ddu? = dduRepresentation.buildCurrentDdu()
            ddu?.let {
                save(it, file)
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
                val dduFilename = dduFileService.dduPathOf(file)
                Log.i(TAG, "Saving ddu at $dduFilename")
                ddu.saveToFile(file)
                context.toast(context.getString(R.string.ddu_saved_toast, dduFilename))
                dduFileRepository.saveDerivative(source = ddu.file?.filename, target = file.filename)
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

    /** Resume ddu evolution after [pause] or [stop] */
    fun resume() {
        val newUpdating = oldUpdating ?: DEFAULT_UPDATING
        setUpdating(newUpdating)
        showBottomBar()
    }

    /** Pause ddu evolution (no autosave) */
    private fun _pause() {
        oldUpdating = updating.value
        setUpdating(false)
        hideBottomBar()
    }

    /** Pause ddu evolution and maybe autosave (can be [resume]d) */
    fun pause() {
        _pause()
        maybeAutosave()
    }

    /** Stop ddu evolution and maybe autosave */
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
        _updating.postValue(newUpdating)
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
        _updating.postValue(dduRepresentation.updating)
        _drawTrace.postValue(dduRepresentation.drawTrace)
        shape.postValue(dduRepresentation.shape)
    }


    fun applyInstantSettings(
        revertCurrentDdu: Boolean = false,
        revertAllDdus: Boolean = false,
        discardAllPreviews: Boolean = false
    ) {
        suspend fun revertCurrentDdu() {
            getDduFile()?.let { file: File ->
                dduFileService.extractDduAsset(file.filename, overwrite = true)
                loadDduFrom(file)
            }
        }
        suspend fun revertAllDdus() {
            dduFileService.extractDduAssets(overwrite = true)
            getDduFile()?.let { file: File ->
                loadDduFrom(file)
            }
        }
        suspend fun discardAllPreviews() =
            dduFileRepository.dropAllPreviews()

        viewModelScope.launch {
            if (revertCurrentDdu) revertCurrentDdu()
            if (revertAllDdus) revertAllDdus()
            if (discardAllPreviews) discardAllPreviews()
        }
    }

    private inner class StatUpdater : DduRepresentation.StatHolder {
        internal val statTimeDelta: Int = context.resources.getInteger(R.integer.stat_time_delta)
        private var nUpdates: Long by Delegates.observable(0L) { _, _, newNUpdates: Long ->
            _nUpdates.postValue(newNUpdates)
        }
        private var lastUpdateTime: Long = 0
        private var lastTimedUpdate: Long = 0
        private var lastTimedUpdateTime: Long = System.currentTimeMillis()
        @Suppress("RemoveExplicitTypeArguments") // do not compile without it...
        private var dTime: Float? by Delegates.observable<Float?>(null) { _, _, newDTime: Float? ->
            _dTime.postValue(newDTime)
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
        private const val TAG = "DodecaViewModel"
        private const val DEFAULT_DRAW_TRACE = true
        private const val DEFAULT_UPDATING = true
        private val DEFAULT_SHAPE = Shape.CIRCLE
    }
}

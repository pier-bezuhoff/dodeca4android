package com.pierbezuhoff.dodeca.ui.dodecaedit

import android.app.Application
import android.graphics.Canvas
import android.util.Log
import android.view.MotionEvent
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.pierbezuhoff.dodeca.R
import com.pierbezuhoff.dodeca.data.Ddu
import com.pierbezuhoff.dodeca.data.Option
import com.pierbezuhoff.dodeca.data.Shape
import com.pierbezuhoff.dodeca.data.circlegroup.CircleGroup
import com.pierbezuhoff.dodeca.models.DduRepresentation
import com.pierbezuhoff.dodeca.models.OptionsManager
import com.pierbezuhoff.dodeca.ui.dodecaview.BottomBarHider
import com.pierbezuhoff.dodeca.ui.dodecaview.CoroutineBottomBarHider
import com.pierbezuhoff.dodeca.ui.dodecaview.DodecaGestureDetector
import com.pierbezuhoff.dodeca.ui.meta.DodecaAndroidViewModelWithOptions
import com.pierbezuhoff.dodeca.utils.ComplexFF
import com.pierbezuhoff.dodeca.utils.div
import com.pierbezuhoff.dodeca.utils.filename
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.anko.toast
import java.io.File

class DodecaEditViewModel(
    application: Application,
    optionsManager: OptionsManager
) : DodecaAndroidViewModelWithOptions(application, optionsManager)
    , DodecaGestureDetector.SingleTapListener
    , DodecaGestureDetector.ScrollListener
    , DodecaGestureDetector.ScaleListener
    , DduRepresentation.ToastEmitter
    , BottomBarHider
{
    private val _dduLoading: MutableLiveData<Boolean> = MutableLiveData(false)
    private val _dduRepresentation: MutableLiveData<DduRepresentation> = MutableLiveData()
    private val _updating: MutableLiveData<Boolean> = MutableLiveData()
    private val _editingMode: MutableLiveData<EditingMode> = MutableLiveData(DEFAULT_EDITING_MODE)
    private val _showEverything: MutableLiveData<Boolean> = MutableLiveData(DEFAULT_SHOW_EVERYTHING)
    private val _selection: MutableLiveData<Set<Int>> = MutableLiveData()

    private var oldUpdating: Boolean? = null // [updating] before [pause]
    private var oldDrawTrace: Boolean? = null // drawTrace in the NAVIGATE mode
    private var dduLoaded = false
    val dduLoading: LiveData<Boolean> = _dduLoading // for ProgressBar

    val dduRepresentation: LiveData<DduRepresentation> = _dduRepresentation
    val updating: LiveData<Boolean> = _updating
    val editingMode: LiveData<EditingMode> = _editingMode
    val showEverything: LiveData<Boolean> = _showEverything
    val selection: LiveData<Set<Int>> = _selection

    private val bottomBarHider: BottomBarHider = CoroutineBottomBarHider(viewModelScope)
    override val bottomBarShown: LiveData<Boolean> get() = bottomBarHider.bottomBarShown
    override fun showBottomBar() = bottomBarHider.showBottomBar()
    override fun hideBottomBar() = bottomBarHider.hideBottomBar()

    private val threshold: Double = 25.0 // TODO: adjust

    private var oldForceRedraw: Boolean? = null // original redraw trace on move value

    val gestureDetector: DodecaGestureDetector = DodecaGestureDetector.get(context)

    init {
        registerOptionsObservers()
        optionsManager.fetchAll()
        showEverything.observeForever {
            dduRepresentation.value?.showEverything = it
        }
        selection.observeForever {
            dduRepresentation.value?.selectedCircles = it.toIntArray()
        }
        gestureDetector.onSingleTapSubscription.subscribeFrom(this)
        gestureDetector.onScrollSubscription.subscribeFrom(this)
        gestureDetector.onScaleSubscription.subscribeFrom(this)
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
        DduRepresentation(ddu, optionsManager)
            .let { dduRepresentation: DduRepresentation ->
                dduRepresentation.toastEmitterSubscription.subscribeFrom(this)
                updateDduAttributesFrom(dduRepresentation)
                _dduRepresentation.value = dduRepresentation // invoke DodecaView observer
                _editingMode.value = DEFAULT_EDITING_MODE
                _showEverything.value = DEFAULT_SHOW_EVERYTHING
                _selection.value = emptySet()
            }
        ddu.file?.let { file: File ->
            setSharedPreference(optionsManager.options.recentDdu, dduFileService.dduPathOf(file))
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
        with (optionsManager.options) {
            autocenterAlways.observe {
                dduRepresentation.value?.onAutocenterAlways(it)
            }
            canvasFactor.observe { dduRepresentation.value?.onCanvasFactor(it) }
            speed.observe { dduRepresentation.value?.onSpeed(it) }
            skipN.observe { skipN: Int ->
                dduRepresentation.value?.let { dduRepresentation: DduRepresentation ->
                    doSkipN(dduRepresentation, skipN)
                }
            }
        }
    }

    fun registerOptionsObserversIn(owner: LifecycleOwner) {
        with (optionsManager.options) {
            angularSpeedFactor.liveData.observe(owner) { factor: Float ->
                dduRepresentation.value?.let { dduR: DduRepresentation ->
                    if (factor != 1f) {
                        dduR.circleGroup.changeAngularSpeed(factor)
                        optionsManager.set(angularSpeedFactor, 1f)
                    }
                }
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
        // TODO: do on a cloned CircleGroup
        if (n > 0) {
            viewModelScope.launch {
                val timeoutSeconds = optionsManager.run { fetched(options.skipNTimeout) }
                val timeoutMilliseconds: Long = timeoutSeconds * 1000L
                Log.i(TAG, "Skipping $n updates... (timeout $timeoutSeconds s)")
                toast("Skipping $n updates... (timeout $timeoutSeconds s)")
                pause()
                _dduLoading.postValue(true)
                val startTime = System.currentTimeMillis()
                withTimeoutOrNull(timeoutMilliseconds) {
                    dduRepresentation.updateTimes(n)
                    val skippingTime = (System.currentTimeMillis() - startTime) / 1000f
                    Log.i(TAG, "Skipped $n updates within $skippingTime s")
                    toast("Skipped $n updates within $skippingTime s")
                } ?: run {
                    val skippingTime = (System.currentTimeMillis() - startTime) / 1000f
                    Log.w(TAG, "Skipping aborted due to timeout ($timeoutSeconds s > $skippingTime s)")
                    toast("Skipping aborted due to timeout ($timeoutSeconds s)")
                }
                setSharedPreference(optionsManager.options.skipN, 0)
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
        dduFileService.dduDir/optionsManager.fetched(optionsManager.options.recentDdu)

    override fun onSingleTap(e: MotionEvent?) {
        val mode = editingMode.value
        val ddu = dduRepresentation.value
        val showEverything = showEverything.value
        if (
            e != null &&
            mode in listOf(EditingMode.MULTISELECT, EditingMode.COPY) &&
            ddu != null &&
            showEverything != null
        ) {
            val p = ComplexFF(e.x, e.y)
            val selectedCircles = ddu.selectCircles(p, threshold = threshold, amongHidden = showEverything)
            if (selectedCircles.isNotEmpty())
                when (mode) {
                    EditingMode.MULTISELECT -> {
                        _selection.postValue((selection.value ?: emptySet()) + selectedCircles)
                    }
                    EditingMode.COPY -> 3 // copy-paste new
                    else -> Unit
                }
            showBottomBar()
        } else {
            toggleBottomBar()
        }
    }

    override fun onScroll(fromX: Float, fromY: Float, dx: Float, dy: Float) {
        val v = ComplexFF(-dx, -dy)
        dduRepresentation.value?.let { ddu ->
            when (editingMode.value) {
                EditingMode.NAVIGATE, EditingMode.COPY -> ddu.onScroll(fromX, fromY, dx, dy)
                EditingMode.MULTISELECT -> {
                    val selectedCircles = selection.value ?: emptyList()
                    if (selectedCircles.isEmpty())
                        ddu.onScroll(fromX, fromY, dx, dy)
                    else
                        for (i in selectedCircles)
                            ddu.moveCircle(i, v)
                }
                EditingMode.NAVIGATE_3D -> Unit // TODO
                EditingMode.ROTATE_3D -> Unit
                else -> Unit
            }
        }
    }

    override fun onScale(scale: Float, focusX: Float, focusY: Float) {
        val p = ComplexFF(focusX, focusY)
        val s = scale.toDouble()
        dduRepresentation.value?.let { ddu ->
            when (editingMode.value) {
                EditingMode.NAVIGATE, EditingMode.COPY -> ddu.onScale(scale, focusX, focusY)
                EditingMode.MULTISELECT -> {
                    val selectedCircles = selection.value ?: emptyList()
                    if (selectedCircles.isEmpty())
                        ddu.onScale(scale, focusX, focusY)
                    else
                        for (i in selectedCircles)
                            ddu.changeCircleRadius(i, scale = s)
                    // TODO: also add an option to scale the whole group rel. to the *focus*
                }
                EditingMode.NAVIGATE_3D -> Unit //  convert to Oz movements
                EditingMode.ROTATE_3D -> Unit
                else -> Unit
            }
        }
    }

    fun requestEditingMode(mode: EditingMode) {
        if (editingMode.value == EditingMode.NAVIGATE && mode != EditingMode.NAVIGATE) {
            oldDrawTrace = dduRepresentation.value?.drawTrace
            dduRepresentation.value?.drawTrace = false
        } else if (mode == EditingMode.NAVIGATE) {
            oldDrawTrace?.let {
                dduRepresentation.value?.drawTrace = it
            }
        }
        if (mode == EditingMode.NAVIGATE) {
            resume()
        }
        if (mode == EditingMode.MULTISELECT && editingMode.value == EditingMode.MULTISELECT)
            _selection.value = emptySet() // double click unselects everything
        if (mode == EditingMode.MULTISELECT) {
            _showEverything.value = true
            _pause()
            toast("select & drag circles")
        }
        val modes3d = listOf(EditingMode.NAVIGATE_3D, EditingMode.ROTATE_3D)
        if (mode in modes3d && editingMode.value !in modes3d) {
            when (mode) {
                EditingMode.NAVIGATE_3D ->
                    dduRepresentation.value?.switchMode(DduRepresentation.Mode.MODE_3D_NAVIGATE)
                EditingMode.ROTATE_3D ->
                    dduRepresentation.value?.switchMode(DduRepresentation.Mode.MODE_3D_ROTATE)
                else -> Unit // never
            }
        } else if (mode !in modes3d && editingMode.value in modes3d) {
            dduRepresentation.value?.switchMode(DduRepresentation.Mode.MODE_2D)
        }
        _editingMode.value = mode
        showBottomBar()
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
        if (optionsManager.values.autosave && dduRepresentation.value?.ddu?.file != null)
            viewModelScope.launch {
                saveDdu()
            }
    }

    private suspend fun saveDdu(file: File? = null) {
        dduRepresentation.value?.let { dduRepresentation: DduRepresentation ->
            val ddu: Ddu = dduRepresentation.buildCurrentDduState()
            oldDrawTrace?.let { drawTrace ->
                ddu.drawTrace = drawTrace
            }
            save(ddu, file)
        }
    }

    private suspend fun save(ddu: Ddu, outputFile: File? = null) {
        val file: File? = outputFile ?: ddu.file
        if (file == null) {
            // MAYBE: then save as
            context.toast(context.getString(R.string.error_ddu_save_no_file_toast))
        } else {
            try {
                val dduFilename = dduFileService.dduPathOf(file)
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

    fun resume() {
        val newUpdating = oldUpdating ?: DEFAULT_UPDATING
        setUpdating(newUpdating)
    }

    private fun _pause() {
        oldUpdating = updating.value
        setUpdating(false)
    }

    fun pause() {
        _pause()
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
        _updating.postValue(newUpdating)
        dduRepresentation.value?.updating = newUpdating
    }

    fun toggleShowEverything() {
        val newShowEverything: Boolean = !(showEverything.value ?: DEFAULT_SHOW_EVERYTHING)
        _showEverything.postValue(newShowEverything)
    }

    fun onDraw(canvas: Canvas) =
        dduRepresentation.value?.draw(canvas)

    fun getDduFile(): File? =
        dduRepresentation.value?.ddu?.file

    fun getCircleGroup(): CircleGroup? =
        dduRepresentation.value?.circleGroup

    private fun updateDduAttributesFrom(dduRepresentation: DduRepresentation) {
        _updating.postValue(dduRepresentation.updating)
    }

    fun applyInstantSettings(
        revertCurrentDdu: Boolean = false,
        revertAllDdus: Boolean = false,
        discardAllPreviews: Boolean = false,
        updateCircleGroup: Boolean = false
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

        fun updateCircleGroup() {
            optionsManager.run {
                fetch(options.circleGroupImplementation)
                fetch(options.projR)
            }
            dduRepresentation.value?.updateCircleGroup()
        }

        viewModelScope.launch {
            if (revertCurrentDdu) revertCurrentDdu()
            if (revertAllDdus) revertAllDdus()
            if (discardAllPreviews) discardAllPreviews()
            if (updateCircleGroup) updateCircleGroup()
        }
    }
    fun overwriteForceRedraw() {
        optionsManager.run {
            oldForceRedraw = values.redrawTraceOnMove
            set(options.redrawTraceOnMove, true)
        }
    }

    fun restoreForceRedraw() {
        optionsManager.run {
            oldForceRedraw?.let { old ->
                set(options.redrawTraceOnMove, old)
            }
        }
    }

    companion object {
        private const val TAG = "DodecaEditViewModel"
        private const val DEFAULT_DRAW_TRACE = true
        private const val DEFAULT_UPDATING = true
        private val DEFAULT_SHAPE = Shape.CIRCLE
        private val DEFAULT_EDITING_MODE = EditingMode.NAVIGATE
        private val DEFAULT_SHOW_EVERYTHING = false
    }
}

enum class EditingMode {
    NAVIGATE, MULTISELECT, COPY, NAVIGATE_3D, ROTATE_3D
}
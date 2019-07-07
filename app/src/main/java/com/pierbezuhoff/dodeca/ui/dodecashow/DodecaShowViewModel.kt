package com.pierbezuhoff.dodeca.ui.dodecashow

import android.app.Application
import android.graphics.Canvas
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.pierbezuhoff.dodeca.models.DduRepresentation
import com.pierbezuhoff.dodeca.models.OptionsManager
import com.pierbezuhoff.dodeca.ui.meta.DodecaAndroidViewModelWithOptionsManager
import com.pierbezuhoff.dodeca.utils.Once
import kotlinx.coroutines.launch
import org.jetbrains.anko.toast
import java.io.File

class DodecaShowViewModel(
    application: Application,
    optionsManager: OptionsManager
) : DodecaAndroidViewModelWithOptionsManager(application, optionsManager)
    , DodecaShowGestureDetector.SingleTapListener
    , DodecaShowGestureDetector.DoubleTapListener
    , DodecaShowGestureDetector.SwipeListener
    , DduRepresentation.ToastEmitter
{
    private val _dduLoading: MutableLiveData<Boolean> = MutableLiveData(false)
    private val _dduRepresentation: MutableLiveData<DduRepresentation> = MutableLiveData()

    private val firstTargetDir by Once(true)
    private lateinit var targetDir: File
    private val firstTargetFile by Once(true)
    private lateinit var targetFile: File
    private lateinit var dduFileRing: DduFileRing

    private var updating = false
    val dduLoading: LiveData<Boolean> = _dduLoading // for ProgressBar

    val dduRepresentation: LiveData<DduRepresentation> = _dduRepresentation
    // show top bar first 3 s (option)

    val gestureDetector: DodecaShowGestureDetector = DodecaShowGestureDetector.get(context)

    init {
        gestureDetector.onSingleTapSubscription.subscribeFrom(this)
        gestureDetector.onDoubleTapSubscription.subscribeFrom(this)
    }

    fun setInitialTargetDir(dir: File) {
        if (firstTargetDir) {
            targetDir = dir
            dduFileRing = DduFileRing(dir, scope = viewModelScope)
        }
    }

    fun setInitialTargetFile(file: File) {
        if (firstTargetFile) {
            setTargetFile(file)
        }
    }

    fun createRing() {
        setInitialTargetDir(dir)
        setInitialTargetFile(targetDir.listFiles()[0])
    }

    private fun setTargetFile(file: File) {
        targetFile = file
        viewModelScope.launch {
            // NOTE: [this@DodecaShowViewModel] leaks!
            _dduLoading.postValue(true)
            val ddu = dduFileRing.setHead(file)
            val dduRepresentation = DduRepresentation(ddu)
            dduRepresentation.connectOptionsManager(optionsManager)
            dduRepresentation.toastEmitterSubscription.subscribeFrom(this@DodecaShowViewModel)
            _dduRepresentation.value = dduRepresentation
            _dduLoading.postValue(false)
            updating = true
            Log.d(TAG, "show title 3 s")
        }
    }

    fun resume() {
        require(!updating)
        updating = true
        _dduRepresentation.value?.updating = true
    }

    fun pause() {
        require(updating)
        updating = false
        _dduRepresentation.value?.updating = false
    }

    override fun onSingleTap() {
        toggleUpdating()
        Log.d(TAG, "show play/pause sign")
    }

    private fun toggleUpdating() {
        updating = !updating
        _dduRepresentation.value?.updating = updating
    }

    override fun onDoubleTap() {
        Log.d(TAG, "go to DodecaViewActivity")
    }

    override fun onSwipe(velocityX: Float, velocityY: Float) {
        _dduRepresentation.value?.presenter?.getSize()?.also { (width: Int, height: Int) ->
            val verticalThreshold = VERTICAL_SWIPE_RATIO_PER_SECOND * height
            val horizontalThreshold = HORIZONTAL_SWIPE_RATIO_PER_SECOND * width
            when {
                velocityY > verticalThreshold ->
                    Log.d(TAG, "show")
                velocityY < -verticalThreshold ->
                    Log.d(TAG, "hide title")
                velocityX > horizontalThreshold ->
                    Log.d(TAG, "next file")
                velocityX < -horizontalThreshold ->
                    Log.d(TAG, "previous file")
            }
        }
    }

    override fun toast(message: CharSequence) { context.toast(message) }
    override fun formatToast(id: Int, vararg args: Any) { context.toast(context.getString(id, *args)) }

    fun onDraw(canvas: Canvas) {
        _dduRepresentation.value?.draw(canvas)
    }

    companion object {
        private const val TAG = "DodecaShowViewModel"
        // NOTE: experimental
        private const val VERTICAL_SWIPE_RATIO_PER_SECOND = 0.1
        private const val HORIZONTAL_SWIPE_RATIO_PER_SECOND = 0.1
    }
}
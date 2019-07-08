package com.pierbezuhoff.dodeca.ui.dodecashow

import android.app.Application
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.pierbezuhoff.dodeca.data.Ddu
import com.pierbezuhoff.dodeca.data.values
import com.pierbezuhoff.dodeca.models.DduRepresentation
import com.pierbezuhoff.dodeca.models.OptionsManager
import com.pierbezuhoff.dodeca.ui.meta.DodecaAndroidViewModelWithOptionsManager
import com.pierbezuhoff.dodeca.ui.meta.MetaDodecaView
import com.pierbezuhoff.dodeca.utils.Once
import kotlinx.coroutines.Deferred
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
    , MetaDodecaView.MetaDodecaViewModel
{
    private val _dduLoading: MutableLiveData<Boolean> = MutableLiveData(false)
    private val _playing: MutableLiveData<Boolean> = MutableLiveData(true)
    private val _toolbarShown: MutableLiveData<Boolean> = MutableLiveData(true)
    private val _dduRepresentation: MutableLiveData<DduRepresentation> = MutableLiveData()

    private val firstTargetDir by Once(true)
    private lateinit var targetDir: File
    private val firstTargetFile by Once(true)
    private lateinit var dduFileRing: DduFileRing

    private var updating = false
    val playing: LiveData<Boolean> = _playing // for ImageView
    val toolbarShown: LiveData<Boolean> = _toolbarShown // TODO: change
    val dduLoading: LiveData<Boolean> = _dduLoading // for ProgressBar

    val file: File get() = dduFileRing.currentHead
    override val dduRepresentation: LiveData<DduRepresentation> = _dduRepresentation

    override val gestureDetector: DodecaShowGestureDetector = DodecaShowGestureDetector.get(context)

    init {
        gestureDetector.onSingleTapSubscription.subscribeFrom(this)
        gestureDetector.onDoubleTapSubscription.subscribeFrom(this)
        gestureDetector.onSwipeSubscription.subscribeFrom(this)
    }

    fun setInitialTargetDir(dir: File) {
        if (firstTargetDir) {
            require(dir.exists())
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
        val recentFile = dduFileService.dduFile(values.recentDdu)
        val file = if (recentFile in dduFileRing.files) recentFile else dduFileRing.files.first()
        setInitialTargetFile(file)
    }

    private fun setTargetFile(file: File) {
        representDeferredDdu(dduFileRing.setHeadAsync(file))
    }

    private fun representDeferredDdu(deferredDdu: Deferred<Ddu>) {
        viewModelScope.launch {
            // NOTE: [this@DodecaShowViewModel] leaks!
            _dduLoading.postValue(true)
            val ddu = deferredDdu.await()
            val dduRepresentation = DduRepresentation(ddu)
            dduRepresentation.connectOptionsManager(optionsManager)
            dduRepresentation.toastEmitterSubscription.subscribeFrom(this@DodecaShowViewModel)
            _dduRepresentation.value = dduRepresentation
            _dduLoading.postValue(false)
            updating = true
            Log.i(TAG, "show title ~3s")
        }
    }

    fun resume() {
        require(!updating)
        setUpdating(true)
    }

    fun pause() {
        require(updating)
        setUpdating(false)
    }

    private fun setUpdating(newUpdating: Boolean) {
        updating = newUpdating
        _playing.postValue(newUpdating)
        _dduRepresentation.value?.updating = newUpdating
    }

    override fun onSingleTap() {
        toggleUpdating()
    }

    private fun toggleUpdating() {
        setUpdating(!updating)
    }

    override fun onDoubleTap() {
        Log.i(TAG, "go to DodecaViewActivity")
    }

    override fun onSwipe(velocityX: Float, velocityY: Float) {
        _dduRepresentation.value?.presenter?.getSize()?.also { (width: Int, height: Int) ->
            Log.i(TAG, "swipe: $velocityX / $width, $velocityY / $height")
            val verticalThreshold = VERTICAL_SWIPE_RATIO_PER_SECOND * height
            val horizontalThreshold = HORIZONTAL_SWIPE_RATIO_PER_SECOND * width
            when {
                velocityX > horizontalThreshold ->
                    nextFile()
                velocityX < -horizontalThreshold ->
                    previousFile()
                velocityY > verticalThreshold ->
                    Log.i(TAG, "show title")
                velocityY < -verticalThreshold ->
                    Log.i(TAG, "hide title")
            }
        }
    }

    private fun nextFile() {
        representDeferredDdu(dduFileRing.nextHeadAsync())
    }

    private fun previousFile() {
        representDeferredDdu(dduFileRing.previousHeadAsync())
    }

    override fun toast(message: CharSequence) { context.toast(message) }
    override fun formatToast(id: Int, vararg args: Any) { context.toast(context.getString(id, *args)) }

    companion object {
        private const val TAG = "DodecaShowViewModel"
        // NOTE: experimental
        private const val VERTICAL_SWIPE_RATIO_PER_SECOND = 2
        private const val HORIZONTAL_SWIPE_RATIO_PER_SECOND = 2
    }
}
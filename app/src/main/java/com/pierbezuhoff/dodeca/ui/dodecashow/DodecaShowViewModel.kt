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
import com.pierbezuhoff.dodeca.ui.meta.DoubleTapListener
import com.pierbezuhoff.dodeca.ui.meta.MetaDodecaView
import com.pierbezuhoff.dodeca.ui.meta.SingleTapListener
import com.pierbezuhoff.dodeca.ui.meta.SwipeListener
import com.pierbezuhoff.dodeca.utils.Once
import com.pierbezuhoff.dodeca.utils.fileName
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs

class DodecaShowViewModel(
    application: Application,
    optionsManager: OptionsManager
) : DodecaAndroidViewModelWithOptionsManager(application, optionsManager)
    , MetaDodecaView.MetaDodecaViewModel
    , SingleTapListener
    , DoubleTapListener // unused for now
    , SwipeListener
    , AppBarHider // by [appBarHider]
{
    private val _dduLoading: MutableLiveData<Boolean> = MutableLiveData(false)
    private val _playing: MutableLiveData<Boolean> = MutableLiveData(true)
    private val _dduRepresentation: MutableLiveData<DduRepresentation> = MutableLiveData()

    private val firstTargetDir by Once(true)
    private lateinit var targetDir: File
    private val firstTargetFile by Once(true)
    private lateinit var dduFileRing: DduFileRing

    private val appBarHider: AppBarHider = CoroutineAppBarHider(viewModelScope)
    override val appBarShown = appBarHider.appBarShown
    override fun showAppBar(timeoutSeconds: Int?) = appBarHider.showAppBar(timeoutSeconds)
    override fun hideAppBar() = appBarHider.hideAppBar()

    private val _title: MutableLiveData<String> = MutableLiveData()
    val title: LiveData<String> = _title

    private var updating = false
    val playing: LiveData<Boolean> = _playing // for ImageView
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
            _title.postValue(file.fileName.toString())
            showAppBar(timeoutSeconds = APPBAR_SHOW_TIMEOUT_SECONDS)
        }
    }

    fun resume() {
        setUpdating(true)
    }

    fun pause() {
        setUpdating(false)
    }

    private fun setUpdating(newUpdating: Boolean) {
        updating = newUpdating
        _playing.postValue(newUpdating)
        _dduRepresentation.value?.updating = newUpdating
        if (updating)
            hideAppBar()
        else
            showAppBar()
    }

    override fun onSingleTap() {
        toggleUpdating()
    }

    private fun toggleUpdating() {
        setUpdating(!updating)
    }

    override fun onDoubleTap() {
        // MAYBE: choose current file or smth.
    }

    override fun onSwipe(velocityX: Float, velocityY: Float) {
        _dduRepresentation.value?.presenter?.getSize()?.also { (width: Int, height: Int) ->
            Log.i(TAG, "swipe: $velocityX / $width, $velocityY / $height")
            val verticalThreshold = VERTICAL_SWIPE_RATIO_PER_SECOND * height
            val horizontalThreshold = HORIZONTAL_SWIPE_RATIO_PER_SECOND * width
            if (abs(velocityX) > SWIPE_DISTINGUISHING_RATIO * abs(velocityY))
                when {
                    velocityX > horizontalThreshold ->
                        previousFile()
                    velocityX < -horizontalThreshold ->
                        nextFile()
                }
            if (abs(velocityY) > SWIPE_DISTINGUISHING_RATIO * abs(velocityX))
                when {
                    velocityY > verticalThreshold ->
                        showAppBar().also { Log.i(TAG, "showAppBar") }
                    velocityY < -verticalThreshold ->
                        hideAppBar().also { Log.i(TAG, "hideAppBar") }
                }
        }
    }

    private fun nextFile() {
        Log.i(TAG, "nextFile")
        representDeferredDdu(dduFileRing.nextHeadAsync())
    }

    private fun previousFile() {
        Log.i(TAG, "previousFile")
        representDeferredDdu(dduFileRing.previousHeadAsync())
    }

    companion object {
        private const val TAG = "DodecaShowViewModel"
        private const val APPBAR_SHOW_TIMEOUT_SECONDS = 3
        // NOTE: experimental
        private const val VERTICAL_SWIPE_RATIO_PER_SECOND = 2
        private const val HORIZONTAL_SWIPE_RATIO_PER_SECOND = 2
        private const val SWIPE_DISTINGUISHING_RATIO = 1.1f
    }
}
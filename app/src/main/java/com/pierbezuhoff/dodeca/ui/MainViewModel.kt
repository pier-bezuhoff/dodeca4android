package com.pierbezuhoff.dodeca.ui

import android.app.Application
import android.util.Log
import android.view.MotionEvent
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.pierbezuhoff.dodeca.BuildConfig
import com.pierbezuhoff.dodeca.R
import com.pierbezuhoff.dodeca.data.options
import com.pierbezuhoff.dodeca.models.OptionsManager
import com.pierbezuhoff.dodeca.ui.dduchooser.DduChooserActivity
import com.pierbezuhoff.dodeca.ui.dodeca.DodecaGestureDetector
import com.pierbezuhoff.dodeca.ui.meta.DodecaAndroidViewModelWithOptionsManager
import com.pierbezuhoff.dodeca.utils.Connection
import com.pierbezuhoff.dodeca.utils.Filename
import com.pierbezuhoff.dodeca.utils.dduDir
import com.pierbezuhoff.dodeca.utils.extractDduFrom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class MainViewModel(
    application: Application,
    optionsManager: OptionsManager
) : DodecaAndroidViewModelWithOptionsManager(application, optionsManager)
    , DodecaGestureDetector.SingleTapListener
    , DduChooserActivity.DduFileChooser
{
    interface OnDestroyMainActivity { fun onDestroyMainActivity() }
    private val onDestroyMainActivityConnection = Connection<OnDestroyMainActivity>()
    val onDestroyMainActivitySubscription = onDestroyMainActivityConnection.subscription

    private val _bottomBarShown: MutableLiveData<Boolean> = MutableLiveData(true)
    private val _dir: MutableLiveData<File> = MutableLiveData(context.dduDir)
    val currentDir: File get() = dir.value!!
    private val _showStat: MutableLiveData<Boolean> = MutableLiveData(false)
    private var bottomBarHidingJob: Job? = null
    var chosenDduFile: File? = null
        get() = field?.also { field = null }
        private set

    val bottomBarShown: LiveData<Boolean> = _bottomBarShown
    val dir: LiveData<File> = _dir
    val showStat: LiveData<Boolean> = _showStat

    init {
        DodecaGestureDetector.get(context)
            .onSingleTapSubscription
            .subscribeFrom(this)
        _bottomBarShown.observeForever {
            when(it) {
                true -> hideBottomBarAfterTimeout()
                false -> cancelBottomBarHidingJob()
            }
        }
        options.showStat.liveData.observeForever {
            _showStat.postValue(it)
        }
        if (_dir.value == null)
            _dir.value = context.dduDir
    }

    override fun onSingleTap(e: MotionEvent?) {
        toggleBottomBar()
    }

    override fun chooseDduFile(file: File) {
        chosenDduFile = file
    }

    fun checkUpgrade() {
        val currentVersionCode = BuildConfig.VERSION_CODE
        val oldVersionCode = optionsManager.fetched(options.versionCode)
        if (oldVersionCode != currentVersionCode) {
            val upgrading: Boolean = oldVersionCode < currentVersionCode
            val upgradingOrDegrading: String = if (upgrading) "Upgrading" else "Degrading"
            val currentVersionName: String = BuildConfig.VERSION_NAME
            val versionCodeChange = "$oldVersionCode -> $currentVersionCode"
            Log.i(TAG,"$upgradingOrDegrading to $currentVersionName ($versionCodeChange)")
            optionsManager.set(options.versionCode, currentVersionCode)
            runBlocking {
                viewModelScope.launch {
                    onUpgrade()
                }
            }
        }
    }

    fun showBottomBar() =
        _bottomBarShown.postValue(true)

    fun hideBottomBar() =
        _bottomBarShown.postValue(false)

    private fun hideBottomBarAfterTimeout() {
        Log.i(TAG, "hideBottomBarAfterTimeout")
        bottomBarHidingJob?.cancel()
        bottomBarHidingJob = viewModelScope.launch(Dispatchers.Default) {
            delay(BOTTOM_BAR_HIDE_DELAY_IN_MILLISECONDS)
            hideBottomBar()
        }
    }

    fun cancelBottomBarHidingJob() {
        Log.i(TAG, "cancelBottomBarHidingJob")
        bottomBarHidingJob?.cancel()
    }

    fun restartBottomBarHidingJobIfShown() {
        if (bottomBarShown.value == true)
            hideBottomBarAfterTimeout()
    }

    fun toggleBottomBar() {
        _bottomBarShown.value = !(_bottomBarShown.value ?: false)
    }

    fun sendOnDestroy() {
        onDestroyMainActivityConnection.send { onDestroyMainActivity() }
    }

    fun changeDir(dir: File) {
        _dir.value = dir
    }

    private suspend fun onUpgrade() {
        // extracting assets
        withContext(Dispatchers.IO) {
            try {
                if (!currentDir.exists()) {
                    Log.i(TAG, "Extracting all assets into $currentDir")
                    currentDir.mkdir()
                    extractDdusFromAssets()
                } else {
                    // TODO: check it
                    // try to export new ddus
                    Log.i(TAG, "Adding new ddu assets into $currentDir")
                    val existedDdus = currentDir.listFiles().map { it.name }.toSet()
                    context.assets
                        .list(context.getString(R.string.ddu_asset_dir))
                        ?.filter { it !in existedDdus }
                        ?.forEach { name ->
                            extractDduFrom(Filename(name), currentDir)
                        }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    suspend fun extractDdusFromAssets(overwrite: Boolean = false) {
        withContext(Dispatchers.IO) {
            val targetDir = currentDir
            context.assets
                .list(context.getString(R.string.ddu_asset_dir))
                ?.forEach { name ->
                    extractDduFrom(Filename(name), targetDir, overwrite)
                }
        }
    }

    suspend fun extractDduFrom(filename: Filename, dir: File = currentDir, overwrite: Boolean = false) =
        context.extractDduFrom(filename, dir, dduFileRepository,
            TAG, overwrite)

    companion object {
        private const val TAG = "MainViewModel"
        private const val BOTTOM_BAR_HIDE_DELAY_IN_SECONDS = 30
        private const val BOTTOM_BAR_HIDE_DELAY_IN_MILLISECONDS: Long =
            1000L * BOTTOM_BAR_HIDE_DELAY_IN_SECONDS
    }
}
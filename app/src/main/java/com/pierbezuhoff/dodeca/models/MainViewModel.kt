package com.pierbezuhoff.dodeca.models

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.pierbezuhoff.dodeca.data.options
import com.pierbezuhoff.dodeca.utils.Connection
import com.pierbezuhoff.dodeca.utils.dduDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(application: Application) : DodecaAndroidViewModel(application) {
    interface OnDestroyMainActivity { fun onDestroyMainActivity() }
    private val onDestroyMainActivityConnection = Connection<OnDestroyMainActivity>()
    val onDestroyMainActivitySubscription = onDestroyMainActivityConnection.subscription

    private val _bottomBarShown: MutableLiveData<Boolean> = MutableLiveData(true)
    private val _dir: MutableLiveData<File> = MutableLiveData()
    private val _showStat: MutableLiveData<Boolean> = MutableLiveData(false)
    private var bottomBarHidingJob: Job? = null

    val bottomBarShown: LiveData<Boolean> = _bottomBarShown
    val dir: LiveData<File> = _dir
    val showStat: LiveData<Boolean> = _showStat

    init {
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

    fun showBottomBar() =
        _bottomBarShown.postValue(true)

    fun hideBottomBar() =
        _bottomBarShown.postValue(false)

    private fun hideBottomBarAfterTimeout() {
        bottomBarHidingJob?.cancel()
        bottomBarHidingJob = viewModelScope.launch(Dispatchers.Default) {
            delay(BOTTOM_BAR_HIDE_DELAY_IN_MILLISECONDS)
            hideBottomBar()
        }
    }

    fun cancelBottomBarHidingJob() {
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

    companion object {
        private const val TAG = "MainViewModel"
        private const val BOTTOM_BAR_HIDE_DELAY_IN_SECONDS = 30
        private const val BOTTOM_BAR_HIDE_DELAY_IN_MILLISECONDS: Long =
            1000L * BOTTOM_BAR_HIDE_DELAY_IN_SECONDS
    }
}
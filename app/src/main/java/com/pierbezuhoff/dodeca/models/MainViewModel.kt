package com.pierbezuhoff.dodeca.models

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.pierbezuhoff.dodeca.data.options
import com.pierbezuhoff.dodeca.utils.FlexibleTimer
import java.io.File

class MainViewModel : ViewModel() {
    private val _bottomBarShown: MutableLiveData<Boolean> = MutableLiveData(true)
    private val _dir: MutableLiveData<File> = MutableLiveData()
    // TODO: setup on sh prf changes
    private val _showStat: MutableLiveData<Boolean> = MutableLiveData(false)
    private val _shapeOrdinal: MutableLiveData<Int> = MutableLiveData(0)
    private val _onDestroy: MutableLiveData<Unit> = MutableLiveData()
    private var bottomBarHideTimer: FlexibleTimer =
        FlexibleTimer(1000L * BOTTOM_BAR_HIDE_DELAY) { hideBottomBar() }

    val bottomBarShown: LiveData<Boolean> = _bottomBarShown
    val dir: LiveData<File> = _dir
    val showStat: LiveData<Boolean> = _showStat
    val shapeOrdinal: LiveData<Int> = _shapeOrdinal
    val onDestroy: LiveData<Unit> = _onDestroy

    init {
        _bottomBarShown.observeForever {
            when(it) {
                true -> bottomBarHideTimer.start()
                false -> bottomBarHideTimer.stop()
            }
        }
        options.showStat.liveData.observeForever {
            _showStat.postValue(it)
        }
    }

    fun showBottomBar() =
        _bottomBarShown.postValue(true)

    fun hideBottomBar() =
        _bottomBarShown.postValue(false)

    fun toggleBottomBar() {
        _bottomBarShown.value = !(_bottomBarShown.value ?: false)
    }

    fun stopBottomBarHideTimer() =
        bottomBarHideTimer.stop()

    fun sendOnDestroy() {
        _onDestroy.value = Unit
    }

    fun setDirOnce(dir: File) {
        if (_dir.value == null)
            _dir.value = dir
    }

    fun changeDir(dir: File) {
        _dir.value = dir
    }

    companion object {
        const val BOTTOM_BAR_HIDE_DELAY = 30 // seconds
    }
}
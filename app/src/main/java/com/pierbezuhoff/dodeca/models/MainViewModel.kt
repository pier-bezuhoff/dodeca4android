package com.pierbezuhoff.dodeca.models

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.pierbezuhoff.dodeca.data.options
import com.pierbezuhoff.dodeca.utils.dduDir
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val context: Context
        get() = getApplication<Application>().applicationContext

    private val _bottomBarShown: MutableLiveData<Boolean> = MutableLiveData(true)
    private val _dir: MutableLiveData<File> = MutableLiveData()
    // TODO: setup on sh prf changes
    private val _showStat: MutableLiveData<Boolean> = MutableLiveData(false)
    private val _shapeOrdinal: MutableLiveData<Int> = MutableLiveData(0)
    private val _onDestroy: MutableLiveData<Unit> = MutableLiveData()
    private var bottomBarHidingJob: Job? = null

    val bottomBarShown: LiveData<Boolean> = _bottomBarShown
    val dir: LiveData<File> = _dir
    val showStat: LiveData<Boolean> = _showStat
    val shapeOrdinal: LiveData<Int> = _shapeOrdinal
    val onDestroy: LiveData<Unit> = _onDestroy

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

    private fun hideBottomBarAfterTimeout() {
        bottomBarHidingJob = viewModelScope.launch {
            delay(BOTTOM_BAR_HIDE_DELAY_IN_MILLISECONDS)
            _bottomBarShown.value = false
        }
    }

    private fun cancelBottomBarHidingJob() {
        bottomBarHidingJob?.cancel()
    }

    fun toggleBottomBar() {
        _bottomBarShown.value = !(_bottomBarShown.value ?: false)
    }

    fun sendOnDestroy() {
        _onDestroy.value = Unit
    }

    fun changeDir(dir: File) {
        _dir.value = dir
    }

    companion object {
        private const val BOTTOM_BAR_HIDE_DELAY_IN_SECONDS = 30
        private const val BOTTOM_BAR_HIDE_DELAY_IN_MILLISECONDS: Long =
            1000L * BOTTOM_BAR_HIDE_DELAY_IN_SECONDS
    }
}
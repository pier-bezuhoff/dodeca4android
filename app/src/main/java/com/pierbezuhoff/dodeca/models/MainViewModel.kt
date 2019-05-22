package com.pierbezuhoff.dodeca.models

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.pierbezuhoff.dodeca.data.Shapes
import com.pierbezuhoff.dodeca.data.options
import com.pierbezuhoff.dodeca.utils.dduDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val context: Context
        get() = getApplication<Application>().applicationContext

    private val _bottomBarShown: MutableLiveData<Boolean> = MutableLiveData(true)
    private val _dir: MutableLiveData<File> = MutableLiveData()
    private val _showStat: MutableLiveData<Boolean> = MutableLiveData(false)
    private val _onDestroy: MutableLiveData<Unit> = MutableLiveData()
    private var bottomBarHidingJob: Job? = null

    val bottomBarShown: LiveData<Boolean> = _bottomBarShown
    val dir: LiveData<File> = _dir
    val showStat: LiveData<Boolean> = _showStat
    // FIX: initial only, selection changes further without shapeOrdinal
    val shapeOrdinal: MutableLiveData<Int> = MutableLiveData(0)
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
        shapeOrdinal.observeForever { showBottomBar() }
    }

    fun updateFromShape(shape: LiveData<Shapes>) {
        shape.observeForever { newShape: Shapes ->
            if (newShape.ordinal != shapeOrdinal.value)
                shapeOrdinal.value = newShape.ordinal
        }
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
        private const val TAG = "MainViewModel"
        private const val BOTTOM_BAR_HIDE_DELAY_IN_SECONDS = 30
        private const val BOTTOM_BAR_HIDE_DELAY_IN_MILLISECONDS: Long =
            1000L * BOTTOM_BAR_HIDE_DELAY_IN_SECONDS
    }
}
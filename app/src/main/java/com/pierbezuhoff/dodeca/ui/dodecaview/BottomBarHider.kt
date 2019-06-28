package com.pierbezuhoff.dodeca.ui.dodecaview

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

interface BottomBarHider {
    val bottomBarShown: LiveData<Boolean>
    fun showBottomBar()
    fun hideBottomBar()
    fun toggleBottomBar() {
        if (bottomBarShown.value == true)
            hideBottomBar()
        else
            showBottomBar()
    }
}

class CoroutineBottomBarHider(private val scope: CoroutineScope) : BottomBarHider {
    private var bottomBarHidingJob: Job? = null
    private val _bottomBarShown: MutableLiveData<Boolean> = MutableLiveData()
    override val bottomBarShown: LiveData<Boolean> = _bottomBarShown

    override fun showBottomBar() {
        _bottomBarShown.postValue(true)
        bottomBarHidingJob?.cancel()
        bottomBarHidingJob = scope.launch(Dispatchers.Default) {
            delay(BOTTOM_BAR_HIDE_DELAY_IN_MILLISECONDS)
            hideBottomBar()
        }
    }

    override fun hideBottomBar() {
        _bottomBarShown.postValue(false)
        bottomBarHidingJob?.cancel()
    }

    companion object {
        private const val BOTTOM_BAR_HIDE_DELAY_IN_SECONDS = 30
        private const val BOTTOM_BAR_HIDE_DELAY_IN_MILLISECONDS: Long =
            1000L * BOTTOM_BAR_HIDE_DELAY_IN_SECONDS
    }
}

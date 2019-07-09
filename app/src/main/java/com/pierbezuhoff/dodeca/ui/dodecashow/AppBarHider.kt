package com.pierbezuhoff.dodeca.ui.dodecashow

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

interface AppBarHider {
    val appBarShown: LiveData<Boolean>
    fun showAppBar(timeoutSeconds: Int? = null)
    fun hideAppBar()
}

class CoroutineAppBarHider(private val scope: CoroutineScope) : AppBarHider {
    private var hidingJob: Job? = null
    private val _appBarShown: MutableLiveData<Boolean> = MutableLiveData(true)
    override val appBarShown: LiveData<Boolean> = _appBarShown

    override fun showAppBar(timeoutSeconds: Int?) {
        hidingJob?.cancel()
        _appBarShown.postValue(true)
        timeoutSeconds?.let {
            hidingJob = scope.launch(Dispatchers.Default) {
                delay(1000L * timeoutSeconds)
                hideAppBar()
            }
        }
    }

    override fun hideAppBar() {
        _appBarShown.postValue(false)
        hidingJob?.cancel()
    }
}
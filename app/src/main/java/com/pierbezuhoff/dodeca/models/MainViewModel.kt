package com.pierbezuhoff.dodeca.models

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.io.File

class MainViewModel : ViewModel() {
    private val _bottomBarShown: MutableLiveData<Boolean> = MutableLiveData(true)
    private val _dir: MutableLiveData<File> = MutableLiveData()
    private val _nUpdates: MutableLiveData<Long> = MutableLiveData(0L)
    // TODO: setup on sh prf changes
    private val _showStat: MutableLiveData<Boolean> = MutableLiveData(false)

    val bottomBarShown: LiveData<Boolean> = _bottomBarShown
    val dir: LiveData<File> = _dir
    val nUpdates: LiveData<Long> = _nUpdates
    val showStat: LiveData<Boolean> = _showStat

    init {
    }
}
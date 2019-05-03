package com.pierbezuhoff.dodeca.models

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.io.File

class MainViewModel : ViewModel() {
    private val _bottomBarShown: MutableLiveData<Boolean> = MutableLiveData(true)
    private val _dir: MutableLiveData<File> = MutableLiveData()
    // TODO: setup on sh prf changes
    private val _showStat: MutableLiveData<Boolean> = MutableLiveData(false)
    private val _shapeOrdinal: MutableLiveData<Int> = MutableLiveData(0)

    val bottomBarShown: LiveData<Boolean> = _bottomBarShown
    val dir: LiveData<File> = _dir
    val showStat: LiveData<Boolean> = _showStat
    val shapeOrdinal: LiveData<Int> = _shapeOrdinal
}
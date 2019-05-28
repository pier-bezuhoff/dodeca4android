package com.pierbezuhoff.dodeca.ui.dodecaedit

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class DodecaEditViewModel : ViewModel() {
    private val _playing: MutableLiveData<Boolean> = MutableLiveData(false)
    private val _drawCirclesMode: MutableLiveData<Boolean> = MutableLiveData(false)

    val playing: LiveData<Boolean> = _playing
    val drawCirclesMode: LiveData<Boolean> = _drawCirclesMode
}
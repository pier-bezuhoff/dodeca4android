package com.pierbezuhoff.dodeca.ui.dodecashow

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.pierbezuhoff.dodeca.models.DduRepresentation
import com.pierbezuhoff.dodeca.models.OptionsManager
import com.pierbezuhoff.dodeca.ui.meta.DodecaAndroidViewModelWithOptionsManager

class DodecaShowViewModel(
    application: Application,
    optionsManager: OptionsManager
) : DodecaAndroidViewModelWithOptionsManager(application, optionsManager)
    , DodecaShowGestureDetector.SingleTapListener
    , DodecaShowGestureDetector.DoubleTapListener
    , DodecaShowGestureDetector.ScrollListener
{
    private val _dduLoading: MutableLiveData<Boolean> = MutableLiveData(false)
    private val _dduRepresentation: MutableLiveData<DduRepresentation> = MutableLiveData()

    private val _updating: MutableLiveData<Boolean> = MutableLiveData()
    val dduLoading: LiveData<Boolean> = _dduLoading // for ProgressBar

    val dduRepresentation: LiveData<DduRepresentation> = _dduRepresentation
    val updating: LiveData<Boolean> = _updating
    // show top bar first 3 s (option)

    val gestureDetector: DodecaShowGestureDetector = DodecaShowGestureDetector.get(context)

    init {

    }
}
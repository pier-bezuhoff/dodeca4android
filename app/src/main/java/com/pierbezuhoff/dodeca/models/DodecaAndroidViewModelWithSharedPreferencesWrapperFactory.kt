package com.pierbezuhoff.dodeca.models

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class DodecaAndroidViewModelWithSharedPreferencesWrapperFactory(
    private val application: Application,
    private val sharedPreferencesWrapper: SharedPreferencesWrapper
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(DodecaAndroidViewModelWithSharedPreferencesWrapper::class.java))
        val constructor =
            modelClass.getConstructor(Application::class.java, SharedPreferencesWrapper::class.java)
        return constructor.newInstance(application, sharedPreferencesWrapper)
    }
}


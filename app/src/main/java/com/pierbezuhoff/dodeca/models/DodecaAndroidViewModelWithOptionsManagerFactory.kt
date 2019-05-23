package com.pierbezuhoff.dodeca.models

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class DodecaAndroidViewModelWithOptionsManagerFactory(
    private val application: Application,
    private val optionsManager: OptionsManager
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(DodecaAndroidViewModelWithOptionsManager::class.java))
        val constructor =
            modelClass.getConstructor(Application::class.java, OptionsManager::class.java)
        return constructor.newInstance(application, optionsManager)
    }
}


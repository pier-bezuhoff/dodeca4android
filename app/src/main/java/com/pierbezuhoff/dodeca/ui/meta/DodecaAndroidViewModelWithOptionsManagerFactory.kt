package com.pierbezuhoff.dodeca.ui.meta

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.pierbezuhoff.dodeca.models.OptionsManager

class DodecaAndroidViewModelWithOptionsManagerFactory(
    private val application: Application,
    private val optionsManager: OptionsManager
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(DodecaAndroidViewModelWithOptions::class.java.isAssignableFrom(modelClass))
        val constructor =
            modelClass.getConstructor(Application::class.java, OptionsManager::class.java)
        return constructor.newInstance(application, optionsManager)
    }
}


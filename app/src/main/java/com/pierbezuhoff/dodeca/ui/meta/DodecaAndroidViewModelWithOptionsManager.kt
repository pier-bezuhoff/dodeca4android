package com.pierbezuhoff.dodeca.ui.meta

import android.app.Application
import com.pierbezuhoff.dodeca.models.OptionsManager

abstract class DodecaAndroidViewModelWithOptionsManager(
    application: Application,
    protected val optionsManager: OptionsManager
) : DodecaAndroidViewModel(application)
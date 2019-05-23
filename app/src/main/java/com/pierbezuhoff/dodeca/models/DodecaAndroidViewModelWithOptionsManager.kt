package com.pierbezuhoff.dodeca.models

import android.app.Application

abstract class DodecaAndroidViewModelWithOptionsManager(
    application: Application,
    protected val optionsManager: OptionsManager
) : DodecaAndroidViewModel(application)
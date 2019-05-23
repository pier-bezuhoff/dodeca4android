package com.pierbezuhoff.dodeca.models

import android.app.Application

abstract class DodecaAndroidViewModelWithSharedPreferencesWrapper(
    application: Application,
    protected val sharedPreferencesWrapper: SharedPreferencesWrapper
) : DodecaAndroidViewModel(application)
package com.pierbezuhoff.dodeca.models

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.pierbezuhoff.dodeca.db.DduFileRepository

abstract class DodecaAndroidViewModel(application: Application) : AndroidViewModel(application) {
    protected val context: Context
        get() = getApplication<Application>().applicationContext
    protected val dduFileRepository: DduFileRepository by lazy {
        DduFileRepository.get(context)
    }
}
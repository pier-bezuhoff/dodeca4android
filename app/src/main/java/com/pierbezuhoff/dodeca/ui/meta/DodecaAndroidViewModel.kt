package com.pierbezuhoff.dodeca.ui.meta

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.pierbezuhoff.dodeca.models.DduFileRepository
import com.pierbezuhoff.dodeca.models.DduFileService

abstract class DodecaAndroidViewModel(application: Application) : AndroidViewModel(application) {
    protected val context: Context
        get() = getApplication<Application>().applicationContext
    protected val dduFileRepository: DduFileRepository = DduFileRepository.get(context)
    protected val dduFileService: DduFileService = DduFileService(context)
}
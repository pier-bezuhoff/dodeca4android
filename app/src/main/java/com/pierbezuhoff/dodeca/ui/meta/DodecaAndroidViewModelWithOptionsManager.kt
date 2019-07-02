package com.pierbezuhoff.dodeca.ui.meta

import android.app.Application
import com.pierbezuhoff.dodeca.data.options
import com.pierbezuhoff.dodeca.models.OptionsManager
import com.pierbezuhoff.dodeca.utils.div
import java.io.File

abstract class DodecaAndroidViewModelWithOptionsManager(
    application: Application,
    protected val optionsManager: OptionsManager
) : DodecaAndroidViewModel(application) {
    val dir: File
        get() {
            val dduDir = dduFileService.dduDir
            return (dduDir/optionsManager.fetched(options.recentDdu)).parentFile ?: dduDir
        }
}
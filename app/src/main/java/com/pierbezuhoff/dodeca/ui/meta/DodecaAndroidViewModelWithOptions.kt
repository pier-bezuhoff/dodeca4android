package com.pierbezuhoff.dodeca.ui.meta

import android.app.Application
import com.pierbezuhoff.dodeca.models.OptionsManager
import com.pierbezuhoff.dodeca.utils.div
import java.io.File

abstract class DodecaAndroidViewModelWithOptions(
    application: Application,
    protected val optionsManager: OptionsManager
) : DodecaAndroidViewModel(application) {
    val dir: File
        get() {
            val dduDir = dduFileService.dduDir
            val recentDdu = optionsManager.run { fetched(options.recentDdu) }
            return (dduDir/recentDdu).parentFile ?: dduDir
        }
}
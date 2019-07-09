package com.pierbezuhoff.dodeca.ui.meta

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.pierbezuhoff.dodeca.models.DduFileRepository
import com.pierbezuhoff.dodeca.models.DduFileService
import com.pierbezuhoff.dodeca.models.DduRepresentation
import org.jetbrains.anko.toast

abstract class DodecaAndroidViewModel(application: Application) : AndroidViewModel(application)
    , DduRepresentation.ToastEmitter
{
    protected val context: Context
        get() = getApplication<Application>().applicationContext
    protected val dduFileRepository: DduFileRepository = DduFileRepository.get(context)
    protected val dduFileService: DduFileService = DduFileService(context)

    override fun toast(message: CharSequence) { context.toast(message) }
    override fun formatToast(id: Int, vararg args: Any) { context.toast(context.getString(id, *args)) }
}
package com.pierbezuhoff.dodeca.ui.dduchooser

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import androidx.paging.PagedList
import com.pierbezuhoff.dodeca.data.Ddu
import com.pierbezuhoff.dodeca.data.values
import com.pierbezuhoff.dodeca.ui.meta.DodecaAndroidViewModel
import com.pierbezuhoff.dodeca.utils.Filename
import com.pierbezuhoff.dodeca.utils.filename
import com.pierbezuhoff.dodeca.utils.isDdu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DduChooserViewModel(application: Application) : DodecaAndroidViewModel(application) {
    private val _dduFiles: MutableLiveData<PagedList<DduFileAdapter.DduFileEntry>> = MutableLiveData()
    val dduFiles: LiveData<PagedList<DduFileAdapter.DduFileEntry>> = _dduFiles

    fun requestFilesAt(dir: File) {
        viewModelScope.launch {
            val files = getFiles(dir)
        }
    }

    private suspend fun getFiles(dir: File): Array<File> = withContext(Dispatchers.IO) {
        return@withContext dir.listFiles { file -> file.isDdu }
    }

    fun buildPreviewOf(file: File): LiveData<Bitmap> {
        return liveData(viewModelScope.coroutineContext) {
            _buildPreviewOf(file)
        }
    }

    private suspend fun _buildPreviewOf(file: File): Bitmap {
        val filename: Filename = file.filename
        val ddu = Ddu.fromFile(file)
        val size = values.previewSizePx
        val bitmap = ddu.buildPreview(size, size)
        dduFileRepository.setPreviewInserting(filename, bitmap)
        return bitmap
    }
}
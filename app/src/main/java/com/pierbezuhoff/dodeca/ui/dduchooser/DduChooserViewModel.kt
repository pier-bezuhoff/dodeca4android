package com.pierbezuhoff.dodeca.ui.dduchooser

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.pierbezuhoff.dodeca.data.Ddu
import com.pierbezuhoff.dodeca.data.values
import com.pierbezuhoff.dodeca.ui.meta.DodecaAndroidViewModel
import com.pierbezuhoff.dodeca.utils.Filename
import com.pierbezuhoff.dodeca.utils.filename
import com.pierbezuhoff.dodeca.utils.isDdu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DduChooserViewModel(application: Application) : DodecaAndroidViewModel(application) {
    var dduContextMenuCreatorPosition: Int? = null
    var dirContextMenuCreatorPosition: Int? = null

    fun requestFilesAt(dir: File): LiveData<Array<File>> = liveData {
        val files = getFiles(dir)
        emit(files)
    }

    private suspend fun getFiles(dir: File): Array<File> = withContext(Dispatchers.IO) {
        return@withContext dir.listFiles { file -> file.isDdu }
    }

    fun requestPreviewsMapOf(files: Array<File>): LiveData<Map<File, Bitmap?>> = liveData {
        val map = getPreviewsMap(files)
        emit(map)
    }

    private suspend fun getPreviewsMap(files: Array<File>): Map<File, Bitmap?> {
        return files.associate { file: File ->
            file to dduFileRepository.getPreview(file.filename)
        }
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
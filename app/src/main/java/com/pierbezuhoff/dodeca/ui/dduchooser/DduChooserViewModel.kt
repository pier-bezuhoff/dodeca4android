package com.pierbezuhoff.dodeca.ui.dduchooser

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.liveData
import androidx.paging.LivePagedListBuilder
import androidx.paging.PagedList
import com.pierbezuhoff.dodeca.data.Ddu
import com.pierbezuhoff.dodeca.data.values
import com.pierbezuhoff.dodeca.ui.meta.DodecaAndroidViewModel
import com.pierbezuhoff.dodeca.utils.Filename
import com.pierbezuhoff.dodeca.utils.filename
import org.jetbrains.anko.toast
import java.io.File

class DduChooserViewModel(application: Application) : DodecaAndroidViewModel(application) {
    var dduContextMenuCreatorPosition: Int? = null
    var dirContextMenuCreatorPosition: Int? = null
    private var __files: LiveData<PagedList<File>> = MutableLiveData()
        set(value) {
            _files.removeSource(field)
            _files.addSource(value) { pagedList ->
                _files.postValue(pagedList)
            }
            field = value
        }
    private val _files: MediatorLiveData<PagedList<File>> = MediatorLiveData()
    val files: LiveData<PagedList<File>> = _files

    fun setDir(dir: File) {
        val factory = DirFilesDataSourceFactory(dir)
        __files = LivePagedListBuilder<Int, File>(factory, PAGED_LIST_CONFIG).build()
    }

    fun getPreviewOf(file: File): LiveData<Bitmap> = liveData {
        val cachedBitmap = dduFileRepository.getPreview(file.filename)
        val bitmap = cachedBitmap ?: tryBuildPreviewOf(file)
        bitmap?.let { emit(it) }
    }

    private suspend fun tryBuildPreviewOf(file: File): Bitmap? {
        return try {
            buildPreviewOf(file)
        } catch (e: Exception) {
            e.printStackTrace()
            context.toast("Failed to build preview of ddu-file \"${file.filename}\"")
            // TODO: show error icon instead of progress bar
            null
        }
    }

    private suspend fun buildPreviewOf(file: File): Bitmap {
        val filename: Filename = file.filename
        val ddu = Ddu.fromFile(file)
        val size = values.previewSizePx
        // MAYBE: progress bar (# of updates)
        val bitmap = ddu.buildPreview(size, size)
        dduFileRepository.setPreviewInserting(filename, bitmap)
        return bitmap
    }

    companion object {
        private const val TAG = "DduChooserViewModel"
        private const val PAGE_SIZE = 10
        private val PAGED_LIST_CONFIG = PagedList.Config.Builder()
            .setPageSize(PAGE_SIZE)
            .setEnablePlaceholders(true)
            .build()
    }
}
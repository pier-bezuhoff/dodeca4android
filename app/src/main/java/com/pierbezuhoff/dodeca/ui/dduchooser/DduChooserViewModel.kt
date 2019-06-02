package com.pierbezuhoff.dodeca.ui.dduchooser

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import androidx.paging.LivePagedListBuilder
import androidx.paging.PagedList
import com.pierbezuhoff.dodeca.data.Ddu
import com.pierbezuhoff.dodeca.data.values
import com.pierbezuhoff.dodeca.ui.meta.DodecaAndroidViewModel
import com.pierbezuhoff.dodeca.utils.Filename
import com.pierbezuhoff.dodeca.utils.filename
import com.pierbezuhoff.dodeca.utils.isDdu
import org.jetbrains.anko.toast
import java.io.File
import java.io.FileFilter

class DduChooserViewModel(application: Application) : DodecaAndroidViewModel(application) {
    private var dirFilesDataSourceFactory: DirFilesDataSourceFactory? = null
    private var dirsDataSourceFactory: DirFilesDataSourceFactory? = null
    lateinit var files: LiveData<PagedList<File>> private set
    lateinit var dirs: LiveData<PagedList<File>> private set

    fun setDir(dir: File) {
        Log.i(TAG, "setDir($dir)")
        if (dirFilesDataSourceFactory == null || dirsDataSourceFactory == null) {
            setInitialDir(dir)
        } else {
            dirFilesDataSourceFactory!!.changeDir(dir)
            dirsDataSourceFactory!!.changeDir(dir)
        }
    }

    private fun setInitialDir(dir: File) {
        Log.i(TAG, "setInitialDir($dir)")
        dirFilesDataSourceFactory = DirFilesDataSourceFactory(dir, FileFilter { it.isDdu })
        dirsDataSourceFactory = DirFilesDataSourceFactory(dir, FileFilter { it.isDirectory })
        files = LivePagedListBuilder<Int, File>(dirFilesDataSourceFactory!!, PAGED_LIST_CONFIG)
            .build()
        dirs = LivePagedListBuilder<Int, File>(dirsDataSourceFactory!!, PAGED_LIST_CONFIG)
            .build()
    }

    fun clearFactories() {
        Log.i(TAG, "clearFactories")
        dirFilesDataSourceFactory = null
        dirsDataSourceFactory = null
    }

    fun getPreviewOf(file: File): LiveData<Bitmap> = liveData {
        Log.i(TAG, "gettingPreviewOf($file)")
        val cachedBitmap = dduFileRepository.getPreview(file.filename)
        val bitmap = cachedBitmap ?: tryBuildPreviewOf(file)
        Log.i(TAG, "preview built")
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
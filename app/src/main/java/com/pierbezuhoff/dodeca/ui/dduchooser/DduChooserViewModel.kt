package com.pierbezuhoff.dodeca.ui.dduchooser

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.liveData
import androidx.paging.LivePagedListBuilder
import androidx.paging.PagedList
import com.pierbezuhoff.dodeca.data.Ddu
import com.pierbezuhoff.dodeca.data.options
import com.pierbezuhoff.dodeca.data.values
import com.pierbezuhoff.dodeca.models.OptionsManager
import com.pierbezuhoff.dodeca.ui.meta.DodecaAndroidViewModelWithOptionsManager
import com.pierbezuhoff.dodeca.utils.Filename
import com.pierbezuhoff.dodeca.utils.dduDir
import com.pierbezuhoff.dodeca.utils.filename
import com.pierbezuhoff.dodeca.utils.isDdu
import org.jetbrains.anko.toast
import java.io.File
import java.io.FileFilter

class DduChooserViewModel(
    application: Application,
    optionsManager: OptionsManager
) : DodecaAndroidViewModelWithOptionsManager(application, optionsManager) {
    private var dirFilesDataSourceFactory: DirFilesDataSourceFactory? = null
    private var dirsDataSourceFactory: DirFilesDataSourceFactory? = null
    // NOTE: only previews for files in current dir, should not use very much memory
    private val previews: MutableMap<File, LiveData<Bitmap>> = mutableMapOf()
    private val _dir: MutableLiveData<File> = MutableLiveData()
    lateinit var files: LiveData<PagedList<File>> private set
    lateinit var dirs: LiveData<PagedList<File>> private set
    val dir: LiveData<File> = _dir.distinctUntilChanged()

    init {
        val recentDir = optionsManager.fetched(options.recentDir)
        if (recentDir == null) {
            val dduDir = context.dduDir
            optionsManager.set(options.recentDir, dduDir)
            _dir.value = dduDir
        } else {
            _dir.value = recentDir
        }
    }

    fun setDir(dir: File) {
        Log.i(TAG, "setDir($dir)")
        ! _dir.value = dir...
        previews.clear()
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

    fun clear() {
        Log.i(TAG, "clear")
        dirFilesDataSourceFactory = null
        dirsDataSourceFactory = null
        previews.clear()
    }

    fun getPreviewOf(file: File): LiveData<Bitmap> {
        previews[file]?.let { return it }
        val preview = liveData {
            Log.i(TAG, "getPreviewOf($file)")
            val cachedBitmap = dduFileRepository.getPreview(file.filename)
            val bitmap = cachedBitmap ?: tryBuildPreviewOf(file)
            bitmap?.let { emit(it) }
        }
        previews[file] = preview
        return preview
    }

    private suspend fun tryBuildPreviewOf(file: File): Bitmap? {
        return try {
            Log.i(TAG, "buildPreviewOf($file)")
            val bitmap = buildPreviewOf(file)
            Log.i(TAG, "preview of $file built")
            bitmap
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
package com.pierbezuhoff.dodeca.ui.dduchooser

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.map
import androidx.paging.PagedList
import androidx.paging.toLiveData
import com.pierbezuhoff.dodeca.data.Ddu
import com.pierbezuhoff.dodeca.data.values
import com.pierbezuhoff.dodeca.models.OptionsManager
import com.pierbezuhoff.dodeca.ui.meta.DodecaAndroidViewModelWithOptionsManager
import com.pierbezuhoff.dodeca.utils.Filename
import com.pierbezuhoff.dodeca.utils.filename
import com.pierbezuhoff.dodeca.utils.isDdu
import kotlinx.coroutines.CancellationException
import org.jetbrains.anko.toast
import java.io.File
import java.io.FileFilter

class DduChooserViewModel(
    application: Application,
    optionsManager: OptionsManager
) : DodecaAndroidViewModelWithOptionsManager(application, optionsManager)
    , DduFileAdapter.PreviewSupplier
    , DirAdapter.DirChangeListener
{
    private lateinit var dirsDataSourceFactory: DirFilesDataSourceFactory
    private lateinit var dirFilesDataSourceFactory: DirFilesDataSourceFactory
    // NOTE: only previews for ddu-files in current dir, should not use very much memory
    private val previews: MutableMap<File, LiveData<Bitmap>> = mutableMapOf()
    private val _dir: MutableLiveData<File> = MutableLiveData()
    lateinit var dirs: LiveData<PagedList<File>> private set
    lateinit var files: LiveData<PagedList<File>> private set
    val dir: LiveData<File> = _dir

    fun setInitialDir(newDir: File) {
        if (_dir.value == null) {
            _dir.value = newDir
            dirsDataSourceFactory = DirFilesDataSourceFactory(newDir, FileFilter { it.isDirectory })
            dirFilesDataSourceFactory = DirFilesDataSourceFactory(newDir, FileFilter { it.isDdu })
            dirs = dirsDataSourceFactory.toLiveData(config = PAGED_LIST_CONFIG)
            files = dirFilesDataSourceFactory.toLiveData(config = PAGED_LIST_CONFIG)
        }
    }

    override fun onDirChanged(dir: File) {
        Log.i(TAG, "onDirChanged($dir)")
        _dir.postValue(dir)
        previews.clear()
        dirsDataSourceFactory.changeDir(dir)
        dirFilesDataSourceFactory.changeDir(dir)
    }

    override fun getPreviewOf(file: File): LiveData<Pair<File, Bitmap>> {
        previews[file]?.let { livePreview: LiveData<Bitmap> ->
            return livePreview.map { file to it }
        }
        val preview = liveData {
            dduFileRepository.insertIfAbsent(file.filename)
            val cachedBitmap = dduFileRepository.getPreview(file.filename)
            val bitmap = cachedBitmap ?: tryBuildPreviewOf(file)
            bitmap?.let { emit(it) }
        }
        previews[file] = preview
        return preview.map { file to it }
    }

    private suspend fun tryBuildPreviewOf(file: File): Bitmap? {
        return try {
            val bitmap = buildPreviewOf(file)
            bitmap
        } catch (e: CancellationException) {
            null // NOTE: it's ok if we returned to MainActivity before preview has been built
        } catch (e: Exception) {
            Log.w(TAG, "Failed to buildPreviewOf($file)")
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
        dduFileRepository.setPreview(filename, bitmap)
        return bitmap
    }

    companion object {
        private const val TAG = "DduChooserViewModel"
        // TODO: understand, why when PAGE_SIZE <= 10 rename/duplicate/... cause crash (item created in the end of the recycler view)
        private const val PAGE_SIZE = 1000 // when # of ddus > PAGE_SIZE smth bad may happen
        private val PAGED_LIST_CONFIG = PagedList.Config.Builder()
            .setPageSize(PAGE_SIZE)
            .setEnablePlaceholders(true)
            .build()
    }
}
package com.pierbezuhoff.dodeca.ui.dduchooser

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.map
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
    // NOTE: only previews for ddu-files in current dir, should not use very much memory
    private val previews: MutableMap<File, LiveData<Bitmap>> = mutableMapOf()
    private val _dir: MutableLiveData<File> = MutableLiveData()
    val dirs: MutableList<File> = mutableListOf()
    val files: MutableList<File> = mutableListOf()
    val dir: LiveData<File> = _dir

    fun setInitialDir(newDir: File) {
        if (_dir.value == null) {
            _dir.value = newDir
            updateFromDir(newDir)
        }
    }

    override fun onDirChanged(dir: File) {
        Log.i(TAG, "onDirChanged($dir)")
        _dir.postValue(dir)
        previews.clear()
        updateFromDir(dir)
    }

    private fun updateFromDir(dir: File) {
        dirs.clear()
        files.clear()
        dirs.addAll(dir.listFiles(DIR_FILE_FILTER))
        files.addAll(dir.listFiles(DDU_FILE_FILTER))
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
            // MAYBE: show error icon instead of progress bar
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

    fun forgetPreviewOf(file: File) {
        previews.remove(file)
    }

    companion object {
        private const val TAG = "DduChooserViewModel"
        private val DIR_FILE_FILTER: FileFilter = FileFilter { it.isDirectory }
        private val DDU_FILE_FILTER: FileFilter = FileFilter { it.isDdu }
//        // TODO: understand, why when PAGE_SIZE <= 10 rename/duplicate/... cause crash (item created in the end of the recycler view)
//        private const val PAGE_SIZE = 1000 // when # of ddus > PAGE_SIZE smth bad may happen
//        private val PAGED_LIST_CONFIG = PagedList.Config.Builder()
//            .setPageSize(PAGE_SIZE)
//            .setEnablePlaceholders(true)
//            .build()
    }
}
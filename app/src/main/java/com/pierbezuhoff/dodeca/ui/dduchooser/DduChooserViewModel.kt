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
import kotlinx.coroutines.Job
import org.jetbrains.anko.toast
import java.io.File
import java.io.FileFilter
import kotlin.coroutines.coroutineContext

class DduChooserViewModel(
    application: Application,
    optionsManager: OptionsManager
) : DodecaAndroidViewModelWithOptionsManager(application, optionsManager)
    , DduFileAdapter.PreviewSupplier
{
    // NOTE: only previews for ddu-files in current currentDir, should not use very much memory
    private val previews: MutableMap<File, LiveData<Bitmap>> = mutableMapOf()
    private val previewJobs: MutableMap<File, Job> = mutableMapOf()
    private val _currentDir: MutableLiveData<File> = MutableLiveData()
    private val _ddusLoading: MutableLiveData<Boolean> = MutableLiveData(false)
    val dirs: MutableList<File> = mutableListOf()
    val files: MutableList<File> = mutableListOf()
    val currentDir: LiveData<File> = _currentDir
    val ddusLoading: LiveData<Boolean> = _ddusLoading

    fun setInitialDir(newDir: File) {
        if (_currentDir.value == null) {
            _currentDir.value = newDir
            updateFromDir(newDir)
        }
    }

    fun goToDir(dir: File) {
        if (_currentDir.value != dir)
            Log.i(TAG, "currentDir -> $dir")
        _currentDir.postValue(dir)
        clearPreviewJobs()
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
            coroutineContext[Job]?.let { job ->
                previewJobs[file] = job // track job to be able to cancel it afterwards
            }
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
        clearPreviewJob(file)
        previews.remove(file)
    }

    private fun clearPreviewJobs() {
        previewJobs.forEach { (_, job) -> job.cancel() }
        previewJobs.clear()
    }

    private fun clearPreviewJob(file: File) {
        previewJobs[file]?.cancel()
        previewJobs.remove(file)
    }

    fun startLoadingDdus() {
        _ddusLoading.postValue(true)
    }

    fun finishLoadingDdus() {
        _ddusLoading.postValue(false)
    }

    suspend inline fun <T> loadingDdus(crossinline action: suspend () -> T): T {
        startLoadingDdus()
        return try {
            action()
        } finally {
            finishLoadingDdus()
        }

    }

    override fun onCleared() {
        clearPreviewJobs()
        super.onCleared()
    }

    companion object {
        private const val TAG = "DduChooserViewModel"
        private val DIR_FILE_FILTER: FileFilter = FileFilter { it.isDirectory }
        private val DDU_FILE_FILTER: FileFilter = FileFilter { it.isDdu }
    }
}
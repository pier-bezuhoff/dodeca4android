package com.pierbezuhoff.dodeca.ui.dduchooser

import androidx.paging.DataSource
import androidx.paging.PositionalDataSource
import com.pierbezuhoff.dodeca.utils.isDdu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min
import kotlin.properties.Delegates

class DirFilesDataSourceFactory(private var dir: File) : DataSource.Factory<Int, File>() {
    private val validDataSources: MutableList<DataSource<Int, File>> =
        mutableListOf()

    override fun create(): DataSource<Int, File> {
        return DirFilesDataSource(dir).also {
            validDataSources.add(it)
        }
    }

    fun changeDir(dir: File) {
        this.dir = dir
        validDataSources.forEach { it.invalidate() }
        validDataSources.clear()
    }

    private class DirFilesDataSource(private val dir: File) : PositionalDataSource<File>() {
        private lateinit var files: List<File>
        private var size: Int by Delegates.notNull()

        private suspend fun loadFiles() = withContext(Dispatchers.IO) {
            files = dir
                .listFiles { file -> file.isDdu }
                .toList()
            size = files.size
        }

        override fun loadRange(params: LoadRangeParams, callback: LoadRangeCallback<File>) {
            val startPosition = min(params.startPosition, size)
            val endPosition = min(startPosition + params.loadSize, size)
            val loadedFiles = files.subList(startPosition, endPosition)
            callback.onResult(loadedFiles)
        }

        override fun loadInitial(params: LoadInitialParams, callback: LoadInitialCallback<File>) {
            val requestedSize = params.requestedLoadSize
            val requestedStartPosition = params.requestedStartPosition
            GlobalScope.launch {
                loadFiles()
                val startPosition = computeInitialLoadPosition(params, size)
                val loadSize = computeInitialLoadSize(params, startPosition, size)
                val loadedFiles = files.subList(startPosition, startPosition + loadSize)
                callback.onResult(loadedFiles, startPosition, size)
            }
        }
    }
}


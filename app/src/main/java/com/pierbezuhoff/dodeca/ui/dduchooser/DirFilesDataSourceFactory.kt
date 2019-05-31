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

class DirFilesDataSourceFactory(private val dir: File) : DataSource.Factory<Int, File>() {
    override fun create(): DataSource<Int, File> {
        return DirFilesDataSource(dir)
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
                val startPosition = min(requestedStartPosition, size)
                val endPosition = min(startPosition + params.requestedLoadSize, size)
                val loadedFiles = files.subList(startPosition, endPosition)
                callback.onResult(loadedFiles, startPosition, size)
            }
        }
    }
}


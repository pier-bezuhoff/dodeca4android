package com.pierbezuhoff.dodeca.ui.dduchooser

import androidx.paging.PositionalDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileFilter
import kotlin.math.min
import kotlin.properties.Delegates

class DirFilesDataSource(
    private val dir: File,
    private val fileFilter: FileFilter
) : PositionalDataSource<File>() {
    private lateinit var files: List<File>
    private var size: Int by Delegates.notNull()

    private suspend fun loadFiles() = withContext(Dispatchers.IO) {
        files = dir
            .listFiles(fileFilter)
            .toList()
        size = files.size
    }

    private fun loadRangeInternal(requestedStartPosition: Int, loadSize: Int): List<File> {
        val startPosition = min(requestedStartPosition, size)
        val endPosition = min(startPosition + loadSize, size) // exclusive: [start; end)
        return files.subList(startPosition, endPosition)
    }

    override fun loadInitial(params: LoadInitialParams, callback: LoadInitialCallback<File>) {
        GlobalScope.launch {
            loadFiles()
            val startPosition = computeInitialLoadPosition(params, size)
            val loadSize = computeInitialLoadSize(params, startPosition, size)
            callback.onResult(
                loadRangeInternal(startPosition, loadSize), startPosition, size)
        }
    }

    override fun loadRange(params: LoadRangeParams, callback: LoadRangeCallback<File>) {
        callback.onResult(loadRangeInternal(params.startPosition, params.loadSize))
    }
}
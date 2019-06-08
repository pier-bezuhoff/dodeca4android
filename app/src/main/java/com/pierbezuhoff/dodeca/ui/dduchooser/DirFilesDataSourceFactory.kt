package com.pierbezuhoff.dodeca.ui.dduchooser

import androidx.paging.DataSource
import java.io.File
import java.io.FileFilter

class _DirFilesDataSourceFactory(
    private var dir: File,
    private val fileFilter: FileFilter
    ) : DataSource.Factory<Int, File>() {
    private val validDataSources: MutableList<DataSource<Int, File>> =
        mutableListOf()

    override fun create(): DataSource<Int, File> {
        return DirFilesDataSource(dir, fileFilter).also {
            validDataSources.add(it)
        }
    }

    fun changeDir(dir: File) {
        this.dir = dir
        validDataSources.forEach { it.invalidate() }
        validDataSources.clear()
    }
}


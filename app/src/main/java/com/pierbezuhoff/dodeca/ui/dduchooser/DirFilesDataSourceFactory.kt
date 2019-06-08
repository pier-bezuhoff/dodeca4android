package com.pierbezuhoff.dodeca.ui.dduchooser

import androidx.lifecycle.MutableLiveData
import androidx.paging.DataSource
import java.io.File
import java.io.FileFilter

class DirFilesDataSourceFactory(
    private var dir: File,
    private val fileFilter: FileFilter
) : DataSource.Factory<Int, File>() {
    private val source: MutableLiveData<DirFilesDataSource> = MutableLiveData()

    override fun create(): DataSource<Int, File> {
        val latestSource = DirFilesDataSource(dir, fileFilter)
        source.postValue(latestSource)
        return latestSource
    }

    fun changeDir(dir: File) {
        this.dir = dir
        source.value?.invalidate()
    }
}

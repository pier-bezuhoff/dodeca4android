package com.pierbezuhoff.dodeca.db

import android.graphics.Bitmap
import com.pierbezuhoff.dodeca.utils.Filename

// TODO: refactor usage
class DduFileRepository private constructor() {
    private val dduFileDao: DduFileDao by lazy {
        DduFileDatabase.INSTANCE!!.dduFileDao()
    }

    suspend fun getAllDduFiles(): List<DduFile> =
        dduFileDao.getAll()

    suspend fun delete(filename: Filename) =
        getDduFile(filename)?.let {
            dduFileDao.delete(it)
        }

    private suspend fun getDduFile(filename: Filename): DduFile? =
        dduFileDao.findByFilename(filename)

    private suspend fun applyInserting(filename: Filename, action: DduFile.() -> Unit) {
        val maybeDduFile: DduFile? = getDduFile(filename)
        if (maybeDduFile == null) {
            val newDduFile = DduFile.fromFilename(filename)
            newDduFile.action()
            dduFileDao.insert(newDduFile)
        } else {
            maybeDduFile.action()
            dduFileDao.update(maybeDduFile)
        }
    }

    suspend fun dropPreviewInserting(filename: Filename) =
        applyInserting(filename) { preview = null }

    suspend fun dropPreview(filename: Filename) {
        getDduFile(filename)?.let { dduFile ->
            dduFile.preview = null
            dduFileDao.update(dduFile)
        }
    }

    suspend fun dropPreview(dduFile: DduFile) {
        dduFile.preview = null
        dduFileDao.update(dduFile)
    }

    suspend fun getOriginalFilename(filename: Filename): Filename? =
        getDduFile(filename)?.originalFilename

    suspend fun dropPreviewAndSetOriginalFilenameInserting(filename: Filename, newOriginalFilename: Filename) =
        applyInserting(filename) {
            preview = null
            originalFilename = newOriginalFilename
        }

    suspend fun updateFilenameInserting(filename: Filename, newFilename: Filename) =
        applyInserting(filename) {
            this.filename = newFilename
        }

    suspend fun getPreview(filename: Filename): Bitmap? =
        getDduFile(filename)?.preview

    suspend fun setPreviewInserting(filename: Filename, newPreview: Bitmap) =
        applyInserting(filename) {
            preview = newPreview
        }

    companion object {
        val INSTANCE: DduFileRepository = DduFileRepository()
    }
}

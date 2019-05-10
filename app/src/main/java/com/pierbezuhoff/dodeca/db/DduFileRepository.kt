package com.pierbezuhoff.dodeca.db

import android.graphics.Bitmap
import com.pierbezuhoff.dodeca.utils.Filename

// TODO: refactor usage
class DduFileRepository private constructor() {
    private val dduFileDao: DduFileDao by lazy {
        DduFileDatabase.INSTANCE!!.dduFileDao()
    }

    fun getAllDduFiles(): List<DduFile> =
        dduFileDao.getAll()

    fun delete(filename: Filename) =
        getDduFile(filename)?.let {
            dduFileDao.delete(it)
        }

    private fun getDduFile(filename: Filename): DduFile? =
        dduFileDao.findByFilename(filename)

    private fun applyInserting(filename: Filename, action: DduFile.() -> Unit) {
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

    fun dropPreviewInserting(filename: Filename) =
        applyInserting(filename) { preview = null }

    fun dropPreview(filename: Filename) {
        getDduFile(filename)?.let { dduFile ->
            dduFile.preview = null
            dduFileDao.update(dduFile)
        }
    }

    fun dropPreview(dduFile: DduFile) {
        dduFile.preview = null
        dduFileDao.update(dduFile)
    }

    fun getOriginalFilename(filename: Filename): Filename? =
        getDduFile(filename)?.originalFilename

    fun dropPreviewAndSetOriginalFilenameInserting(filename: Filename, newOriginalFilename: Filename) =
        applyInserting(filename) {
            preview = null
            originalFilename = newOriginalFilename
        }

    fun updateFilenameInserting(filename: Filename, newFilename: Filename) =
        applyInserting(filename) {
            this.filename = newFilename
        }

    fun getPreview(filename: Filename): Bitmap? =
        getDduFile(filename)?.preview

    fun setPreviewInserting(filename: Filename, newPreview: Bitmap) =
        applyInserting(filename) {
            preview = newPreview
        }

    companion object {
        val INSTANCE: DduFileRepository = DduFileRepository()
    }
}

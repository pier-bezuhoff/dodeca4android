package com.pierbezuhoff.dodeca.models

import android.content.Context
import android.graphics.Bitmap
import androidx.room.Room
import com.pierbezuhoff.dodeca.db.DduFile
import com.pierbezuhoff.dodeca.db.DduFileDao
import com.pierbezuhoff.dodeca.db.DduFileDatabase
import com.pierbezuhoff.dodeca.utils.Filename
import com.pierbezuhoff.dodeca.utils.filename
import java.io.File

class DduFileRepository private constructor(context: Context) {
    private val db: DduFileDatabase by lazy {
        Room.databaseBuilder(
            context.applicationContext,
            DduFileDatabase::class.java,
            DB_NAME
        )
            .addMigrations(DduFileDatabase.MIGRATION_3_4)
//            .fallbackToDestructiveMigration()
            .build()
    }
    private val dduFileDao: DduFileDao by lazy {
        db.dduFileDao()
    }

    private suspend fun getAllDduFiles(): List<DduFile> =
        dduFileDao.getAll()

    suspend fun dropAllPreviews() {
        getAllDduFiles().forEach {
            dropPreview(it)
        }
    }

    suspend fun delete(filename: Filename) =
        getDduFile(filename)?.let {
            dduFileDao.delete(it)
        }

    suspend fun delete(file: File) =
        delete(file.filename)

    private suspend fun getDduFile(filename: Filename): DduFile? =
        dduFileDao.findByFilename(filename)

    private suspend inline fun applyInserting(filename: Filename, crossinline action: DduFile.() -> Unit) {
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

    private suspend fun dropPreview(dduFile: DduFile) {
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
        @Volatile private var instance: DduFileRepository? = null
        private const val DB_NAME: String = "ddu-files"

        /** Thread-safe via double-checked locking */
        fun get(context: Context): DduFileRepository =
            instance ?: synchronized(this) {
                instance
                    ?: DduFileRepository(context).also {
                    instance = it
                }
            }
    }
}

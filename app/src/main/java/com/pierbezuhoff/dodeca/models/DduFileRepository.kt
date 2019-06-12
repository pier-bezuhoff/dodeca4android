package com.pierbezuhoff.dodeca.models

import android.content.Context
import android.graphics.Bitmap
import androidx.room.Room
import com.pierbezuhoff.dodeca.db.DduFile
import com.pierbezuhoff.dodeca.db.DduFileDao
import com.pierbezuhoff.dodeca.db.DduFileDatabase
import com.pierbezuhoff.dodeca.utils.Filename

/** Singleton repo */
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

//    suspend operator fun div(filename: Filename, action: DduFile.() -> Unit) {
//    }

    private suspend fun getAllDduFiles(): List<DduFile> =
        dduFileDao.getAll()

    suspend fun dropAllPreviews() {
        getAllDduFiles().forEach {
            it.preview = null
            dduFileDao.update(it)
        }
    }

    suspend fun deleteIfExists(filename: Filename): Boolean {
        val dduFile = getDduFile(filename)
        dduFile?.let {
            dduFileDao.delete(it)
        }
        return dduFile != null
    }

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

    private suspend inline fun apply(filename: Filename, crossinline action: DduFile.() -> Unit) {
        val dduFile: DduFile? = getDduFile(filename)
        require(dduFile != null) { "File $filename not found" }
        dduFile.action()
        dduFileDao.update(dduFile)
    }

    suspend fun dropPreviewInserting(filename: Filename) =
        applyInserting(filename) { preview = null }

    suspend fun dropPreview(filename: Filename) {
        apply(filename) {
            preview = null
        }
    }

    suspend fun getOriginalFilename(filename: Filename): Filename? =
        getDduFile(filename)?.originalFilename

    suspend fun dropPreviewAndSetOriginalFilename(filename: Filename, newOriginalFilename: Filename) =
        apply(filename) {
            preview = null
            originalFilename = newOriginalFilename
        }

    suspend fun updateFilename(filename: Filename, newFilename: Filename) =
        apply(filename) {
            this.filename = newFilename
        }

    suspend fun getPreview(filename: Filename): Bitmap? =
        getDduFile(filename)?.preview

    suspend fun setPreview(filename: Filename, newPreview: Bitmap) =
        apply(filename) {
            preview = newPreview
        }

    suspend fun insertIfAbsent(filename: Filename): Boolean {
        val dduFile = getDduFile(filename)
        val absent = dduFile == null
        if (absent)
            dduFileDao.insert(DduFile.fromFilename(filename))
        return absent
    }

    suspend fun duplicate(source: Filename, target: Filename) {
        require(getDduFile(target) == null)
        val sourceDduFile = getDduFile(source)
        val targetDduFile: DduFile =
            sourceDduFile?.copy(filename = target)
                ?: DduFile.fromFilename(target)
        dduFileDao.insert(targetDduFile)
    }

    suspend fun saveDerivative(source: Filename? = null, target: Filename) {
        val sourceDduFile = source?.let { getDduFile(it) }
        val oldTargetDduFile = getDduFile(target)
        val targetDduFile: DduFile =
            sourceDduFile?.copy(filename = target, preview = null)
                ?: oldTargetDduFile?.apply { preview = null }
                ?: DduFile.fromFilename(target)
        if (sourceDduFile == null && source != null)
            targetDduFile.originalFilename = source
        if (oldTargetDduFile == null)
            dduFileDao.insert(targetDduFile)
        else
            dduFileDao.update(targetDduFile)
    }

    suspend fun extract(target: Filename, originalFilename: Filename) {
        val oldTargetDduFile = getDduFile(target)
        val targetDduFile =
            DduFile.fromFilename(target).apply {
                this.originalFilename = originalFilename
            }
        val absent = oldTargetDduFile == null
        if (absent)
            dduFileDao.insert(targetDduFile)
        else
            dduFileDao.update(targetDduFile)
    }

    companion object {
        @Volatile private var instance: DduFileRepository? = null
        private const val DB_NAME: String = "ddu-files"

        /** Returns singleton repo, thread-safe via double-checked locking */
        fun get(context: Context): DduFileRepository =
            instance ?: synchronized(this) {
                instance
                    ?: DduFileRepository(context).also {
                    instance = it
                }
            }
    }
}

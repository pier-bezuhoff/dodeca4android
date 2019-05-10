package com.pierbezuhoff.dodeca.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import java.io.ByteArrayOutputStream

class BitmapConverter {
    @TypeConverter fun fromByteArray(byteArray: ByteArray?): Bitmap? = byteArray?.let {
        BitmapFactory.decodeByteArray(it, 0, byteArray.size)
    }

    @TypeConverter fun toByteArray(bitmap: Bitmap?): ByteArray? = bitmap?.let {
        val stream = ByteArrayOutputStream()
        val success = it.compress(Bitmap.CompressFormat.PNG, 100, stream)
        if (success) stream.toByteArray() else null
    }
}

// TODO: store relative path instead of filename
@Entity(indices = [Index("filename")])
data class DduFile(
    @ColumnInfo(name = "filename") var filename: Filename,
    @ColumnInfo(name = "original_filename") var originalFilename: Filename,
    @ColumnInfo(name = "preview" /*, typeAffinity = ColumnInfo.BLOB*/) var preview: Bitmap? = null
) {
    @PrimaryKey(autoGenerate = true) var uid: Int = 0
}

@Dao
interface DduFileDao {
    @Query("SELECT * FROM ddufile")
    fun getAll(): List<DduFile>

    @Query("SELECT * FROM ddufile WHERE uid IN (:dduFileIds)")
    fun loadAllByIds(dduFileIds: IntArray): List<DduFile>

    @Query("SELECT * FROM ddufile WHERE filename LIKE :filename LIMIT 1")
    fun findByFilename(filename: Filename): DduFile?

    @Update fun update(dduFile: DduFile)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg dduFiles: DduFile)

    @Delete fun delete(dduFile: DduFile)
}

fun DduFileDao.insertOrUpdate(filename: Filename, action: DduFile.() -> Unit) {
    val dduFile: DduFile? = findByFilename(filename)
    if (dduFile != null)
        update(dduFile.apply(action))
    else
        insert(DduFile(filename = filename, originalFilename = filename).apply(action))
}

fun DduFileDao.insertOrDropPreview(filename: Filename) {
    val dduFile: DduFile? = findByFilename(filename)
    if (dduFile != null) {
        dduFile.preview = null
        update(dduFile)
    } else {
        insert(DduFile(filename = filename, originalFilename = filename))
    }
}

@Database(entities = [DduFile::class], version = 2)
@TypeConverters(BitmapConverter::class)
abstract class DduFileDatabase : RoomDatabase() {
    abstract fun dduFileDao(): DduFileDao

    companion object {
        var INSTANCE: DduFileDatabase? = null
        fun init(context: Context) {
            if (INSTANCE == null)
                synchronized(DduFileDatabase) {
                    if (INSTANCE == null)
                        INSTANCE = Room.databaseBuilder(context, DduFileDatabase::class.java, "ddu-files")
                            .allowMainThreadQueries()
                            .build()
                }
        }
    }
}

val DB: DduFileDatabase by lazy { DduFileDatabase.INSTANCE!! }

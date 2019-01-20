package com.pierbezuhoff.dodeca

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

@Entity(indices = [Index("filename")])
data class DDUFile(
    @ColumnInfo(name = "original_filename") var originalFilename: String,
    @ColumnInfo(name = "filename") var filename: String,
    @ColumnInfo(name = "preview" /*, typeAffinity = ColumnInfo.BLOB*/) var preview: Bitmap
) {
    @PrimaryKey(autoGenerate = true) var uid: Int = 0
}

@Dao
interface DDUFileDao {
    @Query("SELECT * FROM ddufile")
    fun getAll(): List<DDUFile>

    @Query("SELECT * FROM ddufile WHERE uid IN (:dduFileIds)")
    fun loadAllByIds(dduFileIds: IntArray): List<DDUFile>

    @Query("SELECT * FROM ddufile WHERE filename LIKE :filename LIMIT 1")
    fun findByFilename(filename: String): DDUFile

    @Update fun update(dduFile: DDUFile)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg dduFiles: DDUFile)

    @Delete fun delete(dduFile: DDUFile)
}

@Database(entities = [DDUFile::class], version = 1)
@TypeConverters(BitmapConverter::class)
abstract class DDUFileDatabase : RoomDatabase() {
    abstract fun dduFileDao(): DDUFileDao

    companion object {
        var INSTANCE: DDUFileDatabase? = null
        fun init(context: Context) {
            if (INSTANCE == null)
                synchronized(DDUFileDatabase) {
                    if (INSTANCE == null)
                        INSTANCE = Room.databaseBuilder(
                            context, DDUFileDatabase::class.java, "ddu-files"
                        ).allowMainThreadQueries().build()
                }
        }
    }
}


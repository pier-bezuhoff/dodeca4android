package com.pierbezuhoff.dodeca.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [DduFile::class],
    version = 3
)
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

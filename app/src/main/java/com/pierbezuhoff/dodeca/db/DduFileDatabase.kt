package com.pierbezuhoff.dodeca.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [DduFile::class],
    version = 3
)
@TypeConverters(BitmapConverter::class)
abstract class DduFileDatabase : RoomDatabase() {
    abstract fun dduFileDao(): DduFileDao
}

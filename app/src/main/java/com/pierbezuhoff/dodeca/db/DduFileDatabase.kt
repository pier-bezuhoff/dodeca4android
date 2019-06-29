package com.pierbezuhoff.dodeca.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DduFile::class],
    version = 4
)
@TypeConverters(BitmapConverter::class, FilenameConverter::class)
abstract class DduFileDatabase : RoomDatabase() {
    abstract fun dduFileDao(): DduFileDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // nothing to do: db was not altered
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // nothing to do: db was not altered
            }
        }
    }
}

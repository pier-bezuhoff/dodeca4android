package com.pierbezuhoff.dodeca.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pierbezuhoff.dodeca.utils.Filename

@Dao
interface DduFileDao {
    @Query("SELECT * FROM ddufile")
    suspend fun getAll(): List<DduFile>

    @Query("SELECT * FROM ddufile WHERE uid IN (:dduFileIds)")
    suspend fun loadAllByIds(dduFileIds: IntArray): List<DduFile>

    @Query("SELECT * FROM ddufile WHERE filename LIKE :filename LIMIT 1")
    suspend fun findByFilename(filename: Filename): DduFile?

    @Update
    suspend fun update(dduFile: DduFile)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg dduFiles: DduFile)

    @Delete
    suspend fun delete(dduFile: DduFile)
}
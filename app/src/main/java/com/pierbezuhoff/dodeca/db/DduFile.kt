package com.pierbezuhoff.dodeca.db

import android.graphics.Bitmap
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pierbezuhoff.dodeca.utils.Filename

// TODO: store relative path instead of filename
@Entity(indices = [Index("filename")])
data class DduFile(
    @ColumnInfo(name = "filename") var filename: Filename,
    @ColumnInfo(name = "original_filename") var originalFilename: Filename,
    @ColumnInfo(name = "preview" /*, typeAffinity = ColumnInfo.BLOB*/) var preview: Bitmap? = null
) {
    @PrimaryKey(autoGenerate = true) var uid: Int = 0

    companion object {
        fun fromFilename(filename: Filename, originalFilename: Filename = filename): DduFile =
            DduFile(filename = filename, originalFilename = originalFilename)
    }
}
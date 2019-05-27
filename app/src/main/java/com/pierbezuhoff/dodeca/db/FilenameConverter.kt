package com.pierbezuhoff.dodeca.db

import androidx.room.TypeConverter
import com.pierbezuhoff.dodeca.utils.Filename

class FilenameConverter {
    @TypeConverter
    fun fromFilename(filename: Filename?): String? =
        filename?.toString()

    @TypeConverter
    fun toFilename(s: String?): Filename? =
        s?.let { Filename(it) }
}
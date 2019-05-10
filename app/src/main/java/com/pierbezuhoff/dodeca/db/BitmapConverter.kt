package com.pierbezuhoff.dodeca.db

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.room.TypeConverter
import java.io.ByteArrayOutputStream

class BitmapConverter {
    @TypeConverter
    fun fromByteArray(byteArray: ByteArray?): Bitmap? = byteArray?.let {
        BitmapFactory.decodeByteArray(it, 0, byteArray.size)
    }

    @TypeConverter
    fun toByteArray(bitmap: Bitmap?): ByteArray? = bitmap?.let {
        val stream = ByteArrayOutputStream()
        val success = it.compress(Bitmap.CompressFormat.PNG, 100, stream)
        if (success) stream.toByteArray() else null
    }
}
package com.pierbezuhoff.dodeca

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

// https://randula.wordpress.com/2013/01/01/android-save-images-to-database-sqlite-database/

class CompressedBitmap(var id: String, var byteArray: ByteArray)

class DBHelper(context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION) {
    override fun onCreate(db: SQLiteDatabase?) {
        db?.execSQL("CREATE TABLE $PREVIEWS_TABLE ($COL_ID INTEGER PRIMARY KEY, $PREVIEW_ID TEXT, $PREVIEW_BITMAP TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL("DROP TABLE IF EXISTS $PREVIEWS_TABLE")
        onCreate(db)
    }

    fun insertBitmap(bitmap: Bitmap, id: String) {
        val db = writableDatabase
        val values = ContentValues()
        values.put(PREVIEW_ID, id)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        values.put(PREVIEW_BITMAP, stream.toByteArray())
        db.insert(PREVIEWS_TABLE, null, values)
        db.close()
    }

    fun getPreview(id: String): CompressedBitmap? {
        val db = writableDatabase // why not readable?
        val cursor = db.query(
            PREVIEWS_TABLE, arrayOf(COL_ID, PREVIEW_ID, PREVIEW_BITMAP),
            "$PREVIEW_ID LIKE '$id%'", null, null, null, null)
        var preview: CompressedBitmap? = null
        if (cursor.moveToFirst()) {
            preview = CompressedBitmap(cursor.getString(1), cursor.getBlob(2))
//            do {
//                preview = CompressedBitmap(cursor.getString(1), cursor.getBlob(2))
//            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return preview
    }

    companion object {
        const val NAME: String = "previews"
        const val VERSION: Int = 1
        const val PREVIEWS_TABLE = "PreviewsTable"
        const val COL_ID = "col_id"
        const val PREVIEW_ID = "preview_id"
        const val PREVIEW_BITMAP = "preview_bitmap"
    }
}

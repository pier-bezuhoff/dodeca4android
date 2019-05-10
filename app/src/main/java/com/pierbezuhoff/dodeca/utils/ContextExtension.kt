package com.pierbezuhoff.dodeca.utils

import android.content.Context
import android.util.Log
import com.pierbezuhoff.dodeca.R
import com.pierbezuhoff.dodeca.db.DduFileRepository
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

val Context.dduDir get() =  File(filesDir, "ddu")
fun Context.dduPath(file: File): String =
    file.absolutePath.substringAfter(dduDir.absolutePath).trim('/')

// TODO: refactor!
fun Context.extract1Ddu(
    filename: Filename, dir: File,
    dduFileRepository: DduFileRepository,
    TAG: String,
    overwrite: Boolean = false
): Filename? {
    var source: Filename = filename
    fun streamFromDDUAsset(filename: Filename): InputStream =
        assets.open("${getString(R.string.ddu_asset_dir)}/$filename")
    val inputStream: InputStream? = try {
        streamFromDDUAsset(source)
    } catch (e: IOException) {
        dduFileRepository.getOriginalFilename(filename)?.let { originalSource ->
            try {
                source = originalSource
                streamFromDDUAsset(originalSource)
            } catch (e: IOException) { null }
        }
    }
    return inputStream?.let {
        val targetFile0 = File(dir, source)
        val targetFile =
            if (!overwrite && targetFile0.exists()) withUniquePostfix(targetFile0)
            else targetFile0
        targetFile.createNewFile()
        Log.i(TAG, "Copying asset $source to ${targetFile.path}")
        copyStream(inputStream, FileOutputStream(targetFile))
        dduFileRepository.dropPreviewAndSetOriginalFilenameInserting(targetFile.name, newOriginalFilename = source)
        targetFile.name
    } ?: null.also {
        Log.w(TAG, "Cannot find asset $filename ($source)")
    }
}



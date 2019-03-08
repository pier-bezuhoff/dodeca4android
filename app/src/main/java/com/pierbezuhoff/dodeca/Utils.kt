package com.pierbezuhoff.dodeca

import android.content.ContentResolver
import android.content.Context
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Timer
import kotlin.concurrent.timerTask

typealias FileName = String // without extension
typealias Filename = String // with extension
fun Filename.stripDDU(): FileName = this.removeSuffix(".ddu").removeSuffix(".DDU")
val File.isDDU: Boolean get() = extension.toLowerCase() == "ddu"

fun copyStream(inputStream: InputStream, outputStream: OutputStream) =
    inputStream.use { input -> outputStream.use { input.copyTo(it, DEFAULT_BUFFER_SIZE) } }

fun copyFile(source: File, target: File) {
    copyStream(source.inputStream(), target.apply { createNewFile() }.outputStream())
}

fun copyFile(contentResolver: ContentResolver, source: DocumentFile, target: File) {
    contentResolver.openInputStream(source.uri)?.let { inputStream ->
        copyStream(inputStream, target.apply { createNewFile() }.outputStream())
    }
}

fun copyDirectory(source: File, target: File) {
    target.mkdir()
    source.listFiles().forEach {
        if (it.isFile)
            copyFile(it, File(target, it.name))
        else if (it.isDirectory)
            copyDirectory(it, File(target, it.name))
    }
}

fun copyDirectory(contentResolver: ContentResolver, source: DocumentFile, target: File) {
    target.mkdir()
    source.listFiles().forEach {
        if (it.isFile)
            copyFile(contentResolver, it, File(target, it.name))
        else if (it.isDirectory)
            copyDirectory(contentResolver, it, File(target, it.name))
    }
}

val Context.dduDir get() =  File(filesDir, "ddu")
fun Context.dduPath(file: File): String =
    file.absolutePath.substringAfter(dduDir.absolutePath).trim('/')

fun Context.extract1DDU(filename: Filename, dir: File, dduFileDao: DDUFileDao, TAG: String): Filename? {
    var source: Filename = filename
    fun streamFromDDUAsset(filename: Filename): InputStream =
        assets.open("${getString(R.string.ddu_asset_dir)}/$filename")
    val inputStream: InputStream? = try {
        streamFromDDUAsset(source)
    } catch (e: IOException) {
        dduFileDao.findByFilename(filename)?.let {
            source = it.originalFilename
            try {
                streamFromDDUAsset(source)
            } catch (e: IOException) { null }
        }
    }
    val success = inputStream?.let {
        val targetFile = File(dir, source)
        if (targetFile.createNewFile()) {
            Log.i(TAG, "Copying asset $source to ${targetFile.path}")
        } else {
            Log.i(TAG, "Overwriting ${targetFile.path} by asset $source")
        }
        copyStream(inputStream, FileOutputStream(targetFile))
        dduFileDao.insertOrUpdate(source) { preview = null }
    }
    return if (success != null) source
    else null.also {
        Log.w(TAG, "cannot find asset $filename ($source)")
    }
}


class FlexibleTimer(val timeMilis: Long, val action: () -> Unit) {
    private var timer: Timer? = null

    fun start() {
        timer?.cancel()
        timer = Timer().apply {
            schedule(timerTask { action() }, timeMilis)
        }
    }

    fun stop() {
        timer?.cancel()
        timer = null
    }
}


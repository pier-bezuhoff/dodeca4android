package com.pierbezuhoff.dodeca.utils

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.InputStream
import java.io.OutputStream

typealias FileName = String // without extension
typealias Filename = String // with extension
val Filename.isDDU: Boolean get() = endsWith(".ddu", ignoreCase = true)
fun Filename.stripDdu(): FileName = this.removeSuffix(".ddu").removeSuffix(".Ddu")
val File.isDdu: Boolean get() = extension.toLowerCase() == "ddu"
val DocumentFile.isDDU: Boolean get() = name?.isDDU ?: false

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

/* add digital postfix if [file] already exists */
fun withUniquePostfix(file: File, allFiles: List<File> = file.siblings()): File {
    val fileName = file.nameWithoutExtension
    val part1 = Regex("^(.*)-(\\d*)$")
    fun namePart1(s: String): String = part1.find(s)?.groupValues?.let { it[1] } ?: s
    val name = namePart1(fileName)
    val postfixes: Set<Int> = allFiles
        .asSequence()
        .filter { it.isDdu }
        .map {
            it.nameWithoutExtension.let { name ->
                part1.find(name)?.groupValues
                    ?.let { it[1] to it[2].toInt() }
                    ?: name to null
            }
        }
        .filter { (_name, postfix) -> _name == name && postfix != null }
        .map { it.second!! }
        .toSet()
    val newPostfix = generateSequence(1, Int::inc)
        .filter { it !in postfixes }
        .first()
    val newFileName = "$name-$newPostfix"
    return File(file.parentFile, "$newFileName.ddu")
}

fun File.siblings(): List<File> = parentFile.listFiles().toList()

// source: https://developer.android.com/guide/topics/providers/document-provider.html#metadata
fun ContentResolver.getDisplayName(uri: Uri): Filename? {
    var filename: Filename? = null
    val cursor: Cursor? = query(uri, null, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val displayName: String? =
                it.getString(it.getColumnIndex(OpenableColumns.DISPLAY_NAME))
            filename = displayName
        }
    }
    return filename
}


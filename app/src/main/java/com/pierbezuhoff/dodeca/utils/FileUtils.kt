package com.pierbezuhoff.dodeca.utils

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** File name (without extension) */
data class FileName(private val fileName: String) {
    override fun toString(): String = fileName
    companion object {
        fun of(file: File): FileName =
            FileName(file.nameWithoutExtension)
    }
}
val File.fileName: FileName get() = FileName.of(this)

/** File name (with extension) */
data class Filename(private val filename: String) {
    val fileName: FileName get() = FileName.of(File(filename))
    val extension: String get() = File(filename).extension
    val isDdu: Boolean get() = extension.toLowerCase() == "ddu"
    override fun toString(): String = filename
    fun toFile(parent: File? = null): File =
        File(parent, filename)
    companion object {
        fun of(file: File): Filename =
            Filename(file.name)
        fun of(file: DocumentFile): Filename? =
            file.name?.let { Filename(it) }
    }
}
val File.filename: Filename get() = Filename.of(this)
val File.absoluteFilename: Filename get() = Filename(absolutePath)
val DocumentFile.filename: Filename? get() = Filename.of(this)
operator fun File.div(filename: Filename) =
    File(this, filename.toString())

/** Absolute file path */
data class FilePath(private val path: String) {
    val fileName: FileName get() = filename.fileName
    val extension: String get() = filename.extension
    val filename: Filename get() = Filename.of(toFile())
    val isDdu: Boolean get() = filename.isDdu
    override fun toString(): String = path
    fun toFile(): File =
        File(path)
    companion object {
        fun of(file: File): FilePath =
            FilePath(file.absolutePath)
    }
}
val File.filePath: FilePath get() = FilePath.of(this)

val File.isDdu: Boolean get() = filename.isDdu
val DocumentFile.isDdu: Boolean? get() = filename?.isDdu

suspend fun copyStream(
    inputStream: InputStream,
    outputStream: OutputStream
): Long = withContext(Dispatchers.IO) {
    inputStream.use { input -> outputStream.use { input.copyTo(it, DEFAULT_BUFFER_SIZE) } }
}

suspend fun copyFile(source: File, target: File) {
    withContext(Dispatchers.IO) {
        copyStream(source.inputStream(), target.apply { createNewFile() }.outputStream())
    }
}

suspend fun ContentResolver.copyFile(source: DocumentFile, target: File) {
    withContext(Dispatchers.IO) {
        openInputStream(source.uri)?.let { inputStream ->
            copyStream(inputStream, target.apply { createNewFile() }.outputStream())
        }
    }
}

suspend fun copyDirectory(source: File, target: File) {
    withContext(Dispatchers.IO) {
        target.mkdir()
        source.listFiles().forEach {
            if (it.isFile)
                copyFile(it, File(target, it.name))
            else if (it.isDirectory)
                copyDirectory(it, File(target, it.name))
        }
    }
}

suspend fun ContentResolver.copyDirectory(source: DocumentFile, target: File) {
    withContext(Dispatchers.IO) {
        target.mkdir()
        source.listFiles().forEach {
            if (it.isFile)
                this@copyDirectory.copyFile(it, File(target, it.name))
            else if (it.isDirectory)
                this@copyDirectory.copyDirectory(source = it, target = File(target, it.name))
        }
    }
}

/** Add unique digital postfix if [file] already exists */
suspend fun withUniquePostfix(file: File, extenesion: String = "ddu"): File = withContext(Dispatchers.IO) {
    val allFiles: List<File> = file.siblings()
    val fileName = file.fileName
    val part1 = Regex("^(.*)-(\\d*)$") // parse file name as "[namePart1]-[digital postfix]"
    /** namePart1("<name>-<digital postfix>") = "<name>" */
    fun namePart1(fileName: FileName): String = fileName.toString().let { s ->
        part1.find(s)?.groupValues?.let { it[1] } ?: s
    }
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
            // result: Sequence<Pair<String, Int?>>
            // result = Sequence of ((namePart1, digitalPostfix) or (name, null))
        }
        .filter { (_name, postfix) -> _name == name && postfix != null }
        .map { it.second!! }
        .toSet()
    val newPostfix = generateSequence(1, Int::inc)
        .filter { it !in postfixes }
        .first()
    val newFileName = "$name-$newPostfix"
    return@withContext File(file.parentFile, "$newFileName.$extenesion")
}

suspend fun File.siblings(): List<File> = withContext(Dispatchers.IO) {
    parentFile.listFiles().toList()
}

// source: https://developer.android.com/guide/topics/providers/document-provider.html#metadata
fun ContentResolver.getDisplayName(uri: Uri): Filename? {
    var filename: Filename? = null
    val cursor: Cursor? = query(uri, null, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val displayName: String? =
                it.getString(it.getColumnIndex(OpenableColumns.DISPLAY_NAME))
            displayName?.let {
                filename = Filename(it)
            }
        }
    }
    return filename
}


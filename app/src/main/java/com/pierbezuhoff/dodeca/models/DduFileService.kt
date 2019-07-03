package com.pierbezuhoff.dodeca.models

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.pierbezuhoff.dodeca.R
import com.pierbezuhoff.dodeca.data.Ddu
import com.pierbezuhoff.dodeca.utils.Filename
import com.pierbezuhoff.dodeca.utils.copyDirectory
import com.pierbezuhoff.dodeca.utils.copyFile
import com.pierbezuhoff.dodeca.utils.copyStream
import com.pierbezuhoff.dodeca.utils.div
import com.pierbezuhoff.dodeca.utils.filename
import com.pierbezuhoff.dodeca.utils.getDisplayName
import com.pierbezuhoff.dodeca.utils.isDdu
import com.pierbezuhoff.dodeca.utils.withUniquePostfix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

/** Helper for file-related operations, [context] should be applicationContext */
class DduFileService(private val context: Context) {
    private val dduFileRepository: DduFileRepository = DduFileRepository.get(context)
    val dduDir: File = File(context.filesDir, "ddu")

    /** Path, relative to [dduDir] */
    fun dduPathOf(file: File): Filename =
        Filename(file.absolutePath.substringAfter(dduDir.absolutePath).trim('/'))

    suspend fun extractDduAssets(
        targetDir: File = dduDir,
        overwrite: Boolean = false,
        onlyNew: Boolean = false
    ) {
        require(targetDir.isDirectory)
        withContext(Dispatchers.IO) {
            if (!targetDir.exists())
                targetDir.mkdir()
            context.assets
                .list(context.getString(R.string.ddu_asset_dir))
                ?.forEach { name ->
                    extractDduAsset(Filename(name), targetDir, overwrite = overwrite, onlyNew = onlyNew)
                }
        }
    }

    suspend fun extractDduAsset(
        filename: Filename,
        targetDir: File = dduDir,
        overwrite: Boolean = false,
        onlyNew: Boolean = false
    ): File? {
        require(targetDir.isDirectory)
        return withContext(Dispatchers.IO) {
            openStreamFromOriginalDduAsset(filename)?.let { (source: Filename, inputStream: InputStream) ->
                val targetFile0: File = targetDir / source
                if (targetFile0.exists() && onlyNew)
                    return@let null
                val targetFile =
                    if (!overwrite && targetFile0.exists()) withUniquePostfix(targetFile0)
                    else targetFile0
                targetFile.createNewFile()
                Log.i(TAG, "Copying asset $source to $targetFile")
                copyStream(inputStream, FileOutputStream(targetFile))
                dduFileRepository.extract(targetFile.filename, originalFilename = source)
                return@let targetFile
            }
        }
    }

    private suspend fun openStreamFromOriginalDduAsset(filename: Filename): Pair<Filename, InputStream>? {
        val dduFileRepository = DduFileRepository.get(context)
        var source: Filename = filename
        val inputStream: InputStream? = try {
            openStreamFromDduAsset(source)
        } catch (e: IOException) {
            dduFileRepository.getOriginalFilename(filename)?.let { originalSource: Filename ->
                try {
                    source = originalSource
                    return@let openStreamFromDduAsset(originalSource)
                } catch (e: IOException) {
                    Log.w(TAG, "Cannot find asset $filename (original: $originalSource)")
                    return@let null
                }
            }
        }
        return inputStream?.let { source to inputStream }
    }

    private fun openStreamFromDduAsset(filename: Filename): InputStream =
        context.assets.open("${context.getString(R.string.ddu_asset_dir)}/$filename")

    suspend fun importByUris(
        uris: List<Uri>, targetDir: File = dduDir,
        defaultFilename: Filename = DEFAULT_FILENAME,
        defaultExtension: String? = DEFAULT_EXTENSION
    ): List<File?> {
        require(targetDir.isDirectory)
        return withContext(Dispatchers.IO) {
            uris.map { importByUri(it, targetDir, defaultFilename, defaultExtension) }
        }
    }

    suspend fun importByUri(
        uri: Uri, targetDir: File = dduDir,
        defaultFilename: Filename = DEFAULT_FILENAME,
        defaultExtension: String? = DEFAULT_EXTENSION
    ): File? {
        require(targetDir.isDirectory)
        return withContext(Dispatchers.IO) {
            DocumentFile.fromSingleUri(context, uri)?.let { file ->
                val displayName: Filename? by lazy {
                    context.contentResolver.getDisplayName(uri)?.toString()
                        ?.let {
                            Filename(
                                if ('.' !in it && defaultExtension != null)
                                    "$it.$defaultExtension"
                                else it
                            )
                        }
                }
                val filename: Filename =
                    file.filename ?: displayName ?: defaultFilename
                val target0: File = targetDir / filename
                val target: File =
                    if (target0.exists()) withUniquePostfix(target0)
                    else target0
                context.contentResolver.copyFile(file, target)
                return@let target
            }
        }
    }

    suspend fun importDir(dir: DocumentFile, targetDir: File = dduDir): File {
        require(dir.isDirectory)
        require(targetDir.isDirectory)
        return withContext(Dispatchers.IO) {
            val target = File(targetDir, dir.name)
            context.contentResolver.copyDirectory(dir, target)
            return@withContext target
        }
    }

    suspend fun exportFileIntoUri(file: File, targetFileUri: Uri): Boolean {
        require(file.isFile)
        return withContext(Dispatchers.IO) {
            var success = false
            context.contentResolver.openOutputStream(targetFileUri)?.let { outputStream ->
                copyStream(file.inputStream(), outputStream)
                success = true
            }
            return@withContext success
        }
    }

    suspend fun exportDduFileForDodecaLookIntoUri(file: File, targetFileUri: Uri): Boolean {
        require(file.isFile && file.isDdu)
        return withContext(Dispatchers.IO) {
            var success = false
            context.contentResolver.openOutputStream(targetFileUri)?.let { outputStream ->
                Ddu.fromFile(file).saveToStreamForDodecaLook(outputStream)
                success = true
            }
            return@withContext success
        }
    }

    suspend fun exportDirIntoUri(dir: File, targetUri: Uri) {
        require(dir.isDirectory)
        withContext(Dispatchers.IO) {
            DocumentFile.fromTreeUri(context, targetUri)
                ?.let { targetDir ->
                    exportIntoDocumentFile(dir, targetDir)
                }
        }
    }

    private suspend fun exportIntoDocumentFile(source: File, targetDir: DocumentFile) {
        require(targetDir.isDirectory)
        withContext(Dispatchers.IO) {
            when {
                source.isDirectory -> targetDir.createDirectory(source.name)?.let { newDir ->
                    source.listFiles().forEach { exportIntoDocumentFile(it, newDir) }
                }
                source.isDdu -> targetDir.createFile("*/*", source.name)?.let { newFile ->
                    context.contentResolver.openOutputStream(newFile.uri)?.let { outputStream ->
                        copyStream(source.inputStream(), outputStream)
                    }
                }
                else -> Unit
            }
        }
    }

    suspend fun renameDduFile(file: File, newFilename: Filename): File? = withContext(Dispatchers.IO) {
        require(file.isFile && file.isDdu)
        val newFile = file.parentFile/newFilename
        val success = file.renameTo(newFile)
        if (success) {
            dduFileRepository.updateFilename(file.filename, newFilename = newFilename)
        }
        return@withContext if (success) newFile else null
    }

    suspend fun cleanDduDir(dir: File) {
        require(dir.isDirectory)
        withContext(Dispatchers.IO) {
            for (file: File in dir.walkTopDown())
                if (file.isDdu)
                    dduFileRepository.deleteIfExists(file.filename)
            FileUtils.cleanDirectory(dir)
        }
    }

    suspend fun deleteDduDir(dir: File): Boolean {
        require(dir.isDirectory)
        return withContext(Dispatchers.IO) {
            for (file: File in dir.walkTopDown())
                if (file.isDdu)
                    dduFileRepository.deleteIfExists(file.filename)
            val success = dir.deleteRecursively()
            return@withContext success
        }
    }

    suspend fun deleteDduFile(file: File) {
        require(file.isFile && file.isDdu)
        withContext(Dispatchers.IO) {
            dduFileRepository.deleteIfExists(file.filename)
            file.delete()
        }
    }

    suspend fun duplicateDduFile(file: File): File {
        require(file.isFile && file.isDdu)
        return withContext(Dispatchers.IO) {
            val newFile = withUniquePostfix(file)
            copyFile(file, newFile)
            dduFileRepository.duplicate(file.filename, newFile.filename)
            return@withContext newFile
        }
    }

    companion object {
        private const val TAG = "DduFileService"
        private val DEFAULT_FILENAME = Filename("untitled")
        private val DEFAULT_EXTENSION: String? = null
    }
}
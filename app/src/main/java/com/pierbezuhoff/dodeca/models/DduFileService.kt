package com.pierbezuhoff.dodeca.models

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.pierbezuhoff.dodeca.R
import com.pierbezuhoff.dodeca.utils.Filename
import com.pierbezuhoff.dodeca.utils.copyFile
import com.pierbezuhoff.dodeca.utils.copyStream
import com.pierbezuhoff.dodeca.utils.div
import com.pierbezuhoff.dodeca.utils.filename
import com.pierbezuhoff.dodeca.utils.getDisplayName
import com.pierbezuhoff.dodeca.utils.withUniquePostfix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

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
    ): File? = withContext(Dispatchers.IO) {
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
        defaultFilename: Filename = Filename("untitled")
    ): List<File?> =
        uris.map { importByUri(it, targetDir, defaultFilename) }

    suspend fun importByUri(
        uri: Uri, targetDir: File = dduDir,
        defaultFilename: Filename = Filename("untitled")
    ): File? =
        DocumentFile.fromSingleUri(context, uri)?.let { file ->
            val displayName: Filename? by lazy {
                context.contentResolver.getDisplayName(uri)?.toString()
                    ?.let {
                        Filename(if ('.' !in it) "$it.ddu" else it)
                    }
            }
            val filename: Filename =
                file.filename ?: displayName ?: defaultFilename
            val target0: File = targetDir / filename
            val target: File =
                if (target0.exists()) withUniquePostfix(target0)
                else target0
            Log.i(TAG, "importing file \"${target.name}\" from \"${file.uri}\"")
            context.contentResolver.copyFile(file, target)
            return@let target
    }

    companion object {
        private const val TAG = "DduFileService"
    }
}
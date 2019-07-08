package com.pierbezuhoff.dodeca.ui.dodecashow

import android.util.Log
import com.pierbezuhoff.dodeca.data.Ddu
import com.pierbezuhoff.dodeca.utils.filename
import com.pierbezuhoff.dodeca.utils.isDdu
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import java.io.File

class DduFileRing(private val dir: File, private val scope: CoroutineScope) {
    lateinit var currentHead: File
    val files: Array<File> = dir.listFiles { file: File -> file.isDdu }
    private val deferredDdus: MutableMap<File, Deferred<Ddu>> = mutableMapOf()
    private var nowReading: Pair<File, Deferred<Ddu>>? = null

    fun setHeadAsync(head: File): Deferred<Ddu> =
        shiftHeadAsync(head)

    fun nextHeadAsync(): Deferred<Ddu> =
        shiftHeadAsync(currentHead, delta = +1)

    fun previousHeadAsync(): Deferred<Ddu> =
        shiftHeadAsync(currentHead, delta = -1)

    private fun shiftHeadAsync(oldHead: File, delta: Int = 0): Deferred<Ddu> {
        val head = shiftFile(oldHead, delta)
        currentHead = head
        nowReading?.let { (file: File, deferredDdu: Deferred<Ddu>) ->
            if (file != head)
                deferredDdu.cancel()
        }
        return readFilesAsync(readingIteratorOf(head))!!
    }

    /** Start reading files from iterator, async return first ddu (if iterator is not empty). */
    private fun readFilesAsync(iterator: Iterator<File>): Deferred<Ddu>? {
        if (iterator.hasNext()) {
            val file = iterator.next()
            val oldDeferredDdu = deferredDdus[file]
            if (oldDeferredDdu != null) {
                readFilesAsync(iterator)
                return oldDeferredDdu
            } else {
                val deferredDdu = scope.async {
                    Log.i(TAG, "reading ${file.filename}")
                    val ddu = Ddu.fromFile(file)
                    Log.i(TAG, "read ${file.filename}")
                    readFilesAsync(iterator)
                    return@async ddu
                }
                nowReading = file to deferredDdu
                deferredDdus[file] = deferredDdu
                return deferredDdu
            }
        }
        return null
    }

    private fun readingIteratorOf(head: File): Iterator<File> =
        READ_SEQUENCE
            .map { delta -> shiftFile(head, delta) }
            .iterator()

    private fun shiftFile(file: File, delta: Int): File {
        require(file in files)
        return files[(files.indexOf(file) + files.size + delta) % files.size]
    }

    companion object {
        private const val TAG = "DduFileRing"
        // current, next, previous, second next
        private val READ_SEQUENCE = sequenceOf(0, 1, -1, 2)
    }
}
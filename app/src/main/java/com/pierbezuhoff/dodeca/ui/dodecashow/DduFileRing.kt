package com.pierbezuhoff.dodeca.ui.dodecashow

import com.pierbezuhoff.dodeca.data.Ddu
import com.pierbezuhoff.dodeca.utils.isDdu
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import java.io.File

class DduFileRing(dir: File, private val scope: CoroutineScope) {
    private lateinit var currentHead: File
    private val files: Array<File> = dir.listFiles { file, _ -> file.isDdu }
    private val ddus: MutableMap<File, Ddu> = mutableMapOf()
    private val deferredDdus: MutableMap<File, Deferred<Ddu>> = mutableMapOf()

    suspend fun setHead(head: File): Ddu {
        require(head in files)
        currentHead = head
        return ddus[head].also { startReadingNeighbors(head) }
            ?: run {
                val oldDeferredDdu = deferredDdus[head]
                // cancel less important jobs
                for ((file: File, job: Job) in deferredDdus)
                    if (file != head)
                        job.cancel()
                deferredDdus.clear()
                val deferredDdu: Deferred<Ddu> = oldDeferredDdu ?: readHeadAsync(head)
                deferredDdus[head] = deferredDdu
                val ddu = deferredDdu.await()
                // previous async probably has not been started as [readHeadAsync], so we should [startReadingNeighbors]
                if (oldDeferredDdu != null)
                    startReadingNeighbors(head)
                return@run ddu
            }
    }

    suspend fun nextFile(): Ddu {
        val size = files.size
        return if (size >= 2) {
            val ix = files.indexOf(currentHead)
            val nextFile = files[(ix + 1) % size]
            setHead(nextFile)
        } else
            ddus[currentHead] ?: deferredDdus[currentHead]!!.await()
    }

    suspend fun previousFile(): Ddu {
        val size = files.size
        return if (size >= 2) {
            val ix = files.indexOf(currentHead)
            val previousFile = files[(ix + size - 1) % size]
            setHead(previousFile)
        } else
            ddus[currentHead] ?: deferredDdus[currentHead]!!.await()
    }

    private fun readHeadAsync(head: File): Deferred<Ddu> =
        scope.async {
            val ddu = Ddu.fromFile(head)
            ddus[head] = ddu
            startReadingNeighbors(head)
            return@async ddu
        }

    private fun startReadingNeighbors(head: File) {
        val ix = files.indexOf(head)
        val size = files.size
        if (size >= 2) {
            val nextFile = files[(ix + 1) % size]
            deferReadingDduFile(nextFile)
        }
        if (size >= 3) {
            val previousFile = files[(ix + size - 1) % size]
            deferReadingDduFile(previousFile)
        }
    }

    private fun deferReadingDduFile(file: File) {
        if (ddus[file] == null && deferredDdus[file] == null)
            deferredDdus[file] = scope.async {
                val ddu = Ddu.fromFile(file)
                ddus[file] = ddu
                return@async ddu
            }
    }
}
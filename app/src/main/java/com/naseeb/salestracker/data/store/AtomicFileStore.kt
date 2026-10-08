package com.naseeb.salestracker.data.store

import android.util.AtomicFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream

class AtomicFileStore(
    private val file: File,
    private val ioDispatcher: CoroutineDispatcher
) : FileStore {
    private val atomicFile = AtomicFile(file)

    override suspend fun read(): String? = withContext(ioDispatcher) {
        try {
            atomicFile.openRead().use { it.bufferedReader().readText() }
        } catch (e: FileNotFoundException) {
            null
        }
    }

    override suspend fun write(text: String) = withContext(ioDispatcher) {
        var stream: FileOutputStream? = null
        try {
            stream = atomicFile.startWrite()
            stream.write(text.toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(stream)
        } catch (e: Exception) {
            atomicFile.failWrite(stream)
            throw e
        }
    }

    override suspend fun writeBackup(name: String, text: String) = withContext(ioDispatcher) {
        val backupFile = File(file.parentFile, name)
        val backupAtomic = AtomicFile(backupFile)
        var stream: FileOutputStream? = null
        try {
            stream = backupAtomic.startWrite()
            stream.write(text.toByteArray(Charsets.UTF_8))
            backupAtomic.finishWrite(stream)
        } catch (e: Exception) {
            backupAtomic.failWrite(stream)
            throw e
        }
    }
}

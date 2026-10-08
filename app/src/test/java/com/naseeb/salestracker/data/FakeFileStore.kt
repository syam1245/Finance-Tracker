package com.naseeb.salestracker.data

import com.naseeb.salestracker.data.store.FileStore

class FakeFileStore : FileStore {
    var fileContent: String? = null
    var shouldThrowOnRead = false
    var shouldThrowOnWrite = false
    var writeCount = 0
    var backupCount = 0
    val backups = mutableMapOf<String, String>()

    override suspend fun read(): String? {
        if (shouldThrowOnRead) throw Exception("Fake IO Error on Read")
        return fileContent
    }

    override suspend fun write(text: String) {
        if (shouldThrowOnWrite) throw Exception("Fake IO Error")
        writeCount++
        fileContent = text
    }

    override suspend fun writeBackup(name: String, text: String) {
        if (shouldThrowOnWrite) throw Exception("Fake IO Error on Backup")
        backupCount++
        backups[name] = text
    }
}

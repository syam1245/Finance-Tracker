package com.naseeb.salestracker.data

import com.naseeb.salestracker.data.store.FileStore

class FakeFileStore : FileStore {
    var fileContent: String? = null
    var shouldThrowOnWrite = false
    val backups = mutableMapOf<String, String>()

    override suspend fun read(): String? = fileContent

    override suspend fun write(text: String) {
        if (shouldThrowOnWrite) throw Exception("Fake IO Error")
        fileContent = text
    }

    override suspend fun writeBackup(name: String, text: String) {
        if (shouldThrowOnWrite) throw Exception("Fake IO Error on Backup")
        backups[name] = text
    }
}

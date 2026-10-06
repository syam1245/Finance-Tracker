package com.naseeb.salestracker.data.store

interface FileStore {
    suspend fun read(): String?
    suspend fun write(text: String)
    suspend fun writeBackup(name: String, text: String)
}

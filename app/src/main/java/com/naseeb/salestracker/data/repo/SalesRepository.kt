package com.naseeb.salestracker.data.repo

import com.naseeb.salestracker.data.model.SalesEntry
import kotlinx.coroutines.flow.StateFlow

data class LoadReport(val loaded: Int, val skipped: Int)

interface SalesRepository {
    val entries: StateFlow<List<SalesEntry>>
    val loadReport: StateFlow<LoadReport?>
    suspend fun load()
    suspend fun add(entry: SalesEntry): SaveResult
    suspend fun update(entry: SalesEntry): SaveResult
    suspend fun delete(id: String): DeleteResult
    suspend fun wipe(): WipeResult
    suspend fun import(entries: List<SalesEntry>): ImportResult
}

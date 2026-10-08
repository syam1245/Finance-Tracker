package com.naseeb.salestracker.data.repo

import com.naseeb.salestracker.data.model.AppSettings
import kotlinx.coroutines.flow.StateFlow

interface SettingsRepository {
    val settings: StateFlow<AppSettings>

    suspend fun load()
    suspend fun save(newSettings: AppSettings): Boolean
    suspend fun addShop(name: String): Boolean
    suspend fun removeShop(name: String): Boolean
    suspend fun renameShop(oldName: String, newName: String): Boolean
    suspend fun addFinanceOption(name: String): Boolean
    suspend fun removeFinanceOption(name: String): Boolean
    suspend fun renameFinanceOption(oldName: String, newName: String): Boolean
    suspend fun resetToDefaults(): Boolean
}

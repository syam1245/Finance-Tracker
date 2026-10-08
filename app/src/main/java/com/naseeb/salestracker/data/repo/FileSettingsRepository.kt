package com.naseeb.salestracker.data.repo

import com.naseeb.salestracker.data.model.AppSettings
import com.naseeb.salestracker.data.store.FileStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class FileSettingsRepository @Inject constructor(
    @Named("settings") private val fileStore: FileStore
) : SettingsRepository {

    private val mutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val _settings = MutableStateFlow(AppSettings())
    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private var isLoaded = false

    override suspend fun load() = mutex.withLock {
        loadInternal()
    }

    private suspend fun loadInternal() {
        val text = try {
            fileStore.read()
        } catch (_: Exception) {
            // A read error is not an empty settings file. Leave the repository unloaded so
            // a later operation can retry without replacing user settings with defaults.
            return
        }

        if (text.isNullOrBlank()) {
            val defaults = AppSettings()
            try {
                fileStore.write(json.encodeToString(defaults))
            } catch (_: Exception) {}
            _settings.value = defaults
            isLoaded = true
            return
        }

        try {
            val parsed = json.decodeFromString<AppSettings>(text.trim())
            // Guard against empty lists
            val validShops = if (parsed.shopList.isEmpty()) AppSettings.DEFAULT_SHOPS else parsed.shopList
            val validFinances = if (parsed.financeOptions.isEmpty()) AppSettings.DEFAULT_FINANCE_OPTIONS else parsed.financeOptions
            val clean = parsed.copy(shopList = validShops, financeOptions = validFinances)
            _settings.value = clean
        } catch (_: Exception) {
            try {
                fileStore.writeBackup("app_settings.corrupt-${System.currentTimeMillis()}.json", text)
            } catch (_: Exception) {}
            _settings.value = AppSettings()
        }
        isLoaded = true
    }

    private suspend fun ensureLoaded(): Boolean {
        if (!isLoaded) {
            loadInternal()
        }
        return isLoaded
    }

    override suspend fun save(newSettings: AppSettings): Boolean = mutex.withLock {
        if (!ensureLoaded()) return false
        writeToStore(newSettings)
    }

    private suspend fun writeToStore(newSettings: AppSettings): Boolean {
        val text = json.encodeToString(newSettings)
        return try {
            fileStore.write(text)
            _settings.value = newSettings
            true
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun addShop(name: String): Boolean = mutex.withLock {
        if (!ensureLoaded()) return false
        val trimmed = name.trim().uppercase()
        if (trimmed.isBlank()) return false
        val current = _settings.value.shopList
        if (current.any { it.equals(trimmed, ignoreCase = true) }) return false
        val updated = current + trimmed
        writeToStore(_settings.value.copy(shopList = updated))
    }

    override suspend fun removeShop(name: String): Boolean = mutex.withLock {
        if (!ensureLoaded()) return false
        val current = _settings.value.shopList
        if (current.size <= 1) return false // Keep at least one shop
        val updated = current.filterNot { it.equals(name.trim(), ignoreCase = true) }
        if (updated.size == current.size) return false
        writeToStore(_settings.value.copy(shopList = updated))
    }

    override suspend fun renameShop(oldName: String, newName: String): Boolean = mutex.withLock {
        if (!ensureLoaded()) return false
        val trimmedNew = newName.trim().uppercase()
        if (trimmedNew.isBlank()) return false
        val current = _settings.value.shopList
        if (current.any { it.equals(trimmedNew, ignoreCase = true) && !it.equals(oldName.trim(), ignoreCase = true) }) {
            return false // Duplicate name
        }
        val updated = current.map { if (it.equals(oldName.trim(), ignoreCase = true)) trimmedNew else it }
        writeToStore(_settings.value.copy(shopList = updated))
    }

    companion object {
        val RESERVED_FINANCE_NAMES = setOf(
            "TOTAL FINANCE",
            "TOTAL COUNT",
            "DATE",
            "SHOP NAME",
            "CD",
            "MOBILE"
        )
    }

    override suspend fun addFinanceOption(name: String): Boolean = mutex.withLock {
        if (!ensureLoaded()) return false
        val trimmed = name.trim()
        if (trimmed.isBlank()) return false
        if (RESERVED_FINANCE_NAMES.any { it.equals(trimmed, ignoreCase = true) }) return false
        val current = _settings.value.financeOptions
        if (current.any { it.equals(trimmed, ignoreCase = true) }) return false
        val updated = current + trimmed
        writeToStore(_settings.value.copy(financeOptions = updated))
    }

    override suspend fun removeFinanceOption(name: String): Boolean = mutex.withLock {
        if (!ensureLoaded()) return false
        val current = _settings.value.financeOptions
        if (current.size <= 1) return false // Keep at least one finance option
        val updated = current.filterNot { it.equals(name.trim(), ignoreCase = true) }
        if (updated.size == current.size) return false
        writeToStore(_settings.value.copy(financeOptions = updated))
    }

    override suspend fun renameFinanceOption(oldName: String, newName: String): Boolean = mutex.withLock {
        if (!ensureLoaded()) return false
        val trimmedNew = newName.trim()
        if (trimmedNew.isBlank()) return false
        if (RESERVED_FINANCE_NAMES.any { it.equals(trimmedNew, ignoreCase = true) }) return false
        val current = _settings.value.financeOptions
        if (current.any { it.equals(trimmedNew, ignoreCase = true) && !it.equals(oldName.trim(), ignoreCase = true) }) {
            return false
        }
        val updated = current.map { if (it.equals(oldName.trim(), ignoreCase = true)) trimmedNew else it }
        writeToStore(_settings.value.copy(financeOptions = updated))
    }

    override suspend fun resetToDefaults(): Boolean = mutex.withLock {
        if (!ensureLoaded()) return false
        writeToStore(AppSettings())
    }
}

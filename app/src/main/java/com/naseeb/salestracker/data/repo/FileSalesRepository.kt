package com.naseeb.salestracker.data.repo

import com.naseeb.salestracker.data.model.SalesEntry
import com.naseeb.salestracker.data.model.SalesEntryValidator
import com.naseeb.salestracker.data.store.FileStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class FileSalesRepository @Inject constructor(
    private val fileStore: FileStore,
    private val settingsRepository: SettingsRepository? = null,
    @Named("cpu") private val cpuDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default
) : SalesRepository {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    private fun getKnownShops(): List<String> {
        val baseShops = settingsRepository?.settings?.value?.shopList ?: com.naseeb.salestracker.data.model.Shops.ALL
        val repoShops = _entries.value.map { it.shop }
        return (baseShops + repoShops).filter { it.isNotBlank() }.distinct()
    }

    private sealed interface RepoState {
        data object Unloaded : RepoState
        data object Loaded : RepoState
        data class LoadFailed(val cause: Exception) : RepoState
    }

    private var state: RepoState = RepoState.Unloaded

    private val _entries = MutableStateFlow<List<SalesEntry>>(emptyList())
    override val entries: StateFlow<List<SalesEntry>> = _entries.asStateFlow()

    private val _loadReport = MutableStateFlow<LoadReport?>(null)
    override val loadReport: StateFlow<LoadReport?> = _loadReport.asStateFlow()

    private val _loadError = MutableStateFlow<Exception?>(null)
    override val loadError: StateFlow<Exception?> = _loadError.asStateFlow()

    private var needsBackup = false
    private var rawFileText: String? = null

    override suspend fun load() = mutex.withLock {
        loadInternal()
    }

    private suspend fun loadInternal() {
        val text = try {
            fileStore.read()
        } catch (e: Exception) {
            state = RepoState.LoadFailed(e)
            _loadError.value = e
            _entries.value = emptyList()
            _loadReport.value = null
            return
        }

        state = RepoState.Loaded
        _loadError.value = null

        if (text == null) {
            needsBackup = false
            rawFileText = null
            _entries.value = emptyList()
            _loadReport.value = LoadReport(0, 0)
            return
        }

        rawFileText = text
        val trimmed = text.trim()
        val loadedEntries = mutableListOf<SalesEntry>()
        var skipped = 0

        try {
            settingsRepository?.load()
        } catch (_: Exception) {}

        withContext(cpuDispatcher) {
            val knownShops = getKnownShops()
            fun isValid(entry: SalesEntry): Boolean {
                val entryKnown = if (entry.shop.isNotBlank()) (knownShops + entry.shop).distinct() else knownShops
                return SalesEntryValidator.validate(entry, entryKnown).isEmpty()
            }

            if (trimmed.startsWith("[")) {
                try {
                    val element = json.parseToJsonElement(trimmed)
                    if (element is JsonArray) {
                        for (item in element) {
                            try {
                                val entry = json.decodeFromJsonElement<SalesEntry>(item)
                                if (isValid(entry)) loadedEntries.add(entry) else skipped++
                            } catch (_: Exception) {
                                skipped++
                            }
                        }
                    } else {
                        skipped++
                    }
                } catch (_: Exception) {
                    val lines = text.lines()
                    for (line in lines) {
                        if (line.isBlank()) continue
                        try {
                            val entry = json.decodeFromString<SalesEntry>(line)
                            if (isValid(entry)) loadedEntries.add(entry) else skipped++
                        } catch (_: Exception) {
                            skipped++
                        }
                    }
                }
            } else {
                for (line in text.lines()) {
                    if (line.isBlank()) continue
                    try {
                        val entry = json.decodeFromString<SalesEntry>(line)
                        if (isValid(entry)) loadedEntries.add(entry) else skipped++
                    } catch (_: Exception) {
                        skipped++
                    }
                }
            }

            needsBackup = skipped > 0
            _entries.value = sortEntries(loadedEntries)
            _loadReport.value = LoadReport(loadedEntries.size, skipped)
        }
    }

    private suspend fun ensureLoaded(): RepoState {
        if (state is RepoState.Unloaded) {
            loadInternal()
        }
        return state
    }

    private suspend fun ensureBackup() {
        if (needsBackup) {
            val text = rawFileText ?: return
            val name = "sales_data.corrupt-${System.currentTimeMillis()}.json"
            fileStore.writeBackup(name, text)
            needsBackup = false
            rawFileText = null
        }
    }

    private fun sortEntries(list: List<SalesEntry>): List<SalesEntry> {
        return list.sortedWith(compareByDescending<SalesEntry> { it.date }.thenBy { it.shop })
    }

    private suspend fun writeToStore(newList: List<SalesEntry>) {
        ensureBackup()
        val text = withContext(cpuDispatcher) {
            newList.joinToString("\n") { json.encodeToString(it) } + if (newList.isNotEmpty()) "\n" else ""
        }
        fileStore.write(text)
    }

    override suspend fun add(entry: SalesEntry): SaveResult = mutex.withLock {
        val currentState = ensureLoaded()
        if (currentState is RepoState.LoadFailed) {
            return SaveResult.IoFailure(currentState.cause)
        }

        val errors = SalesEntryValidator.validate(entry, getKnownShops())
        if (errors.isNotEmpty()) return SaveResult.Invalid(errors)

        val currentList = _entries.value
        if (currentList.any { it.id == entry.id }) {
            return SaveResult.Duplicate(entry.id)
        }

        val duplicate = currentList.find { it.date == entry.date && it.shop.equals(entry.shop, ignoreCase = true) }
        if (duplicate != null) return SaveResult.Duplicate(duplicate.id)

        val newList = sortEntries(currentList + entry)
        try {
            writeToStore(newList)
            _entries.value = newList
            return SaveResult.Saved(entry)
        } catch (e: Exception) {
            return SaveResult.IoFailure(e)
        }
    }

    override suspend fun update(entry: SalesEntry): SaveResult = mutex.withLock {
        val currentState = ensureLoaded()
        if (currentState is RepoState.LoadFailed) {
            return SaveResult.IoFailure(currentState.cause)
        }

        val errors = SalesEntryValidator.validate(entry, getKnownShops())
        if (errors.isNotEmpty()) return SaveResult.Invalid(errors)

        val currentList = _entries.value
        if (currentList.none { it.id == entry.id }) return SaveResult.NotFound

        val duplicate = currentList.find { it.date == entry.date && it.shop.equals(entry.shop, ignoreCase = true) && it.id != entry.id }
        if (duplicate != null) return SaveResult.Duplicate(duplicate.id)

        val newList = sortEntries(currentList.map { if (it.id == entry.id) entry else it })
        try {
            writeToStore(newList)
            _entries.value = newList
            return SaveResult.Saved(entry)
        } catch (e: Exception) {
            return SaveResult.IoFailure(e)
        }
    }

    override suspend fun delete(id: String): DeleteResult = mutex.withLock {
        val currentState = ensureLoaded()
        if (currentState is RepoState.LoadFailed) {
            return DeleteResult.IoFailure(currentState.cause)
        }

        val currentList = _entries.value
        if (currentList.none { it.id == id }) return DeleteResult.NotFound

        val newList = currentList.filter { it.id != id }
        try {
            writeToStore(newList)
            _entries.value = newList
            return DeleteResult.Deleted
        } catch (e: Exception) {
            return DeleteResult.IoFailure(e)
        }
    }

    override suspend fun wipe(): WipeResult = mutex.withLock {
        val currentState = ensureLoaded()
        if (currentState is RepoState.LoadFailed) {
            return WipeResult.IoFailure(currentState.cause)
        }

        val count = _entries.value.size
        try {
            writeToStore(emptyList())
            _entries.value = emptyList()
            return WipeResult.Wiped(count)
        } catch (e: Exception) {
            return WipeResult.IoFailure(e)
        }
    }

    override suspend fun import(entries: List<SalesEntry>): ImportResult = mutex.withLock {
        val currentState = ensureLoaded()
        if (currentState is RepoState.LoadFailed) {
            return ImportResult.IoFailure(currentState.cause)
        }

        var imported = 0
        var skippedDuplicates = 0
        var skippedInvalid = 0

        val currentMap = _entries.value.associateBy { it.id }.toMutableMap()
        val existingDateShopKeys = _entries.value.map { "${it.date}|${it.shop.trim().lowercase()}" }.toMutableSet()
        val knownShops = getKnownShops()

        for (entry in entries) {
            val entryKnown = if (entry.shop.isNotBlank()) (knownShops + entry.shop).distinct() else knownShops
            if (SalesEntryValidator.validate(entry, entryKnown).isNotEmpty()) {
                skippedInvalid++
                continue
            }

            if (currentMap.containsKey(entry.id)) {
                skippedDuplicates++
                continue
            }

            val key = "${entry.date}|${entry.shop.trim().lowercase()}"
            if (existingDateShopKeys.contains(key)) {
                skippedDuplicates++
                continue
            }

            existingDateShopKeys.add(key)
            currentMap[entry.id] = entry
            imported++
        }

        if (imported == 0) {
            return ImportResult.Imported(imported, skippedDuplicates, skippedInvalid)
        }

        val newList = sortEntries(currentMap.values.toList())
        try {
            writeToStore(newList)
            _entries.value = newList
            return ImportResult.Imported(imported, skippedDuplicates, skippedInvalid)
        } catch (e: Exception) {
            return ImportResult.IoFailure(e)
        }
    }

    override suspend fun renameShop(oldName: String, newName: String): Boolean = mutex.withLock {
        val currentState = ensureLoaded()
        if (currentState is RepoState.LoadFailed) return false
        val currentList = _entries.value
        val updatedList = currentList.map { entry ->
            if (entry.shop.equals(oldName, ignoreCase = true)) {
                entry.copy(shop = newName)
            } else {
                entry
            }
        }
        return try {
            writeToStore(updatedList)
            _entries.value = updatedList
            true
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun renameFinanceKey(oldKey: String, newKey: String): Boolean = mutex.withLock {
        val currentState = ensureLoaded()
        if (currentState is RepoState.LoadFailed) return false
        val currentList = _entries.value
        val updatedList = currentList.map { entry ->
            val resolved = entry.getResolvedFinances().toMutableMap()
            val matchedKey = resolved.keys.firstOrNull { it.equals(oldKey, ignoreCase = true) }
            if (matchedKey != null) {
                val value = resolved.remove(matchedKey) ?: 0
                resolved[newKey] = value
            }
            entry.copy(
                financeValues = resolved,
                bajaj = null, idfc = null, hdb = null, tvs = null, poonwala = null, others = null
            )
        }
        return try {
            writeToStore(updatedList)
            _entries.value = updatedList
            true
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun deleteFinanceKey(key: String): Boolean = mutex.withLock {
        val currentState = ensureLoaded()
        if (currentState is RepoState.LoadFailed) return false
        val currentList = _entries.value
        val updatedList = currentList.map { entry ->
            val resolved = entry.getResolvedFinances().toMutableMap()
            val matchedKey = resolved.keys.firstOrNull { it.equals(key, ignoreCase = true) }
            if (matchedKey != null) {
                resolved.remove(matchedKey)
            }
            entry.copy(
                financeValues = resolved,
                bajaj = null, idfc = null, hdb = null, tvs = null, poonwala = null, others = null
            )
        }
        return try {
            writeToStore(updatedList)
            _entries.value = updatedList
            true
        } catch (_: Exception) {
            false
        }
    }
}

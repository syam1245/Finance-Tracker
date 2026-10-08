package com.naseeb.salestracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naseeb.salestracker.data.csv.CsvCodec
import com.naseeb.salestracker.data.model.AppSettings
import com.naseeb.salestracker.data.model.SalesEntry
import com.naseeb.salestracker.data.model.SalesEntryValidator
import com.naseeb.salestracker.data.repo.DeleteResult
import com.naseeb.salestracker.data.repo.ImportResult
import com.naseeb.salestracker.data.repo.SalesRepository
import com.naseeb.salestracker.data.repo.SaveResult
import com.naseeb.salestracker.data.repo.SettingsRepository
import com.naseeb.salestracker.data.repo.WipeResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named

enum class ToastType { SUCCESS, WARNING, DANGER }

sealed interface UiEvent {
    data class ShowToast(val message: String, val type: ToastType = ToastType.SUCCESS) : UiEvent
    data class SwitchTab(val tabIndex: Int) : UiEvent
    data class ExportCsv(
        val entries: List<SalesEntry>,
        val financeOptions: List<String>,
        val filename: String
    ) : UiEvent
    data object ScrollToTop : UiEvent
}

data class FormState(
    val id: String? = null,
    val date: String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
    val shop: String = "",
    val cd: String = "",
    val mobile: String = "",
    val financeMap: Map<String, String> = emptyMap()
) {
    val totalCount: Int
        get() = (cd.toIntOrNull() ?: 0) + (mobile.toIntOrNull() ?: 0)

    val totalFinance: Int
        get() = financeMap.values.sumOf { it.toIntOrNull() ?: 0 }

    val isEditing: Boolean
        get() = id != null
}

private data class FilterResult(
    val entries: List<SalesEntry>,
    val allEntries: List<SalesEntry>,
    val shopFilter: String,
    val startFilter: String?,
    val endFilter: String?
)

data class SalesUiState(
    val entries: List<SalesEntry> = emptyList(),
    val allEntries: List<SalesEntry> = emptyList(),
    val selectedShopFilter: String = "ALL",
    val startDateFilter: String? = null,
    val endDateFilter: String? = null,
    val formState: FormState = FormState(),
    val activeTab: Int = 0, // 0 = Add Entry, 1 = View Data, 2 = Settings
    val shopList: List<String> = AppSettings.DEFAULT_SHOPS,
    val financeOptions: List<String> = AppSettings.DEFAULT_FINANCE_OPTIONS
) {
    val dateRangeDisplayText: String
        get() {
            if (startDateFilter != null && endDateFilter != null) {
                return "${formatDateForDisplay(startDateFilter)} - ${formatDateForDisplay(endDateFilter)}"
            } else if (startDateFilter != null) {
                return "From ${formatDateForDisplay(startDateFilter)}"
            } else if (endDateFilter != null) {
                return "Until ${formatDateForDisplay(endDateFilter)}"
            }
            return ""
        }

    private fun formatDateForDisplay(isoDate: String): String {
        return try {
            val date = LocalDate.parse(isoDate, DateTimeFormatter.ISO_LOCAL_DATE)
            date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        } catch (_: Exception) {
            isoDate
        }
    }
}

@HiltViewModel
class SalesViewModel @Inject constructor(
    private val repository: SalesRepository,
    private val settingsRepository: SettingsRepository,
    @Named("cpu") private val cpuDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _selectedShopFilter = MutableStateFlow("ALL")
    private val _dateRangeFilter = MutableStateFlow<Pair<String?, String?>>(null to null)
    private val _formState = MutableStateFlow(FormState())
    private val _activeTab = MutableStateFlow(0)

    private val _uiState = MutableStateFlow(SalesUiState())
    val uiState: StateFlow<SalesUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>()
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            settingsRepository.load()
            repository.load()
        }

        val filteredDataFlow = combine(
            repository.entries,
            _selectedShopFilter,
            _dateRangeFilter
        ) { allEntries, shopFilter, dateRange ->
            val (startFilter, endFilter) = dateRange

            val shopFiltered = if (shopFilter == "ALL") {
                allEntries
            } else {
                allEntries.filter { it.shop == shopFilter }
            }

            val startDate = startFilter?.let {
                try { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) } catch (_: Exception) { null }
            }
            val endDate = endFilter?.let {
                try { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) } catch (_: Exception) { null }
            }

            val dateFiltered = if (startDate == null && endDate == null) {
                shopFiltered
            } else {
                shopFiltered.filter { entry ->
                    val entryDate = try {
                        LocalDate.parse(entry.date, DateTimeFormatter.ISO_LOCAL_DATE)
                    } catch (_: Exception) {
                        null
                    }
                    if (entryDate == null) {
                        false
                    } else {
                        (startDate == null || !entryDate.isBefore(startDate)) &&
                                (endDate == null || !entryDate.isAfter(endDate))
                    }
                }
            }

            FilterResult(dateFiltered, allEntries, shopFilter, startFilter, endFilter)
        }

        viewModelScope.launch {
            combine(
                filteredDataFlow,
                _formState,
                _activeTab,
                settingsRepository.settings
            ) { filterRes, form, tab, currentSettings ->
                SalesUiState(
                    entries = filterRes.entries,
                    allEntries = filterRes.allEntries,
                    selectedShopFilter = filterRes.shopFilter,
                    startDateFilter = filterRes.startFilter,
                    endDateFilter = filterRes.endFilter,
                    formState = form,
                    activeTab = tab,
                    shopList = currentSettings.shopList,
                    financeOptions = currentSettings.financeOptions
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun selectTab(index: Int) {
        _activeTab.value = index
        viewModelScope.launch {
            _events.emit(UiEvent.SwitchTab(index))
        }
    }

    fun updateDate(date: String) {
        _formState.update { it.copy(date = date) }
    }

    fun updateShop(shop: String) {
        _formState.update { it.copy(shop = shop) }
    }

    fun updateCd(value: String) {
        _formState.update { it.copy(cd = value) }
    }

    fun updateMobile(value: String) {
        _formState.update { it.copy(mobile = value) }
    }

    fun updateFinance(option: String, value: String) {
        _formState.update { current ->
            val updatedMap = current.financeMap.toMutableMap()
            updatedMap[option] = value
            current.copy(financeMap = updatedMap)
        }
    }

    fun updateBajaj(value: String) = updateFinance("Bajaj", value)
    fun updateIdfc(value: String) = updateFinance("IDFC", value)
    fun updateHdb(value: String) = updateFinance("HDB", value)
    fun updateTvs(value: String) = updateFinance("TVS", value)
    fun updatePoonwala(value: String) = updateFinance("Poonwala", value)
    fun updateOthers(value: String) = updateFinance("Others", value)

    fun setShopFilter(filter: String) {
        _selectedShopFilter.value = filter
    }

    fun setDateRangeFilter(startDate: String?, endDate: String?) {
        _dateRangeFilter.value = startDate to endDate
    }

    fun clearDateRangeFilter() {
        _dateRangeFilter.value = null to null
    }

    private fun getCleanInt(valStr: String?): Int {
        if (valStr.isNullOrBlank()) return 0
        val parsed = valStr.toIntOrNull()
        return if (parsed == null || parsed < 0) 0 else parsed
    }

    fun submitEntry() {
        viewModelScope.launch {
            val form = _formState.value
            if (form.date.isBlank() || form.shop.isBlank()) {
                _events.emit(UiEvent.ShowToast("Date and Shop are required fields.", ToastType.WARNING))
                return@launch
            }

            val currentFinances = _uiState.value.financeOptions
            val financeIntMap = mutableMapOf<String, Int>()
            for (opt in currentFinances) {
                val strVal = form.financeMap[opt]
                financeIntMap[opt] = getCleanInt(strVal)
            }

            val entry = SalesEntry(
                id = form.id ?: UUID.randomUUID().toString(),
                date = form.date.trim(),
                shop = form.shop.trim(),
                cd = getCleanInt(form.cd),
                mobile = getCleanInt(form.mobile),
                financeValues = financeIntMap
            )

            val allowedShops = (_uiState.value.shopList + repository.entries.value.map { it.shop })
                .filter { it.isNotBlank() }
                .distinct()
            val validationErrors = SalesEntryValidator.validate(
                entry,
                if (allowedShops.isNotEmpty()) allowedShops else AppSettings.DEFAULT_SHOPS
            )
            if (validationErrors.isNotEmpty()) {
                _events.emit(UiEvent.ShowToast("Invalid entry data.", ToastType.WARNING))
                return@launch
            }

            if (form.isEditing) {
                when (withContext(cpuDispatcher) { repository.update(entry) }) {
                    is SaveResult.Saved -> {
                        cancelEditInternal()
                        _events.emit(UiEvent.ShowToast("Record updated successfully!", ToastType.SUCCESS))
                        _events.emit(UiEvent.ScrollToTop)
                    }
                    is SaveResult.Duplicate -> {
                        _events.emit(UiEvent.ShowToast("An entry for ${entry.shop} on this date already exists. Edit the existing record instead.", ToastType.DANGER))
                    }
                    is SaveResult.Invalid -> {
                        _events.emit(UiEvent.ShowToast("Invalid entry data.", ToastType.WARNING))
                    }
                    else -> {
                        _events.emit(UiEvent.ShowToast("Failed to update record.", ToastType.DANGER))
                    }
                }
            } else {
                when (withContext(cpuDispatcher) { repository.add(entry) }) {
                    is SaveResult.Saved -> {
                        resetFormFieldsInternal()
                        _events.emit(UiEvent.ShowToast("Data Saved Successfully", ToastType.SUCCESS))
                        _events.emit(UiEvent.ScrollToTop)
                    }
                    is SaveResult.Duplicate -> {
                        _events.emit(UiEvent.ShowToast("An entry for ${entry.shop} on this date already exists. Edit the existing record instead.", ToastType.DANGER))
                    }
                    is SaveResult.Invalid -> {
                        _events.emit(UiEvent.ShowToast("Invalid entry data.", ToastType.WARNING))
                    }
                    else -> {
                        _events.emit(UiEvent.ShowToast("Failed to commit record.", ToastType.DANGER))
                    }
                }
            }
        }
    }

    fun editEntry(entry: SalesEntry) {
        val resolvedFinances = entry.getResolvedFinances()
        val formFinances = resolvedFinances.mapValues {
            if (it.value != 0) it.value.toString() else ""
        }

        _formState.value = FormState(
            id = entry.id,
            date = entry.date,
            shop = entry.shop,
            cd = if (entry.cd != 0) entry.cd.toString() else "",
            mobile = if (entry.mobile != 0) entry.mobile.toString() else "",
            financeMap = formFinances
        )
        _activeTab.value = 0
        viewModelScope.launch {
            _events.emit(UiEvent.ShowToast("Record loaded into editor.", ToastType.SUCCESS))
        }
    }

    fun cancelEdit() {
        cancelEditInternal()
    }

    private fun cancelEditInternal() {
        resetFormFieldsInternal()
    }

    private fun resetFormFieldsInternal() {
        _formState.value = FormState(
            date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        )
    }

    fun deleteEntry(id: String) {
        viewModelScope.launch {
            when (withContext(cpuDispatcher) { repository.delete(id) }) {
                is DeleteResult.Deleted -> {
                    if (_formState.value.id == id) {
                        cancelEditInternal()
                    }
                    _events.emit(UiEvent.ShowToast("Record deleted.", ToastType.SUCCESS))
                }
                else -> {
                    _events.emit(UiEvent.ShowToast("Storage Error.", ToastType.DANGER))
                }
            }
        }
    }

    fun exportCsv() {
        viewModelScope.launch {
            val currentEntries = _uiState.value.entries
            if (currentEntries.isEmpty()) {
                _events.emit(UiEvent.ShowToast("No data available to export.", ToastType.DANGER))
                return@launch
            }
            val shopFilter = _selectedShopFilter.value
            val financeOptions = _uiState.value.financeOptions
            val dateStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val shopPart = if (shopFilter == "ALL") "ALL_SHOPS" else shopFilter.replace("\\s+".toRegex(), "_")
            val filename = "DAILY_SALE_DATA_${shopPart}_$dateStr.csv"
            _events.emit(UiEvent.ExportCsv(currentEntries, financeOptions, filename))
        }
    }

    fun importCsvText(csvText: String) {
        viewModelScope.launch {
            val knownShops = (_uiState.value.shopList + repository.entries.value.map { it.shop }).filter { it.isNotBlank() }.distinct()
            val decodeResult = withContext(Dispatchers.Default) {
                CsvCodec.decode(csvText, knownShops, _uiState.value.financeOptions)
            }
            handleCsvDecodeResult(decodeResult)
        }
    }

    fun importCsvResult(decodeResult: CsvCodec.CsvDecodeResult) {
        viewModelScope.launch { handleCsvDecodeResult(decodeResult) }
    }

    private suspend fun handleCsvDecodeResult(decodeResult: CsvCodec.CsvDecodeResult) {
        when (decodeResult) {
                is CsvCodec.CsvDecodeResult.InvalidHeader -> {
                    _events.emit(UiEvent.ShowToast("Invalid CSV format or headers.", ToastType.DANGER))
                }
                is CsvCodec.CsvDecodeResult.Success -> {
                    val entriesToImport = decodeResult.entries
                    if (entriesToImport.isEmpty()) {
                        _events.emit(UiEvent.ShowToast("No valid records found in CSV.", ToastType.WARNING))
                        return
                    }
            when (val importRes = withContext(cpuDispatcher) { repository.import(entriesToImport) }) {
                        is ImportResult.Imported -> {
                            val msg = "Imported ${importRes.imported} records (${importRes.skippedDuplicates} duplicates, ${importRes.skippedInvalid} invalid skipped)."
                            _events.emit(UiEvent.ShowToast(msg, ToastType.SUCCESS))
                        }
                        is ImportResult.IoFailure -> {
                            _events.emit(UiEvent.ShowToast("Failed to save imported records.", ToastType.DANGER))
                        }
                    }
                }
            }
    }

    fun wipeRecords() {
        viewModelScope.launch {
            when (withContext(cpuDispatcher) { repository.wipe() }) {
                is WipeResult.Wiped -> {
                    cancelEditInternal()
                    _events.emit(UiEvent.ShowToast("All records have been completely wiped.", ToastType.DANGER))
                }
                is WipeResult.IoFailure -> {
                    _events.emit(UiEvent.ShowToast("Failed to wipe records.", ToastType.DANGER))
                }
            }
        }
    }

    // --- Customization Settings Actions ---

    fun addShop(shopName: String) {
        viewModelScope.launch {
            val trimmed = shopName.trim().uppercase()
            if (trimmed.isBlank()) return@launch
            if (settingsRepository.addShop(trimmed)) {
                _events.emit(UiEvent.ShowToast("Shop added successfully!", ToastType.SUCCESS))
            } else {
                _events.emit(UiEvent.ShowToast("Shop name already exists or is invalid.", ToastType.WARNING))
            }
        }
    }

    fun removeShop(shopName: String) {
        viewModelScope.launch {
            if (_uiState.value.shopList.size <= 1) {
                _events.emit(UiEvent.ShowToast("At least one shop must remain.", ToastType.WARNING))
                return@launch
            }
            if (settingsRepository.removeShop(shopName)) {
                if (_selectedShopFilter.value.equals(shopName, ignoreCase = true)) {
                    _selectedShopFilter.value = "ALL"
                }
                if (_formState.value.shop.equals(shopName, ignoreCase = true)) {
                    _formState.update { it.copy(shop = "") }
                }
                _events.emit(UiEvent.ShowToast("Shop removed from list.", ToastType.SUCCESS))
            }
        }
    }

    fun renameShop(oldName: String, newName: String) {
        viewModelScope.launch {
            val trimmedNew = newName.trim().uppercase()
            if (trimmedNew.isBlank() || trimmedNew.equals(oldName, ignoreCase = true)) return@launch
            val originalSettings = settingsRepository.settings.value
            if (settingsRepository.renameShop(oldName, trimmedNew)) {
                val salesOk = withContext(cpuDispatcher) { repository.renameShop(oldName, trimmedNew) }
                if (!salesOk) {
                    val rollbackOk = settingsRepository.save(originalSettings)
                    val message = if (rollbackOk) {
                        "Failed to update sales records for renamed shop. Settings were restored."
                    } else {
                        "Shop rename failed and settings could not be restored. Reopen settings and verify the shop list."
                    }
                    _events.emit(UiEvent.ShowToast(message, ToastType.DANGER))
                    return@launch
                }
                if (_selectedShopFilter.value.equals(oldName, ignoreCase = true)) {
                    _selectedShopFilter.value = trimmedNew
                }
                if (_formState.value.shop.equals(oldName, ignoreCase = true)) {
                    _formState.update { it.copy(shop = trimmedNew) }
                }
                _events.emit(UiEvent.ShowToast("Shop renamed successfully!", ToastType.SUCCESS))
            } else {
                _events.emit(UiEvent.ShowToast("Shop name already exists or failed to save.", ToastType.WARNING))
            }
        }
    }

    fun addFinanceOption(optionName: String) {
        viewModelScope.launch {
            val trimmed = optionName.trim()
            if (trimmed.isBlank()) return@launch
            if (settingsRepository.addFinanceOption(trimmed)) {
                _events.emit(UiEvent.ShowToast("Finance option added!", ToastType.SUCCESS))
            } else {
                _events.emit(UiEvent.ShowToast("Finance option already exists or failed to save.", ToastType.WARNING))
            }
        }
    }

    fun removeFinanceOption(optionName: String) {
        viewModelScope.launch {
            if (_uiState.value.financeOptions.size <= 1) {
                _events.emit(UiEvent.ShowToast("At least one finance option must remain.", ToastType.WARNING))
                return@launch
            }
            val originalSettings = settingsRepository.settings.value
            if (settingsRepository.removeFinanceOption(optionName)) {
                val salesOk = withContext(cpuDispatcher) { repository.deleteFinanceKey(optionName) }
                if (!salesOk) {
                    val rollbackOk = settingsRepository.save(originalSettings)
                    val message = if (rollbackOk) {
                        "Failed to delete finance key from sales records. Settings were restored."
                    } else {
                        "Finance removal failed and settings could not be restored. Reopen settings and verify the finance options."
                    }
                    _events.emit(UiEvent.ShowToast(message, ToastType.DANGER))
                    return@launch
                }
                _formState.update { form ->
                    val updatedMap = form.financeMap.toMutableMap()
                    updatedMap.remove(optionName)
                    form.copy(financeMap = updatedMap)
                }
                _events.emit(UiEvent.ShowToast("Finance option removed.", ToastType.SUCCESS))
            } else {
                _events.emit(UiEvent.ShowToast("Failed to remove finance option.", ToastType.DANGER))
            }
        }
    }

    fun renameFinanceOption(oldName: String, newName: String) {
        viewModelScope.launch {
            val trimmedNew = newName.trim()
            if (trimmedNew.isBlank() || trimmedNew == oldName) return@launch
            val originalSettings = settingsRepository.settings.value
            if (settingsRepository.renameFinanceOption(oldName, trimmedNew)) {
                val salesOk = withContext(cpuDispatcher) { repository.renameFinanceKey(oldName, trimmedNew) }
                if (!salesOk) {
                    val rollbackOk = settingsRepository.save(originalSettings)
                    val message = if (rollbackOk) {
                        "Failed to update sales records for renamed finance key. Settings were restored."
                    } else {
                        "Finance rename failed and settings could not be restored. Reopen settings and verify the finance options."
                    }
                    _events.emit(UiEvent.ShowToast(message, ToastType.DANGER))
                    return@launch
                }
                _formState.update { form ->
                    val updatedMap = form.financeMap.toMutableMap()
                    val existingVal = updatedMap.remove(oldName)
                    if (existingVal != null) {
                        updatedMap[trimmedNew] = existingVal
                    }
                    form.copy(financeMap = updatedMap)
                }
                _events.emit(UiEvent.ShowToast("Finance option renamed successfully!", ToastType.SUCCESS))
            } else {
                _events.emit(UiEvent.ShowToast("Finance option already exists or failed to save.", ToastType.WARNING))
            }
        }
    }

    fun resetSettingsToDefaults() {
        viewModelScope.launch {
            if (settingsRepository.resetToDefaults()) {
                _events.emit(UiEvent.ShowToast("Settings restored to defaults.", ToastType.SUCCESS))
            } else {
                _events.emit(UiEvent.ShowToast("Failed to reset settings.", ToastType.DANGER))
            }
        }
    }
}

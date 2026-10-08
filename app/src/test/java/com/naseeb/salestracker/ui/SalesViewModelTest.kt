package com.naseeb.salestracker.ui

import com.naseeb.salestracker.data.FakeFileStore
import com.naseeb.salestracker.data.repo.FileSalesRepository
import com.naseeb.salestracker.data.model.SalesEntry
import com.naseeb.salestracker.data.model.Shops
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

import com.naseeb.salestracker.data.repo.FileSettingsRepository

@OptIn(ExperimentalCoroutinesApi::class)
class SalesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var store: FakeFileStore
    private lateinit var settingsStore: FakeFileStore
    private lateinit var repository: FileSalesRepository
    private lateinit var settingsRepository: FileSettingsRepository
    private lateinit var viewModel: SalesViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        store = FakeFileStore()
        settingsStore = FakeFileStore()
        settingsRepository = FileSettingsRepository(settingsStore)
        repository = FileSalesRepository(store, settingsRepository, Dispatchers.Main)
        viewModel = SalesViewModel(repository, settingsRepository, Dispatchers.Main)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `form calculations update correctly`() = runTest {
        viewModel.updateCd("5")
        viewModel.updateMobile("10")
        advanceUntilIdle()

        val form = viewModel.uiState.value.formState
        assertEquals(15, form.totalCount)

        viewModel.updateBajaj("100")
        viewModel.updateIdfc("200")
        viewModel.updateHdb("300")
        viewModel.updateTvs("400")
        viewModel.updatePoonwala("500")
        viewModel.updateOthers("600")
        advanceUntilIdle()

        val updatedForm = viewModel.uiState.value.formState
        assertEquals(2100, updatedForm.totalFinance)
    }

    @Test
    fun `submitting entry adds to repository`() = runTest {
        val shop = Shops.ALL[0]
        viewModel.updateDate("2026-10-06")
        viewModel.updateShop(shop)
        viewModel.updateCd("3")
        viewModel.updateMobile("2")
        viewModel.updateBajaj("1000")

        viewModel.submitEntry()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.entries.size)
        assertEquals(shop, state.entries[0].shop)
        assertEquals("2026-10-06", state.entries[0].date)
        assertEquals(3, state.entries[0].cd)
        assertEquals(2, state.entries[0].mobile)
        assertEquals(1000, state.entries[0].getFinanceValue("Bajaj"))
    }

    @Test
    fun `editing entry populates form and updates record`() = runTest {
        val shop = Shops.ALL[1]
        val entry = SalesEntry(id = "e1", date = "2026-10-06", shop = shop, cd = 5, mobile = 0, bajaj = 0, idfc = 0, hdb = 0, tvs = 0, poonwala = 0, others = 0)
        repository.add(entry)
        advanceUntilIdle()

        viewModel.editEntry(entry)
        advanceUntilIdle()

        val form = viewModel.uiState.value.formState
        assertTrue(form.isEditing)
        assertEquals("e1", form.id)
        assertEquals("5", form.cd)

        viewModel.updateCd("12")
        viewModel.submitEntry()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.entries.size)
        assertEquals(12, state.entries[0].cd)
        assertFalse(viewModel.uiState.value.formState.isEditing)
    }

    @Test
    fun `deleting entry removes record`() = runTest {
        val entry = SalesEntry(id = "e2", date = "2026-10-06", shop = Shops.ALL[0], cd = 1, mobile = 0, bajaj = 0, idfc = 0, hdb = 0, tvs = 0, poonwala = 0, others = 0)
        repository.add(entry)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.entries.size)

        viewModel.deleteEntry("e2")
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.entries.size)
    }

    @Test
    fun `shop filter filters displayed entries`() = runTest {
        val s1 = Shops.ALL[0]
        val s2 = Shops.ALL[1]
        val e1 = SalesEntry(id = "1", date = "2026-10-06", shop = s1, cd = 1, mobile = 0, bajaj = 0, idfc = 0, hdb = 0, tvs = 0, poonwala = 0, others = 0)
        val e2 = SalesEntry(id = "2", date = "2026-10-06", shop = s2, cd = 2, mobile = 0, bajaj = 0, idfc = 0, hdb = 0, tvs = 0, poonwala = 0, others = 0)
        repository.add(e1)
        repository.add(e2)
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.entries.size)

        viewModel.setShopFilter(s1)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.entries.size)
        assertEquals(s1, viewModel.uiState.value.entries[0].shop)
    }

    @Test
    fun `wiping records clears all entries`() = runTest {
        val e1 = SalesEntry(id = "1", date = "2026-10-06", shop = Shops.ALL[0], cd = 1, mobile = 0, bajaj = 0, idfc = 0, hdb = 0, tvs = 0, poonwala = 0, others = 0)
        repository.add(e1)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.entries.size)

        viewModel.wipeRecords()
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.entries.size)
    }

    @Test
    fun `date range filter filters displayed entries correctly`() = runTest {
        val shop = Shops.ALL[0]
        val e1 = SalesEntry(id = "1", date = "2026-07-01", shop = shop, cd = 1, mobile = 0, bajaj = 0, idfc = 0, hdb = 0, tvs = 0, poonwala = 0, others = 0)
        val e2 = SalesEntry(id = "2", date = "2026-07-15", shop = shop, cd = 2, mobile = 0, bajaj = 0, idfc = 0, hdb = 0, tvs = 0, poonwala = 0, others = 0)
        val e3 = SalesEntry(id = "3", date = "2026-07-30", shop = shop, cd = 3, mobile = 0, bajaj = 0, idfc = 0, hdb = 0, tvs = 0, poonwala = 0, others = 0)
        val e4 = SalesEntry(id = "4", date = "2026-08-01", shop = shop, cd = 4, mobile = 0, bajaj = 0, idfc = 0, hdb = 0, tvs = 0, poonwala = 0, others = 0)
        repository.add(e1)
        repository.add(e2)
        repository.add(e3)
        repository.add(e4)
        advanceUntilIdle()

        assertEquals(4, viewModel.uiState.value.entries.size)

        viewModel.setDateRangeFilter("2026-07-01", "2026-07-30")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(3, state.entries.size)
        assertEquals("01/07/2026 - 30/07/2026", state.dateRangeDisplayText)
        assertTrue(state.entries.any { it.id == "1" })
        assertTrue(state.entries.any { it.id == "2" })
        assertTrue(state.entries.any { it.id == "3" })
        assertFalse(state.entries.any { it.id == "4" })

        viewModel.clearDateRangeFilter()
        advanceUntilIdle()

        val clearedState = viewModel.uiState.value
        assertEquals(4, clearedState.entries.size)
        assertEquals("", clearedState.dateRangeDisplayText)
    }

    @Test
    fun `shop list management can add, rename, and remove shops`() = runTest {
        advanceUntilIdle()
        val initialCount = viewModel.uiState.value.shopList.size

        viewModel.addShop("NEW ALPHA STORE")
        advanceUntilIdle()
        assertEquals(initialCount + 1, viewModel.uiState.value.shopList.size)
        assertTrue(viewModel.uiState.value.shopList.contains("NEW ALPHA STORE"))

        viewModel.renameShop("NEW ALPHA STORE", "RENAMED ALPHA STORE")
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.shopList.contains("NEW ALPHA STORE"))
        assertTrue(viewModel.uiState.value.shopList.contains("RENAMED ALPHA STORE"))

        viewModel.removeShop("RENAMED ALPHA STORE")
        advanceUntilIdle()
        assertEquals(initialCount, viewModel.uiState.value.shopList.size)
        assertFalse(viewModel.uiState.value.shopList.contains("RENAMED ALPHA STORE"))
    }

    @Test
    fun `finance options management can add, rename, and remove options`() = runTest {
        advanceUntilIdle()
        val initialCount = viewModel.uiState.value.financeOptions.size

        viewModel.addFinanceOption("HDFC Finance")
        advanceUntilIdle()
        assertEquals(initialCount + 1, viewModel.uiState.value.financeOptions.size)
        assertTrue(viewModel.uiState.value.financeOptions.contains("HDFC Finance"))

        viewModel.renameFinanceOption("HDFC Finance", "Custom Finance")
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.financeOptions.contains("HDFC Finance"))
        assertTrue(viewModel.uiState.value.financeOptions.contains("Custom Finance"))

        viewModel.removeFinanceOption("Custom Finance")
        advanceUntilIdle()
        assertEquals(initialCount, viewModel.uiState.value.financeOptions.size)
        assertFalse(viewModel.uiState.value.financeOptions.contains("Custom Finance"))
    }

    @Test
    fun `finance removal restores exact settings order when sales write fails`() = runTest {
        advanceUntilIdle()
        val originalOptions = settingsRepository.settings.value.financeOptions
        store.shouldThrowOnWrite = true

        viewModel.removeFinanceOption("Bajaj")
        advanceUntilIdle()

        assertEquals(originalOptions, settingsRepository.settings.value.financeOptions)
    }

    @Test
    fun `creating entry with customized shop and modified finance option succeeds`() = runTest {
        advanceUntilIdle()
        viewModel.addShop("KOCHI HUB")
        viewModel.renameFinanceOption("Bajaj", "Bajaj Auto")
        advanceUntilIdle()

        viewModel.updateDate("2026-10-06")
        viewModel.updateShop("KOCHI HUB")
        viewModel.updateCd("3")
        viewModel.updateMobile("2")
        viewModel.updateFinance("Bajaj Auto", "500")
        advanceUntilIdle()

        viewModel.submitEntry()
        advanceUntilIdle()

        assertEquals(1, repository.entries.value.size)
        val saved = repository.entries.value[0]
        assertEquals("KOCHI HUB", saved.shop)
        assertEquals(5, saved.totalCount)
        assertEquals(500, saved.getFinanceValue("Bajaj Auto"))
    }
}

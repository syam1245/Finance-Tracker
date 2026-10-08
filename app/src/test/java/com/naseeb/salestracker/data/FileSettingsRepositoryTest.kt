package com.naseeb.salestracker.data

import com.naseeb.salestracker.data.model.AppSettings
import com.naseeb.salestracker.data.repo.FileSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FileSettingsRepositoryTest {

    @Test
    fun `rejects reserved finance option names on add and rename`() = runTest {
        val store = FakeFileStore()
        val repo = FileSettingsRepository(store)
        repo.load()

        assertFalse(repo.addFinanceOption("TOTAL FINANCE"))
        assertFalse(repo.addFinanceOption("total count"))
        assertFalse(repo.addFinanceOption("DATE"))
        assertFalse(repo.addFinanceOption("Shop Name"))

        assertTrue(repo.addFinanceOption("Custom Finance"))
        assertFalse(repo.renameFinanceOption("Custom Finance", "TOTAL FINANCE"))
    }

    @Test
    fun `failed store write does not publish new settings to memory`() = runTest {
        val failingStore = FakeFileStore().apply { shouldThrowOnWrite = true }
        val repo = FileSettingsRepository(failingStore)
        repo.load()

        val initialShops = repo.settings.value.shopList
        val success = repo.addShop("NEW SHOP")

        assertFalse(success)
        assertEquals(initialShops, repo.settings.value.shopList)
    }

    @Test
    fun `corrupt settings text triggers backup and falls back to defaults`() = runTest {
        val corruptStore = FakeFileStore().apply { fileContent = "{ corrupt invalid json..." }
        val repo = FileSettingsRepository(corruptStore)
        repo.load()

        assertEquals(1, corruptStore.backupCount)
        val backupKey = corruptStore.backups.keys.firstOrNull()
        assertNotNull(backupKey)
        assertTrue(backupKey!!.contains("app_settings.corrupt-"))
        assertEquals("{ corrupt invalid json...", corruptStore.backups[backupKey])
        assertEquals(AppSettings.DEFAULT_SHOPS, repo.settings.value.shopList)
    }

    @Test
    fun `read failure does not replace existing settings with defaults`() = runTest {
        val unreadableStore = FakeFileStore().apply {
            fileContent = "user-settings"
            shouldThrowOnRead = true
        }
        val repo = FileSettingsRepository(unreadableStore)
        val initialSettings = repo.settings.value

        repo.load()
        assertFalse(repo.addShop("NEW SHOP"))

        assertEquals(initialSettings, repo.settings.value)
        assertEquals("user-settings", unreadableStore.fileContent)
    }
}

package com.naseeb.salestracker.data

import com.naseeb.salestracker.data.model.SalesEntry
import com.naseeb.salestracker.data.model.SalesEntryValidator
import com.naseeb.salestracker.data.model.SalesEntryValidator.Code.*
import com.naseeb.salestracker.data.repo.FileSalesRepository
import com.naseeb.salestracker.data.repo.SaveResult
import com.naseeb.salestracker.data.repo.DeleteResult
import com.naseeb.salestracker.data.repo.WipeResult
import com.naseeb.salestracker.data.repo.ImportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import kotlinx.serialization.encodeToString
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FileSalesRepositoryTest {

    private lateinit var fileStore: FakeFileStore
    private lateinit var repo: FileSalesRepository

    @Before
    fun setup() {
        fileStore = FakeFileStore()
        repo = FileSalesRepository(fileStore)
    }

    @Test
    fun `T1 SalesEntry computed properties`() {
        val entry = SalesEntry(
            date = "2026-10-06",
            shop = "D WEB",
            cd = 2, mobile = 3,
            bajaj = 1, idfc = 0, hdb = 2, tvs = 0, poonwala = 0, others = 4
        )
        assertEquals(5, entry.totalCount)
        assertEquals(7, entry.totalFinance)
    }

    @Test
    fun `T2 valid entry passes validation`() {
        val entry = SalesEntry(
            date = "2026-10-06", shop = "D WEB",
            cd = 1, mobile = 0, bajaj = 0, idfc = 0, hdb = 0, tvs = 0, poonwala = 0, others = 0
        )
        assertTrue(SalesEntryValidator.validate(entry).isEmpty())
    }

    @Test
    fun `T3 negative count gives NEGATIVE_COUNT`() {
        val entry = SalesEntry(
            date = "2026-10-06", shop = "D WEB",
            cd = -1, mobile = 0, bajaj = 0, idfc = 0, hdb = 0, tvs = 0, poonwala = 0, others = 0
        )
        val errors = SalesEntryValidator.validate(entry)
        assertTrue(errors.any { it.code == NEGATIVE_COUNT })
    }

    @Test
    fun `T4 count 1_000_000 gives COUNT_TOO_LARGE`() {
        val entry = SalesEntry(
            date = "2026-10-06", shop = "D WEB",
            cd = 1_000_000, mobile = 0, bajaj = 0, idfc = 0, hdb = 0, tvs = 0, poonwala = 0, others = 0
        )
        val errors = SalesEntryValidator.validate(entry)
        assertTrue(errors.any { it.code == COUNT_TOO_LARGE })
    }

    @Test
    fun `T5 invalid dates give INVALID_DATE`() {
        val e1 = SalesEntry(date = "2026-13-40", shop = "D WEB", cd = 0, mobile = 0, bajaj = 0, idfc = 0, hdb = 0, tvs = 0, poonwala = 0, others = 0)
        val e2 = SalesEntry(date = "06-10-2026", shop = "D WEB", cd = 0, mobile = 0, bajaj = 0, idfc = 0, hdb = 0, tvs = 0, poonwala = 0, others = 0)
        assertTrue(SalesEntryValidator.validate(e1).any { it.code == INVALID_DATE })
        assertTrue(SalesEntryValidator.validate(e2).any { it.code == INVALID_DATE })
    }

    @Test
    fun `T6 unknown shop gives UNKNOWN_SHOP`() {
        val entry = SalesEntry(
            date = "2026-10-06", shop = "RANDOM SHOP",
            cd = 0, mobile = 0, bajaj = 0, idfc = 0, hdb = 0, tvs = 0, poonwala = 0, others = 0
        )
        assertTrue(SalesEntryValidator.validate(entry).any { it.code == UNKNOWN_SHOP })
    }

    @Test
    fun `T7 all-zero entry passes`() {
        val entry = SalesEntry(
            date = "2026-10-06", shop = "D WEB",
            cd = 0, mobile = 0, bajaj = 0, idfc = 0, hdb = 0, tvs = 0, poonwala = 0, others = 0
        )
        assertTrue(SalesEntryValidator.validate(entry).isEmpty())
    }

    @Test
    fun `T8 add valid entry returns Saved, entries size 1, store holds 1 line`() = runTest {
        repo.load()
        val entry = SalesEntry(date = "2026-10-06", shop = "D WEB", cd = 0, mobile = 0, bajaj = 0, idfc = 0, hdb = 0, tvs = 0, poonwala = 0, others = 0)
        val result = repo.add(entry)
        assertTrue(result is SaveResult.Saved)
        assertEquals(1, repo.entries.value.size)
        assertEquals(1, fileStore.fileContent?.trimEnd()?.lines()?.size)
    }

    @Test
    fun `T9 add same date and shop returns Duplicate`() = runTest {
        repo.load()
        val e1 = SalesEntry(date = "2026-10-06", shop = "D WEB", cd = 0, mobile = 0, bajaj = 0, idfc = 0, hdb = 0, tvs = 0, poonwala = 0, others = 0)
        val e2 = SalesEntry(date = "2026-10-06", shop = "D WEB", cd = 1, mobile = 1, bajaj = 0, idfc = 0, hdb = 0, tvs = 0, poonwala = 0, others = 0)
        repo.add(e1)
        val result = repo.add(e2)
        assertTrue(result is SaveResult.Duplicate)
        assertEquals(e1.id, (result as SaveResult.Duplicate).existingId)
        assertEquals(1, repo.entries.value.size)
    }

    @Test
    fun `T10 update moving a row onto another row's date and shop returns Duplicate`() = runTest {
        repo.load()
        val e1 = SalesEntry(date = "2026-10-06", shop = "D WEB", cd = 0, mobile = 0, bajaj = 0, idfc = 0, hdb = 0, tvs = 0, poonwala = 0, others = 0)
        val e2 = SalesEntry(date = "2026-10-07", shop = "AEON DIGITAL", cd = 1, mobile = 1, bajaj = 0, idfc = 0, hdb = 0, tvs = 0, poonwala = 0, others = 0)
        repo.add(e1)
        repo.add(e2)
        val updatedE2 = e2.copy(date = "2026-10-06", shop = "D WEB")
        val result = repo.update(updatedE2)
        assertTrue(result is SaveResult.Duplicate)
    }

    @Test
    fun `T11 update own row with new counts returns Saved`() = runTest {
        repo.load()
        val e1 = SalesEntry(date = "2026-10-06", shop = "D WEB", cd = 0, mobile = 0, bajaj = 0, idfc = 0, hdb = 0, tvs = 0, poonwala = 0, others = 0)
        repo.add(e1)
        val updatedE1 = e1.copy(cd = 5)
        val result = repo.update(updatedE1)
        assertTrue(result is SaveResult.Saved)
        assertEquals(5, repo.entries.value[0].cd)
    }

    @Test
    fun `T12 delete removes the row  delete of an unknown id returns NotFound`() = runTest {
        repo.load()
        val e1 = SalesEntry(date = "2026-10-06", shop = "D WEB", cd = 0, mobile = 0, bajaj = 0, idfc = 0, hdb = 0, tvs = 0, poonwala = 0, others = 0)
        repo.add(e1)
        val resultNotFound = repo.delete("wrong_id")
        assertEquals(DeleteResult.NotFound, resultNotFound)
        
        val resultFound = repo.delete(e1.id)
        assertEquals(DeleteResult.Deleted, resultFound)
        assertTrue(repo.entries.value.isEmpty())
    }

    @Test
    fun `T13 order is date descending, then shop ascending`() = runTest {
        repo.load()
        repo.add(SalesEntry(id = "1", date = "2026-10-06", shop = "D WEB", cd = 0, mobile = 0, bajaj = 0, idfc = 0, hdb = 0, tvs = 0, poonwala = 0, others = 0))
        repo.add(SalesEntry(id = "2", date = "2026-10-07", shop = "AEON DIGITAL", cd = 0, mobile = 0, bajaj = 0, idfc = 0, hdb = 0, tvs = 0, poonwala = 0, others = 0))
        repo.add(SalesEntry(id = "3", date = "2026-10-06", shop = "AEON DIGITAL", cd = 0, mobile = 0, bajaj = 0, idfc = 0, hdb = 0, tvs = 0, poonwala = 0, others = 0))

        val list = repo.entries.value
        assertEquals("2", list[0].id) // 2026-10-07
        assertEquals("3", list[1].id) // 2026-10-06, AEON DIGITAL
        assertEquals("1", list[2].id) // 2026-10-06, D WEB
    }

    @Test
    fun `T14 store throws on write add returns IoFailure and entries stay unchanged`() = runTest {
        repo.load()
        fileStore.shouldThrowOnWrite = true
        val e1 = SalesEntry(date = "2026-10-06", shop = "D WEB", cd = 0, mobile = 0, bajaj = 0, idfc = 0, hdb = 0, tvs = 0, poonwala = 0, others = 0)
        val result = repo.add(e1)
        assertTrue(result is SaveResult.IoFailure)
        assertTrue(repo.entries.value.isEmpty())
    }

    @Test
    fun `T15 file with lines valid, garbage, valid loads 2, skipped 1, backup written once`() = runTest {
        val validLine1 = """{"id":"1","date":"2026-10-06","shop":"D WEB","cd":0,"mobile":0,"bajaj":0,"idfc":0,"hdb":0,"tvs":0,"poonwala":0,"others":0}"""
        val garbageLine = """{garbage}"""
        val validLine2 = """{"id":"2","date":"2026-10-07","shop":"D WEB","cd":0,"mobile":0,"bajaj":0,"idfc":0,"hdb":0,"tvs":0,"poonwala":0,"others":0}"""
        
        fileStore.fileContent = "$validLine1\n$garbageLine\n$validLine2\n"
        repo.load()
        
        val report = repo.loadReport.value
        assertEquals(2, report?.loaded)
        assertEquals(1, report?.skipped)
        
        // Backup should be written exactly once before the first write
        val e3 = SalesEntry(date = "2026-10-08", shop = "D WEB", cd = 0, mobile = 0, bajaj = 0, idfc = 0, hdb = 0, tvs = 0, poonwala = 0, others = 0)
        repo.add(e3) // First write
        assertEquals(1, fileStore.backups.size)
        assertTrue(fileStore.backups.keys.first().startsWith("sales_data.corrupt-"))
        
        repo.add(SalesEntry(date = "2026-10-09", shop = "D WEB", cd = 0, mobile = 0, bajaj = 0, idfc = 0, hdb = 0, tvs = 0, poonwala = 0, others = 0)) // Second write
        assertEquals(1, fileStore.backups.size) // still 1 backup
    }

    @Test
    fun `T16 50 concurrent adds with distinct keys give 50 entries`() = runTest {
        repeat(20) {
            val store = FakeFileStore()
            val repository = FileSalesRepository(store)
            repository.load()
            val jobs = (1..50).map { i ->
                launch(Dispatchers.Default) {
                    val date = java.time.LocalDate.of(2026, 1, 1).plusDays(i.toLong()).toString()
                    val entry = SalesEntry(
                        date = date,
                        shop = "D WEB",
                        id = i.toString(),
                        cd = i, mobile = 0, bajaj = 0, idfc = 0, hdb = 0, tvs = 0, poonwala = 0, others = 0
                    )
                    repository.add(entry)
                }
            }
            jobs.forEach { it.join() }
            assertEquals(50, repository.entries.value.size)
        }
    }

    @Test
    fun `T17 50 concurrent adds with the same key give exactly 1 Saved and 49 Duplicate`() = runTest {
        repeat(20) {
            val store = FakeFileStore()
            val repository = FileSalesRepository(store)
            repository.load()
            val savedCount = java.util.concurrent.atomic.AtomicInteger(0)
            val duplicateCount = java.util.concurrent.atomic.AtomicInteger(0)
            val date = java.time.LocalDate.of(2026, 1, 1).toString()
            val jobs = (1..50).map { i ->
                launch(Dispatchers.Default) {
                    val entry = SalesEntry(
                        date = date,
                        shop = "D WEB",
                        id = i.toString(),
                        cd = 0, mobile = 0, bajaj = 0, idfc = 0, hdb = 0, tvs = 0, poonwala = 0, others = 0
                    )
                    when (repository.add(entry)) {
                        is SaveResult.Saved -> savedCount.incrementAndGet()
                        is SaveResult.Duplicate -> duplicateCount.incrementAndGet()
                        else -> {}
                    }
                }
            }
            jobs.forEach { it.join() }
            assertEquals(1, savedCount.get())
            assertEquals(49, duplicateCount.get())
            assertEquals(1, repository.entries.value.size)
        }
    }

    @Test
    fun `T18 wipe returns Wiped and empties entries and store`() = runTest {
        repo.load()
        repo.add(SalesEntry(date = "2026-10-06", shop = "D WEB", cd = 0, mobile = 0, bajaj = 0, idfc = 0, hdb = 0, tvs = 0, poonwala = 0, others = 0))
        val result = repo.wipe()
        assertTrue(result is WipeResult.Wiped)
        assertEquals(1, (result as WipeResult.Wiped).count)
        assertTrue(repo.entries.value.isEmpty())
        assertTrue(fileStore.fileContent.isNullOrBlank())
    }

    @Test
    fun `T27 FileStore read throws load leaves entries empty and sets loadError add returns IoFailure writeCount 0 backupCount 0`() = runTest {
        val store = FakeFileStore().apply { shouldThrowOnRead = true }
        val repository = FileSalesRepository(store)
        repository.load()
        assertEquals(emptyList<SalesEntry>(), repository.entries.value)
        assertNotNull(repository.loadError.value)
        val entry = SalesEntry(date = "2026-10-06", shop = "D WEB", cd = 1, mobile = 0, bajaj = 0, idfc = 0, hdb = 0, tvs = 0, poonwala = 0, others = 0)
        val res = repository.add(entry)
        assertTrue(res is SaveResult.IoFailure)
        assertEquals(0, store.writeCount)
        assertEquals(0, store.backupCount)
    }

    @Test
    fun `T28 File holds 2 valid lines and the test never calls load add returns Saved and store holds 3 lines`() = runTest {
        val e1 = SalesEntry(id = "1", date = "2026-10-06", shop = "D WEB", cd = 1, mobile = 0, bajaj = 0, idfc = 0, hdb = 0, tvs = 0, poonwala = 0, others = 0)
        val e2 = SalesEntry(id = "2", date = "2026-10-07", shop = "D WEB", cd = 2, mobile = 0, bajaj = 0, idfc = 0, hdb = 0, tvs = 0, poonwala = 0, others = 0)
        val json = kotlinx.serialization.json.Json
        val text = json.encodeToString(e1) + "\n" + json.encodeToString(e2) + "\n"
        val store = FakeFileStore().apply { fileContent = text }
        val repository = FileSalesRepository(store)
        val e3 = SalesEntry(id = "3", date = "2026-10-08", shop = "D WEB", cd = 3, mobile = 0, bajaj = 0, idfc = 0, hdb = 0, tvs = 0, poonwala = 0, others = 0)
        val res = repository.add(e3)
        assertTrue(res is SaveResult.Saved)
        assertEquals(3, repository.entries.value.size)
        val lines = store.fileContent?.lines()?.filter { it.isNotBlank() } ?: emptyList()
        assertEquals(3, lines.size)
    }

    @Test
    fun `T29 After T27 failure set shouldThrowOnRead false and call load loadError is null and add returns Saved`() = runTest {
        val store = FakeFileStore().apply { shouldThrowOnRead = true }
        val repository = FileSalesRepository(store)
        repository.load()
        assertNotNull(repository.loadError.value)

        store.shouldThrowOnRead = false
        repository.load()
        assertNull(repository.loadError.value)

        val entry = SalesEntry(id = "1", date = "2026-10-06", shop = "D WEB", cd = 1, mobile = 0, bajaj = 0, idfc = 0, hdb = 0, tvs = 0, poonwala = 0, others = 0)
        val res = repository.add(entry)
        assertTrue(res is SaveResult.Saved)
        assertEquals(1, repository.entries.value.size)
    }

    @Test
    fun `T30 add of entry whose id matches existing row with different date and shop returns Duplicate and store is unchanged`() = runTest {
        val store = FakeFileStore()
        val repository = FileSalesRepository(store)
        repository.load()
        val e1 = SalesEntry(id = "existing-id", date = "2026-10-06", shop = "D WEB", cd = 1, mobile = 0, bajaj = 0, idfc = 0, hdb = 0, tvs = 0, poonwala = 0, others = 0)
        repository.add(e1)
        val contentBefore = store.fileContent

        val e2 = SalesEntry(id = "existing-id", date = "2026-10-07", shop = "AEON DIGITAL", cd = 2, mobile = 0, bajaj = 0, idfc = 0, hdb = 0, tvs = 0, poonwala = 0, others = 0)
        val res = repository.add(e2)
        assertTrue(res is SaveResult.Duplicate)
        assertEquals("existing-id", (res as SaveResult.Duplicate).existingId)
        assertEquals(1, repository.entries.value.size)
        assertEquals(contentBefore, store.fileContent)
    }

    @Test
    fun `T31 renameFinanceKey migrates legacy fields and does not revive old key`() = runTest {
        val store = FakeFileStore()
        val repository = FileSalesRepository(store)
        repository.load()
        val legacyEntry = SalesEntry(
            id = "1", date = "2026-10-06", shop = "D WEB", cd = 1, mobile = 0,
            bajaj = 500, idfc = 0, hdb = 0, tvs = 0, poonwala = 0, others = 0
        )
        repository.add(legacyEntry)

        repository.renameFinanceKey("Bajaj", "Bajaj Prime")
        val updated = repository.entries.value[0]

        assertEquals(500, updated.getFinanceValue("Bajaj Prime"))
        assertEquals(0, updated.getFinanceValue("Bajaj"))
        assertNull(updated.bajaj)
        assertEquals(500, updated.totalFinance)
    }

    @Test
    fun `T32 deleteFinanceKey removes legacy key and nullifies legacy field`() = runTest {
        val store = FakeFileStore()
        val repository = FileSalesRepository(store)
        repository.load()
        val legacyEntry = SalesEntry(
            id = "1", date = "2026-10-06", shop = "D WEB", cd = 1, mobile = 0,
            bajaj = 500, idfc = 0, hdb = 0, tvs = 0, poonwala = 0, others = 0
        )
        repository.add(legacyEntry)

        repository.deleteFinanceKey("Bajaj")
        val updated = repository.entries.value[0]

        assertEquals(0, updated.getFinanceValue("Bajaj"))
        assertNull(updated.bajaj)
        assertEquals(0, updated.totalFinance)
    }

    @Test
    fun `T34 custom shop entries are preserved on load and not skipped`() = runTest {
        val store = FakeFileStore()
        val customEntry = SalesEntry(
            id = "custom-1", date = "2026-10-06", shop = "MY CUSTOM SHOP", cd = 2, mobile = 1
        )
        store.write(kotlinx.serialization.json.Json.encodeToString(listOf(customEntry)))

        val repository = FileSalesRepository(store)
        repository.load()

        assertEquals(1, repository.entries.value.size)
        assertEquals("MY CUSTOM SHOP", repository.entries.value[0].shop)
        assertEquals(0, repository.loadReport.value?.skipped)
    }

    @Test
    fun `T36 JSON array with single bad record recovers valid records`() = runTest {
        val store = FakeFileStore()
        val jsonArrayWithBadElement = """
            [
              {"id": "valid-1", "date": "2026-10-06", "shop": "D WEB", "cd": 1, "mobile": 0},
              {"bad": "malformed_entry_missing_date_and_shop"},
              {"id": "valid-2", "date": "2026-10-07", "shop": "D-MART", "cd": 2, "mobile": 3}
            ]
        """.trimIndent()
        store.write(jsonArrayWithBadElement)

        val repository = FileSalesRepository(store)
        repository.load()

        assertEquals(2, repository.entries.value.size)
        assertEquals(1, repository.loadReport.value?.skipped)
    }

    @Test
    fun `T37 duplicate check in add rejects case-insensitive shop match`() = runTest {
        val store = FakeFileStore()
        val repository = FileSalesRepository(store)
        repository.load()

        val entry1 = SalesEntry(id = "1", date = "2026-10-06", shop = "D WEB", cd = 1, mobile = 0)
        val entry2 = SalesEntry(id = "2", date = "2026-10-06", shop = "d web", cd = 2, mobile = 1)

        val res1 = repository.add(entry1)
        assertTrue(res1 is SaveResult.Saved)

        val res2 = repository.add(entry2)
        assertTrue(res2 is SaveResult.Duplicate)
    }

    @Test
    fun `T38 duplicate check in import rejects case-insensitive shop match`() = runTest {
        val store = FakeFileStore()
        val repository = FileSalesRepository(store)
        repository.load()

        val existing = SalesEntry(id = "1", date = "2026-10-06", shop = "D WEB", cd = 1, mobile = 0)
        repository.add(existing)

        val importList = listOf(
            SalesEntry(id = "2", date = "2026-10-06", shop = "d web", cd = 2, mobile = 1),
            SalesEntry(id = "3", date = "2026-10-07", shop = "d web", cd = 5, mobile = 2)
        )

        val importRes = repository.import(importList)
        assertTrue(importRes is ImportResult.Imported)
        val result = importRes as ImportResult.Imported
        assertEquals(1, result.imported)
        assertEquals(1, result.skippedDuplicates)
    }

    @Test
    fun `T43 mutations return false and do not overwrite when load failed`() = runTest {
        val failingStore = FakeFileStore().apply { shouldThrowOnRead = true }
        val repository = FileSalesRepository(failingStore)
        repository.load()

        assertFalse(repository.renameShop("D WEB", "NEW SHOP"))
        assertFalse(repository.renameFinanceKey("Bajaj", "NewBajaj"))
        assertFalse(repository.deleteFinanceKey("Bajaj"))
    }
}

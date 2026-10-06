package com.naseeb.salestracker.data

import com.naseeb.salestracker.data.model.SalesEntry
import com.naseeb.salestracker.data.repo.FileSalesRepository
import com.naseeb.salestracker.data.repo.ImportResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileSalesRepositoryImportTest {

    @Test
    fun `T26 import of 3 rows, 1 duplicating an existing row Imported(2, 1, 0)`() = runTest {
        val fileStore = FakeFileStore()
        val repo = FileSalesRepository(fileStore)
        repo.load()

        repo.add(SalesEntry(id = "1", date = "2026-10-06", shop = "D WEB", cd = 0, mobile = 0, bajaj = 0, idfc = 0, hdb = 0, tvs = 0, poonwala = 0, others = 0))

        val importList = listOf(
            SalesEntry(id = "2", date = "2026-10-07", shop = "D WEB", cd = 0, mobile = 0, bajaj = 0, idfc = 0, hdb = 0, tvs = 0, poonwala = 0, others = 0),
            SalesEntry(id = "3", date = "2026-10-08", shop = "AEON DIGITAL", cd = 0, mobile = 0, bajaj = 0, idfc = 0, hdb = 0, tvs = 0, poonwala = 0, others = 0),
            SalesEntry(id = "4", date = "2026-10-06", shop = "D WEB", cd = 1, mobile = 1, bajaj = 0, idfc = 0, hdb = 0, tvs = 0, poonwala = 0, others = 0)
        )

        val result = repo.import(importList)
        assertTrue(result is ImportResult.Imported)
        val imported = result as ImportResult.Imported
        assertEquals(2, imported.imported)
        assertEquals(1, imported.skippedDuplicates)
        assertEquals(0, imported.skippedInvalid)
        assertEquals(3, repo.entries.value.size)
    }
}

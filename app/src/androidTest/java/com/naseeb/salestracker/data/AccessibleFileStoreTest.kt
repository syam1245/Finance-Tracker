package com.naseeb.salestracker.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.naseeb.salestracker.data.store.AccessibleFileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AccessibleFileStoreTest {

    @Test
    fun revisionSelectsNewestCopyEvenWhenItsMtimeIsOlder() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "sales-store-test-${System.nanoTime()}").apply { mkdirs() }
        try {
            val primary = File(root, "primary").apply { mkdirs() }
            val secondary = File(root, "secondary").apply { mkdirs() }
            val store = AccessibleFileStore(
                context = context,
                ioDispatcher = Dispatchers.IO,
                overridePrimaryDir = primary,
                overrideSecondaryDir = secondary,
                overrideLegacyDir = File(root, "legacy")
            )

            store.write("original")
            val primaryFile = File(primary, "sales_data.json")
            val secondaryFile = File(secondary, "sales_data.json")
            val revision = primaryFile.readText().substringAfter(':').substringBefore('\n').toLong()
            secondaryFile.writeText("SALES_TRACKER_STORE_V1:${revision + 1}\nnewest-fallback")
            primaryFile.setLastModified(System.currentTimeMillis() + 60_000)
            secondaryFile.setLastModified(1_000)

            assertEquals("newest-fallback", store.read())
            assertTrue(primaryFile.readText().endsWith("newest-fallback"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun legacyRawDataIsReadableAndMigratedToVersionedPrimary() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "sales-store-legacy-test-${System.nanoTime()}").apply { mkdirs() }
        try {
            val primary = File(root, "primary").apply { mkdirs() }
            val secondary = File(root, "secondary").apply { mkdirs() }
            val legacy = File(root, "legacy").apply { mkdirs() }
            File(legacy, "sales_data.jsonl").writeText("legacy-jsonl")
            val store = AccessibleFileStore(
                context = context,
                ioDispatcher = Dispatchers.IO,
                overridePrimaryDir = primary,
                overrideSecondaryDir = secondary,
                overrideLegacyDir = legacy
            )

            assertEquals("legacy-jsonl", store.read())
            assertTrue(File(primary, "sales_data.json").readText().startsWith("SALES_TRACKER_STORE_V1:0\n"))
        } finally {
            root.deleteRecursively()
        }
    }
}

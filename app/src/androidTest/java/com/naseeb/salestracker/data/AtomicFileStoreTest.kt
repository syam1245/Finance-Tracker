package com.naseeb.salestracker.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.naseeb.salestracker.data.store.AtomicFileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AtomicFileStoreTest {

    private lateinit var file: File
    private lateinit var store: AtomicFileStore

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        file = File(context.filesDir, "test_store.jsonl")
        if (file.exists()) {
            file.delete()
        }
        val backupFile = File(context.filesDir, "test_store.jsonl.bak")
        if (backupFile.exists()) backupFile.delete()
        store = AtomicFileStore(file, Dispatchers.IO)
    }

    @Test
    fun testReadMissingReturnsNull() = runBlocking {
        assertNull(store.read())
    }

    @Test
    fun testWriteThenReadRoundTrips() = runBlocking {
        val text = "hello world\nline 2"
        store.write(text)
        assertEquals(text, store.read())
    }

    @Test
    fun testSecondWriteReplacesFirst() = runBlocking {
        store.write("first")
        assertEquals("first", store.read())
        store.write("second")
        assertEquals("second", store.read())
    }
}

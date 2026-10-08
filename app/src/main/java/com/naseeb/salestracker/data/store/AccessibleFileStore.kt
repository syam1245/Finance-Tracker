package com.naseeb.salestracker.data.store

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File

class AccessibleFileStore(
    private val context: Context? = null,
    private val ioDispatcher: CoroutineDispatcher,
    private val fileName: String = "sales_data.json",
    private val legacyFileName: String = "sales_data.jsonl",
    overridePrimaryDir: File? = null,
    overrideSecondaryDir: File? = null,
    overrideLegacyDir: File? = null
) : FileStore {

    private data class VersionedText(val revision: Long, val text: String, val primary: Boolean)

    private val envelopePrefix = "SALES_TRACKER_STORE_V1:"

    private val primaryFile: File by lazy {
        val appDir = overridePrimaryDir ?: File(
            context?.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context?.filesDir ?: File("."),
            "SalesTracker"
        )
        if (!appDir.exists()) {
            appDir.mkdirs()
        }
        File(appDir, fileName)
    }

    private val secondaryFile: File by lazy {
        val appDir = overrideSecondaryDir ?: (context?.filesDir ?: File("."))
        if (!appDir.exists()) {
            appDir.mkdirs()
        }
        File(appDir, fileName)
    }

    private val legacyFile: File by lazy {
        val appDir = overrideLegacyDir ?: (context?.filesDir ?: File("."))
        File(appDir, legacyFileName)
    }

    private val primaryStore by lazy { AtomicFileStore(primaryFile, ioDispatcher) }
    private val secondaryStore by lazy { AtomicFileStore(secondaryFile, ioDispatcher) }
    private val legacyStore by lazy { AtomicFileStore(legacyFile, ioDispatcher) }

    override suspend fun read(): String? = withContext(ioDispatcher) {
        val candidates = listOf(
            safeRead(primaryStore)?.let { decodeCandidate(it, primary = true) },
            safeRead(secondaryStore)?.let { decodeCandidate(it, primary = false) },
            safeRead(legacyStore)?.let { decodeCandidate(it, primary = false) }
        ).filterNotNull().filter { it.text.isNotBlank() }

        var selected: VersionedText? = null
        for (candidate in candidates) {
            if (selected == null || candidate.revision > selected.revision) selected = candidate
        }
        if (selected != null) {
            if (!selected.primary) {
                try {
                    primaryFile.parentFile?.mkdirs()
                    primaryStore.write(encodeEnvelope(selected.revision, selected.text))
                } catch (_: Exception) {}
            }
            return@withContext selected.text
        }

        // Backward compatibility: attempt reading from legacy public directory if accessible
        try {
            val oldPublicDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "SalesTracker"
            )
            val oldPublicFile = File(oldPublicDir, fileName)
            if (oldPublicFile.exists() && oldPublicFile.canRead()) {
                val oldText = oldPublicFile.readText(Charsets.UTF_8)
                if (oldText.isNotBlank()) {
                    try {
                        primaryStore.write(encodeEnvelope(0, oldText))
                    } catch (_: Exception) {}
                    return@withContext oldText
                }
            }
        } catch (_: Exception) {}

        null
    }

    override suspend fun write(text: String) = withContext(ioDispatcher) {
        val currentRevision = listOfNotNull(
            safeRead(primaryStore)?.let { decodeCandidate(it, primary = true)?.revision },
            safeRead(secondaryStore)?.let { decodeCandidate(it, primary = false)?.revision },
            safeRead(legacyStore)?.let { decodeCandidate(it, primary = false)?.revision }
        ).maxOrNull() ?: 0L
        check(currentRevision < Long.MAX_VALUE) { "Storage revision counter is exhausted" }
        val nextRevision = currentRevision + 1L
        val storedText = encodeEnvelope(nextRevision, text)
        try {
            primaryFile.parentFile?.mkdirs()
            primaryStore.write(storedText)
        } catch (primaryException: Exception) {
            try {
                secondaryFile.parentFile?.mkdirs()
                secondaryStore.write(storedText)
            } catch (_: Exception) {
                throw primaryException
            }
        }
    }

    override suspend fun writeBackup(name: String, text: String) = withContext(ioDispatcher) {
        try {
            primaryStore.writeBackup(name, text)
            pruneBackups(primaryFile.parentFile)
        } catch (_: Exception) {
            try {
                secondaryStore.writeBackup(name, text)
                pruneBackups(secondaryFile.parentFile)
            } catch (_: Exception) {}
        }
    }

    private fun pruneBackups(dir: File?) {
        if (dir == null || !dir.exists()) return
        try {
            val backups = dir.listFiles { file ->
                file.isFile && (file.name.contains(".corrupt-") || file.name.contains("_corrupt_backup_"))
            }?.sortedByDescending { it.lastModified() } ?: return

            if (backups.size > 5) {
                backups.drop(5).forEach { it.delete() }
            }
        } catch (_: Exception) {}
    }

    private fun encodeEnvelope(revision: Long, text: String): String =
        "$envelopePrefix$revision\n$text"

    private fun decodeCandidate(raw: String, primary: Boolean): VersionedText? {
        if (!raw.startsWith(envelopePrefix)) return VersionedText(0L, raw, primary)
        val separator = raw.indexOf('\n')
        if (separator < 0) return VersionedText(0L, raw, primary)
        val revision = raw.substring(envelopePrefix.length, separator).toLongOrNull()
            ?: return VersionedText(0L, raw, primary)
        if (revision < 0L) return VersionedText(0L, raw, primary)
        return VersionedText(revision, raw.substring(separator + 1), primary)
    }

    private suspend fun safeRead(store: FileStore): String? = try {
        store.read()
    } catch (_: Exception) {
        null
    }
}

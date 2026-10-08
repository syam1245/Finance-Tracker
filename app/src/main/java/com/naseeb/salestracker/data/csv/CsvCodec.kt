package com.naseeb.salestracker.data.csv

import com.naseeb.salestracker.data.model.AppSettings
import com.naseeb.salestracker.data.model.SalesEntry
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.io.PushbackReader
import java.io.Reader
import java.io.StringReader
import java.io.StringWriter
import java.io.Writer
import java.util.UUID

object CsvCodec {

    data class SkippedRow(val lineNumber: Int, val reason: String)

    sealed interface CsvDecodeResult {
        data class Success(val entries: List<SalesEntry>, val skippedRows: List<SkippedRow>) : CsvDecodeResult
        data object InvalidHeader : CsvDecodeResult
    }

    fun encode(
        entries: List<SalesEntry>,
        financeOptions: List<String> = AppSettings.DEFAULT_FINANCE_OPTIONS
    ): String = StringWriter().also { encode(entries, financeOptions, it) }.toString()

    fun encode(
        entries: List<SalesEntry>,
        financeOptions: List<String>,
        writer: Writer
    ) {
        // Collect distinct finance options from settings and any historical entry records
        val allFinanceOptions = mutableListOf<String>()
        val seenUpper = mutableSetOf<String>()
        for (opt in financeOptions) {
            if (seenUpper.add(opt.uppercase())) {
                allFinanceOptions.add(opt)
            }
        }
        for (entry in entries) {
            for (key in entry.getResolvedFinances().keys) {
                if (seenUpper.add(key.uppercase())) {
                    allFinanceOptions.add(key)
                }
            }
        }

        val headers = mutableListOf("DATE", "SHOP NAME", "CD", "MOBILE", "TOTAL COUNT")
        headers.addAll(allFinanceOptions.map { sanitizeForCsv(it.uppercase()) })
        headers.add("TOTAL FINANCE")

        writer.append(headers.joinToString(",") { escapeHeader(it) }).append('\n')
        for (entry in entries) {
            val cols = mutableListOf(
                sanitizeForCsv(entry.date),
                sanitizeForCsv(entry.shop),
                entry.cd.toString(),
                entry.mobile.toString(),
                entry.totalCount.toString()
            )
            for (opt in allFinanceOptions) {
                cols.add(entry.getFinanceValue(opt).toString())
            }
            cols.add(entry.totalFinance.toString())

            writer.append(cols.joinToString(",") { escapeField(it) }).append('\n')
        }
        writer.flush()
    }

    private fun sanitizeForCsv(value: String): String {
        val trimmed = value.trim()
        if (trimmed.startsWith("=") || trimmed.startsWith("+") || trimmed.startsWith("-") || trimmed.startsWith("@")) {
            return "'$trimmed"
        }
        return value
    }

    private fun desanitizeFromCsv(value: String): String {
        val trimmed = value.trim()
        if (trimmed.startsWith("'") && trimmed.length > 1 &&
            (trimmed[1] == '=' || trimmed[1] == '+' || trimmed[1] == '-' || trimmed[1] == '@')
        ) {
            return trimmed.substring(1)
        }
        return value
    }

    private fun escapeField(value: String): String {
        return "\"" + value.replace("\"", "\"\"") + "\""
    }

    private fun escapeHeader(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

    fun decode(
        text: String,
        knownShops: List<String> = com.naseeb.salestracker.data.model.Shops.ALL,
        fallbackFinances: List<String> = AppSettings.DEFAULT_FINANCE_OPTIONS
    ): CsvDecodeResult = decode(StringReader(text), knownShops, fallbackFinances)

    fun decode(
        reader: Reader,
        knownShops: List<String> = com.naseeb.salestracker.data.model.Shops.ALL,
        fallbackFinances: List<String> = AppSettings.DEFAULT_FINANCE_OPTIONS
    ): CsvDecodeResult {
        val rows = try {
            parseCsvRows(reader).iterator()
        } catch (_: Exception) {
            return CsvDecodeResult.InvalidHeader
        }
        val header = try {
            if (rows.hasNext()) rows.next() else return CsvDecodeResult.Success(emptyList(), emptyList())
        } catch (_: Exception) {
            return CsvDecodeResult.InvalidHeader
        }

        val headerRow = header.map { desanitizeFromCsv(it.trim()).uppercase() }
        if (headerRow.size < 6) return CsvDecodeResult.InvalidHeader

        // Validate leading columns
        if (headerRow[0] != "DATE" || headerRow[1] != "SHOP NAME" ||
            headerRow[2] != "CD" || headerRow[3] != "MOBILE" ||
            headerRow[4] != "TOTAL COUNT"
        ) {
            return CsvDecodeResult.InvalidHeader
        }

        // Identify finance columns
        val financeColumns = mutableListOf<Pair<Int, String>>()
        for (i in 5 until headerRow.size) {
            val col = headerRow[i]
            if (col == "TOTAL FINANCE") continue
            // Find matched finance option name or use title-cased column name
            val matchedName = fallbackFinances.firstOrNull { it.equals(col, ignoreCase = true) }
                ?: col.lowercase().replaceFirstChar { it.uppercase() }
            financeColumns.add(i to matchedName)
        }

        val entries = mutableListOf<SalesEntry>()
        val skipped = mutableListOf<SkippedRow>()

        var lineNumber = 1
        try {
            while (rows.hasNext()) {
            val row = rows.next()
            lineNumber++
            if (row.isEmpty() || (row.size == 1 && row[0].isBlank())) continue // empty line

            if (row.size != headerRow.size) {
                skipped.add(SkippedRow(lineNumber, "wrong column count"))
                continue
            }

            val dateStr = desanitizeFromCsv(row[0].trim())
            val parsedDate = try {
                if (dateStr.contains(" ")) {
                    val dt = LocalDateTime.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    dt.toLocalDate().toString()
                } else {
                    LocalDate.parse(dateStr).toString()
                }
            } catch (e: DateTimeParseException) {
                skipped.add(SkippedRow(lineNumber, "bad date"))
                continue
            }

            val rawShop = desanitizeFromCsv(row[1].trim())
            val matchedShop = if (knownShops.isNotEmpty()) {
                knownShops.firstOrNull { it.equals(rawShop, ignoreCase = true) }
            } else {
                rawShop
            }
            if (knownShops.isNotEmpty() && matchedShop == null) {
                skipped.add(SkippedRow(lineNumber, "unknown shop"))
                continue
            }
            val shop = matchedShop ?: rawShop

            fun parseCount(s: String): Int? {
                val num = s.trim().toIntOrNull()
                if (num == null || num < 0 || num > 999_999) return null
                return num
            }

            val cd = parseCount(row[2])
            val mobile = parseCount(row[3])
            if (cd == null || mobile == null) {
                skipped.add(SkippedRow(lineNumber, "unparseable or negative count"))
                continue
            }

            val financeValues = mutableMapOf<String, Int>()
            var parseError = false
            for ((colIndex, optName) in financeColumns) {
                val count = parseCount(row[colIndex])
                if (count == null) {
                    parseError = true
                    break
                }
                financeValues[optName] = count
            }

            if (parseError) {
                skipped.add(SkippedRow(lineNumber, "unparseable or negative count in finance"))
                continue
            }

            val entry = SalesEntry(
                id = UUID.randomUUID().toString(),
                date = parsedDate,
                shop = shop,
                cd = cd,
                mobile = mobile,
                financeValues = financeValues
            )
            entries.add(entry)
        }
        } catch (_: Exception) {
            return CsvDecodeResult.InvalidHeader
        }

        return CsvDecodeResult.Success(entries, skipped)
    }

    private fun parseCsvRows(source: Reader): Sequence<List<String>> = sequence {
        val reader = if (source is PushbackReader) source else PushbackReader(source, 1)
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var afterClosingQuote = false
        var fieldStarted = false
        var firstCharacter = true

        fun finishField() {
            row.add(field.toString())
            field.setLength(0)
            fieldStarted = false
            afterClosingQuote = false
        }

        fun finishRow(): List<String> {
            finishField()
            val complete = row
            row = mutableListOf()
            return complete
        }

        while (true) {
            val value = reader.read()
            if (value == -1) break
            val c = value.toChar()
            if (firstCharacter) {
                firstCharacter = false
                if (c == '\uFEFF') continue
            }

            if (inQuotes) {
                if (c == '"') {
                    val next = reader.read()
                    if (next == '"'.code) {
                        field.append('"')
                    } else {
                        inQuotes = false
                        afterClosingQuote = true
                        if (next != -1) reader.unread(next)
                    }
                } else {
                    field.append(c)
                }
                continue
            }

            when (c) {
                '"' -> {
                    if (fieldStarted || afterClosingQuote) throw IllegalArgumentException("Invalid quote placement")
                    inQuotes = true
                    fieldStarted = true
                }
                ',' -> {
                    finishField()
                }
                '\r', '\n' -> {
                    if (c == '\r') {
                        val next = reader.read()
                        if (next != '\n'.code && next != -1) reader.unread(next)
                    }
                    yield(finishRow())
                }
                else -> {
                    if (afterClosingQuote) throw IllegalArgumentException("Unexpected text after quoted field")
                    field.append(c)
                    fieldStarted = true
                }
            }
        }

        if (inQuotes) throw IllegalArgumentException("Unclosed quoted field")
        if (fieldStarted || row.isNotEmpty()) yield(finishRow())
    }
}

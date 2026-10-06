package com.naseeb.salestracker.data.csv

import com.naseeb.salestracker.data.model.SalesEntry
import com.naseeb.salestracker.data.model.SalesEntryValidator
import com.naseeb.salestracker.data.model.Shops
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.UUID

object CsvCodec {
    private val HEADER = "DATE,SHOP NAME,CD,MOBILE,TOTAL COUNT,BAJAJ,IDFC,HDB,TVS,POONWALA,OTHERS,TOTAL FINANCE"

    data class SkippedRow(val lineNumber: Int, val reason: String)

    sealed interface CsvDecodeResult {
        data class Success(val entries: List<SalesEntry>, val skippedRows: List<SkippedRow>) : CsvDecodeResult
        object InvalidHeader : CsvDecodeResult
    }

    fun encode(entries: List<SalesEntry>): String {
        val sb = StringBuilder()
        sb.append(HEADER).append("\n")
        for (entry in entries) {
            val cols = listOf(
                entry.date,
                entry.shop,
                entry.cd.toString(),
                entry.mobile.toString(),
                entry.totalCount.toString(),
                entry.bajaj.toString(),
                entry.idfc.toString(),
                entry.hdb.toString(),
                entry.tvs.toString(),
                entry.poonwala.toString(),
                entry.others.toString(),
                entry.totalFinance.toString()
            )
            val line = cols.joinToString(",") { escapeField(it) }
            sb.append(line).append("\n")
        }
        return sb.toString()
    }

    private fun escapeField(value: String): String {
        return "\"" + value.replace("\"", "\"\"") + "\""
    }

    fun decode(text: String): CsvDecodeResult {
        var cleanText = text
        if (cleanText.startsWith("\uFEFF")) {
            cleanText = cleanText.substring(1)
        }
        val lines = parseCsvLineTokens(cleanText)
        if (lines.isEmpty()) return CsvDecodeResult.Success(emptyList(), emptyList())

        val headerRow = lines[0]
        val expectedHeaders = HEADER.split(",")
        if (headerRow.size != expectedHeaders.size) return CsvDecodeResult.InvalidHeader
        
        for (i in expectedHeaders.indices) {
            if (!headerRow[i].equals(expectedHeaders[i], ignoreCase = true)) {
                return CsvDecodeResult.InvalidHeader
            }
        }

        val entries = mutableListOf<SalesEntry>()
        val skipped = mutableListOf<SkippedRow>()

        for (i in 1 until lines.size) {
            val row = lines[i]
            val lineNumber = i + 1
            if (row.isEmpty() || (row.size == 1 && row[0].isBlank())) continue // empty line

            if (row.size != expectedHeaders.size) {
                skipped.add(SkippedRow(lineNumber, "wrong column count"))
                continue
            }

            val dateStr = row[0].trim()
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

            val shop = row[1].trim()
            if (shop !in Shops.ALL) {
                skipped.add(SkippedRow(lineNumber, "unknown shop"))
                continue
            }

            fun parseCount(s: String): Int? {
                val num = s.trim().toIntOrNull()
                if (num == null || num < 0 || num > 999_999) return null
                return num
            }

            val cd = parseCount(row[2])
            val mobile = parseCount(row[3])
            val bajaj = parseCount(row[5])
            val idfc = parseCount(row[6])
            val hdb = parseCount(row[7])
            val tvs = parseCount(row[8])
            val poonwala = parseCount(row[9])
            val others = parseCount(row[10])

            if (cd == null || mobile == null || bajaj == null || idfc == null || hdb == null || tvs == null || poonwala == null || others == null) {
                skipped.add(SkippedRow(lineNumber, "unparseable or negative count"))
                continue
            }

            val entry = SalesEntry(
                id = UUID.randomUUID().toString(),
                date = parsedDate,
                shop = shop,
                cd = cd,
                mobile = mobile,
                bajaj = bajaj,
                idfc = idfc,
                hdb = hdb,
                tvs = tvs,
                poonwala = poonwala,
                others = others
            )
            entries.add(entry)
        }

        return CsvDecodeResult.Success(entries, skipped)
    }

    private fun parseCsvLineTokens(text: String): List<List<String>> {
        val result = mutableListOf<List<String>>()
        var currentRow = mutableListOf<String>()
        var currentField = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        currentField.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    currentField.append(c)
                }
            } else {
                if (c == '"') {
                    inQuotes = true
                } else if (c == ',') {
                    currentRow.add(currentField.toString())
                    currentField.clear()
                } else if (c == '\r') {
                    if (i + 1 < text.length && text[i + 1] == '\n') {
                        currentRow.add(currentField.toString())
                        result.add(currentRow)
                        currentRow = mutableListOf()
                        currentField.clear()
                        i++
                    } else {
                        currentRow.add(currentField.toString())
                        result.add(currentRow)
                        currentRow = mutableListOf()
                        currentField.clear()
                    }
                } else if (c == '\n') {
                    currentRow.add(currentField.toString())
                    result.add(currentRow)
                    currentRow = mutableListOf()
                    currentField.clear()
                } else {
                    currentField.append(c)
                }
            }
            i++
        }
        if (currentField.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(currentField.toString())
            result.add(currentRow)
        }
        return result
    }
}

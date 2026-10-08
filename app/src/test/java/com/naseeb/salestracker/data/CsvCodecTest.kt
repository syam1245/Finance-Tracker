package com.naseeb.salestracker.data

import com.naseeb.salestracker.data.csv.CsvCodec
import com.naseeb.salestracker.data.model.SalesEntry
import org.junit.Assert.*
import org.junit.Test

class CsvCodecTest {

    @Test
    fun `T19 encode of T1 entry equals exactly`() {
        val entry = SalesEntry(
            id = "1",
            date = "2026-10-06",
            shop = "D WEB",
            cd = 2, mobile = 3,
            bajaj = 1, idfc = 0, hdb = 2, tvs = 0, poonwala = 0, others = 4
        )
        val encoded = CsvCodec.encode(listOf(entry))
        val expected = "DATE,SHOP NAME,CD,MOBILE,TOTAL COUNT,BAJAJ,IDFC,HDB,TVS,POONWALA,OTHERS,TOTAL FINANCE\n" +
                "\"2026-10-06\",\"D WEB\",\"2\",\"3\",\"5\",\"1\",\"0\",\"2\",\"0\",\"0\",\"4\",\"7\"\n"
        assertEquals(expected, encoded)
    }

    @Test
    fun `T20 decode handles BOM plus CRLF`() {
        val text = "\uFEFFDATE,SHOP NAME,CD,MOBILE,TOTAL COUNT,BAJAJ,IDFC,HDB,TVS,POONWALA,OTHERS,TOTAL FINANCE\r\n" +
                "\"2026-10-06\",\"D WEB\",\"2\",\"3\",\"5\",\"1\",\"0\",\"2\",\"0\",\"0\",\"4\",\"7\"\r\n"
        val result = CsvCodec.decode(text)
        assertTrue(result is CsvCodec.CsvDecodeResult.Success)
        val entries = (result as CsvCodec.CsvDecodeResult.Success).entries
        assertEquals(1, entries.size)
        assertEquals("2026-10-06", entries[0].date)
    }

    @Test
    fun `T21 decode normalizes datetime to date`() {
        val text = "DATE,SHOP NAME,CD,MOBILE,TOTAL COUNT,BAJAJ,IDFC,HDB,TVS,POONWALA,OTHERS,TOTAL FINANCE\n" +
                "\"2026-10-06 00:00:00\",\"D WEB\",\"2\",\"3\",\"5\",\"1\",\"0\",\"2\",\"0\",\"0\",\"4\",\"7\"\n"
        val result = CsvCodec.decode(text)
        val entries = (result as CsvCodec.CsvDecodeResult.Success).entries
        assertEquals("2026-10-06", entries[0].date)
    }

    @Test
    fun `T22 decode skips a negative-count row and reports its line number and reason`() {
        val text = "DATE,SHOP NAME,CD,MOBILE,TOTAL COUNT,BAJAJ,IDFC,HDB,TVS,POONWALA,OTHERS,TOTAL FINANCE\n" +
                "\"2026-10-06\",\"D WEB\",\"-2\",\"3\",\"5\",\"1\",\"0\",\"2\",\"0\",\"0\",\"4\",\"7\"\n"
        val result = CsvCodec.decode(text)
        val success = result as CsvCodec.CsvDecodeResult.Success
        assertEquals(0, success.entries.size)
        assertEquals(1, success.skippedRows.size)
        assertEquals(2, success.skippedRows[0].lineNumber)
        assertEquals("unparseable or negative count", success.skippedRows[0].reason)
    }

    @Test
    fun `T23 decode skips an unknown-shop row`() {
        val text = "DATE,SHOP NAME,CD,MOBILE,TOTAL COUNT,BAJAJ,IDFC,HDB,TVS,POONWALA,OTHERS,TOTAL FINANCE\n" +
                "\"2026-10-06\",\"UNKNOWN\",\"2\",\"3\",\"5\",\"1\",\"0\",\"2\",\"0\",\"0\",\"4\",\"7\"\n"
        val result = CsvCodec.decode(text)
        val success = result as CsvCodec.CsvDecodeResult.Success
        assertEquals(0, success.entries.size)
        assertEquals(1, success.skippedRows.size)
        assertEquals(2, success.skippedRows[0].lineNumber)
        assertEquals("unknown shop", success.skippedRows[0].reason)
    }

    @Test
    fun `T24 decode returns InvalidHeader for a wrong header`() {
        val text = "WRONG,HEADER\n\"2026-10-06\",\"D WEB\"\n"
        val result = CsvCodec.decode(text)
        assertEquals(CsvCodec.CsvDecodeResult.InvalidHeader, result)
    }

    @Test
    fun `T25 decode ignores wrong TOTAL values and recomputes them`() {
        val text = "DATE,SHOP NAME,CD,MOBILE,TOTAL COUNT,BAJAJ,IDFC,HDB,TVS,POONWALA,OTHERS,TOTAL FINANCE\n" +
                "\"2026-10-06\",\"D WEB\",\"2\",\"3\",\"999\",\"1\",\"0\",\"2\",\"0\",\"0\",\"4\",\"999\"\n"
        val result = CsvCodec.decode(text)
        val success = result as CsvCodec.CsvDecodeResult.Success
        assertEquals(1, success.entries.size)
        assertEquals(5, success.entries[0].totalCount)
        assertEquals(7, success.entries[0].totalFinance)
    }

    @Test
    fun `T33 decode matches shop case-insensitively and canonicalizes name`() {
        val text = "DATE,SHOP NAME,CD,MOBILE,TOTAL COUNT,BAJAJ,IDFC,HDB,TVS,POONWALA,OTHERS,TOTAL FINANCE\n" +
                "\"2026-10-06\",\"d web\",\"2\",\"3\",\"5\",\"1\",\"0\",\"2\",\"0\",\"0\",\"4\",\"7\"\n"
        val result = CsvCodec.decode(text)
        assertTrue(result is CsvCodec.CsvDecodeResult.Success)
        val entries = (result as CsvCodec.CsvDecodeResult.Success).entries
        assertEquals(1, entries.size)
        assertEquals("D WEB", entries[0].shop)
    }

    @Test
    fun `T35 encode escapes headers with commas and decode parses them successfully`() {
        val entry = SalesEntry(
            id = "1",
            date = "2026-10-06",
            shop = "D WEB",
            cd = 1,
            mobile = 1,
            financeValues = mapOf("EMI, 0%" to 2)
        )
        val customFinances = listOf("EMI, 0%")
        val encoded = CsvCodec.encode(listOf(entry), customFinances)
        assertTrue(encoded.contains("\"EMI, 0%\""))

        val decoded = CsvCodec.decode(encoded, fallbackFinances = customFinances)
        assertTrue(decoded is CsvCodec.CsvDecodeResult.Success)
        val success = decoded as CsvCodec.CsvDecodeResult.Success
        assertEquals(1, success.entries.size)
        assertEquals(2, success.entries[0].getFinanceValue("EMI, 0%"))
    }

    @Test
    fun `T39 formula injection characters are neutralized on encode and preserved on decode`() {
        val entry = SalesEntry(
            id = "1",
            date = "2026-10-06",
            shop = "=CMD|' /C calc'!A0",
            cd = 1,
            mobile = 1
        )
        val encoded = CsvCodec.encode(listOf(entry))
        assertTrue(encoded.contains("\"'=CMD|' /C calc'!A0\""))

        val decoded = CsvCodec.decode(encoded, knownShops = emptyList())
        assertTrue(decoded is CsvCodec.CsvDecodeResult.Success)
        val success = decoded as CsvCodec.CsvDecodeResult.Success
        assertEquals(1, success.entries.size)
        assertEquals("=CMD|' /C calc'!A0", success.entries[0].shop)
    }

    @Test
    fun `T40 strict column count rejects rows with extra columns`() {
        val text = "DATE,SHOP NAME,CD,MOBILE,TOTAL COUNT,BAJAJ,IDFC,HDB,TVS,POONWALA,OTHERS,TOTAL FINANCE\n" +
                "\"2026-10-06\",\"D WEB\",\"1\",\"0\",\"1\",\"0\",\"0\",\"0\",\"0\",\"0\",\"0\",\"0\",\"EXTRA_FIELD\"\n"
        val result = CsvCodec.decode(text)
        assertTrue(result is CsvCodec.CsvDecodeResult.Success)
        val success = result as CsvCodec.CsvDecodeResult.Success
        assertEquals(0, success.entries.size)
        assertEquals(1, success.skippedRows.size)
        assertEquals("wrong column count", success.skippedRows[0].reason)
    }

    @Test
    fun `T41 unclosed quote in CSV returns InvalidHeader`() {
        val text = "DATE,SHOP NAME,CD,MOBILE,TOTAL COUNT,BAJAJ,IDFC,HDB,TVS,POONWALA,OTHERS,TOTAL FINANCE\n" +
                "\"2026-10-06\",\"UNCLOSED_QUOTE\n"
        val result = CsvCodec.decode(text)
        assertEquals(CsvCodec.CsvDecodeResult.InvalidHeader, result)
    }

    @Test
    fun `T42 encode includes historical finance keys missing from current settings options`() {
        val entry = SalesEntry(
            id = "1",
            date = "2026-10-06",
            shop = "D WEB",
            cd = 1,
            mobile = 1,
            financeValues = mapOf("OldKey" to 5)
        )
        // Pass only "Bajaj" as current active finance option
        val encoded = CsvCodec.encode(listOf(entry), financeOptions = listOf("Bajaj"))
        assertTrue(encoded.contains("OLDKEY"))
        assertTrue(encoded.contains("BAJAJ"))

        val decoded = CsvCodec.decode(encoded)
        assertTrue(decoded is CsvCodec.CsvDecodeResult.Success)
        val success = decoded as CsvCodec.CsvDecodeResult.Success
        assertEquals(1, success.entries.size)
        assertEquals(5, success.entries[0].getFinanceValue("OldKey"))
    }

    @Test
    fun `formula-like finance headers are neutralized and round trip`() {
        val entry = SalesEntry(
            id = "1",
            date = "2026-10-06",
            shop = "D WEB",
            financeValues = mapOf("=HYPERLINK" to 3)
        )
        val encoded = CsvCodec.encode(listOf(entry), listOf("=HYPERLINK"))

        assertTrue(encoded.startsWith("DATE,SHOP NAME,CD,MOBILE,TOTAL COUNT,'=HYPERLINK"))
        val decoded = CsvCodec.decode(encoded, fallbackFinances = listOf("=HYPERLINK"))
        assertTrue(decoded is CsvCodec.CsvDecodeResult.Success)
        assertEquals(3, (decoded as CsvCodec.CsvDecodeResult.Success).entries.single().getFinanceValue("=HYPERLINK"))
    }

    @Test
    fun `CSV rejects quotes after closing quote and quote in unquoted fields`() {
        val header = "DATE,SHOP NAME,CD,MOBILE,TOTAL COUNT,BAJAJ,TOTAL FINANCE\n"
        val trailingText = header + "\"2026-10-06\",\"D WEB\"junk,1,0,1,0,0\n"
        val quoteInUnquoted = header + "2026-10-06,D \"WEB\",1,0,1,0,0\n"

        assertEquals(CsvCodec.CsvDecodeResult.InvalidHeader, CsvCodec.decode(trailingText))
        assertEquals(CsvCodec.CsvDecodeResult.InvalidHeader, CsvCodec.decode(quoteInUnquoted))
    }
}

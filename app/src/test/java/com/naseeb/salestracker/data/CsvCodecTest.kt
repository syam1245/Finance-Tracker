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
}

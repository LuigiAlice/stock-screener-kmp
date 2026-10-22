package de.luigialice.stockscreener

import de.luigialice.stockscreener.data.CsvUniverse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CsvUniverseTest {

    @Test
    fun parsesTickerAndNameAndExchange() {
        val csv = "ticker,name,exchange\nBRK-B,Berkshire,NYSE\nAAPL,Apple,NASDAQ\n"
        val universe = CsvUniverse.parse(csv)
        assertEquals(listOf("BRK-B", "AAPL"), universe.tickers)
        assertEquals(mapOf("BRK-B" to "Berkshire", "AAPL" to "Apple"), universe.stocks.mapValues { it.value.name })
        assertEquals(mapOf("BRK-B" to "NYSE", "AAPL" to "NASDAQ"), universe.stocks.mapValues { it.value.exchange })
    }

    @Test
    fun deduplicatesAndSkipsInvalid() {
        val csv = "ticker,name\nAAPL,Apple\nAAPL,Apple Inc\n,Blank\nnan,Nan\n"
        val universe = CsvUniverse.parse(csv)
        assertEquals(listOf("AAPL"), universe.tickers)
    }

    @Test
    fun symbolColumnAndGermanName() {
        val universe = CsvUniverse.parse("symbol,aktienname\nMSFT,Microsoft\n")
        assertEquals(listOf("MSFT"), universe.tickers)
        assertEquals("Microsoft", universe.stocks["MSFT"]?.name)
    }

    @Test
    fun withoutTickerHeaderFails() {
        assertFailsWith<IllegalStateException> { CsvUniverse.parse("wkn,name\n123,Foo\n") }
    }

    @Test
    fun quotedNamesWithComma() {
        val universe = CsvUniverse.parse("ticker,name\nMSFT,\"Microsoft, Inc.\"\n")
        assertEquals("Microsoft, Inc.", universe.stocks["MSFT"]?.name)
    }
}

package de.luigialice.stockscreener

import de.luigialice.stockscreener.screener.DEFAULT_TICKERS
import de.luigialice.stockscreener.screener.normalizeTicker
import de.luigialice.stockscreener.screener.parseTickers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TickersTest {

    @Test
    fun keepsKnownExchangeSuffix() {
        assertEquals("SIE.DE", normalizeTicker("sie.de"))
    }

    @Test
    fun replacesUnknownDot() {
        assertEquals("BRK-B", normalizeTicker("BRK.B"))
    }

    @Test
    fun stripsAndUppercases() {
        assertEquals("AAPL", normalizeTicker("  aapl "))
    }

    @Test
    fun nanVariantsEmpty() {
        assertEquals("", normalizeTicker("nan"))
        assertEquals("", normalizeTicker("NaN"))
        assertEquals("", normalizeTicker(""))
    }

    @Test
    fun splitsAndDeduplicates() {
        assertEquals(listOf("AAPL", "MSFT"), parseTickers("AAPL, msft; MSFT AAPL"))
    }

    @Test
    fun preservesOrder() {
        assertEquals(listOf("MSFT", "AAPL"), parseTickers("MSFT AAPL"))
    }

    @Test
    fun defaultUniverseComplete() {
        val tickers = parseTickers(DEFAULT_TICKERS)
        assertEquals(25, tickers.size)
        assertTrue(tickers.containsAll(listOf("SAP.DE", "VOW3.DE", "V")))
    }
}

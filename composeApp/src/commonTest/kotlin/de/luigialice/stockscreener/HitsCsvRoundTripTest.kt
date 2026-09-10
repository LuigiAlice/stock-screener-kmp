package de.luigialice.stockscreener

import de.luigialice.stockscreener.model.ScanMode
import de.luigialice.stockscreener.model.ScanRow
import de.luigialice.stockscreener.ui.results.buildImportedResult
import de.luigialice.stockscreener.ui.results.parseHitsCsv
import de.luigialice.stockscreener.ui.results.toCsv
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HitsCsvRoundTripTest {

    private fun row(
        ticker: String = "AAPL",
        name: String = "Apple Inc.",
        score: Int = 5,
        relVolume: Double = 1.8,
        criteria: Map<String, Boolean> = mapOf("Rückgang 10 Tage" to true, "Über SMA20" to false),
    ) = ScanRow(
        ticker = ticker,
        name = name,
        price = 123.45,
        performance = -15.2,
        perf12m = -25.0,
        below12m = true,
        rsi14 = 38.7,
        relVolume = relVolume,
        sma20Pct = 1.2,
        sma50Pct = 3.4,
        criteria = criteria,
        score = score,
        lastTradingDay = "2026-09-09",
        yahooChart = "https://finance.yahoo.com/quote/AAPL",
        yahooSummary = "https://finance.yahoo.com/quote/AAPL/profile",
        google = "https://www.google.com/finance/quote/AAPL"
    )

    @Test
    fun exportsAndReimportsRows() {
        val rows = listOf(row(), row(ticker = "GE", name = "General Electric", score = 7, relVolume = 2.1))
        val csv = rows.toCsv()
        val parsed = parseHitsCsv(csv)

        assertEquals(2, parsed.size)
        val first = parsed[0]
        assertEquals("AAPL", first.ticker)
        assertEquals("Apple Inc.", first.name)
        assertEquals(123.45, first.price, 0.01)
        assertEquals(-15.2, first.performance!!, 0.01)
        assertEquals(-25.0, first.perf12m!!, 0.01)
        assertEquals(38.7, first.rsi14, 0.05)
        assertEquals(1.8, first.relVolume, 0.01)
        assertEquals(1.2, first.sma20Pct, 0.01)
        assertEquals(3.4, first.sma50Pct, 0.01)
        assertEquals(5, first.score)
        assertEquals("2026-09-09", first.lastTradingDay)
        assertEquals("https://finance.yahoo.com/quote/AAPL", first.yahooChart)
        assertEquals(mapOf("rückgang 10 tage" to true, "über sma20" to false), first.criteria)
    }

    @Test
    fun importedSettingsInferModeFromCriteriaCount() {
        val result = buildImportedResult(parseHitsCsv(row().let { listOf(it) }.toCsv()))
        assertEquals(ScanMode.ONGOING_RECOVERY, result.settings.mode)
        assertEquals(false, result.settings.onlyBelow12m)
        assertEquals(1, result.settings.scoreMin)
    }

    @Test
    fun rejectsForeignCsv() {
        assertTrue(parseHitsCsv("ticker,name,exchange\nAAPL,Apple,NASDAQ\n").isEmpty())
        assertTrue(parseHitsCsv("").isEmpty())
    }
}

package de.luigialice.stockscreener

import de.luigialice.stockscreener.model.ScanRow
import de.luigialice.stockscreener.ui.results.sortedByColumn
import kotlin.test.Test
import kotlin.test.assertEquals

/** Sortierung der Trefferliste über Spaltenköpfe (Zahlen und Ja/Nein-Kriterien). */
class ColumnSortTest {

    private fun row(
        ticker: String,
        price: Double = 100.0,
        score: Int = 5,
        rsi: Double = 40.0,
        criteria: Map<String, Boolean> = mapOf("RSI im Band" to true, "Über SMA20" to false),
    ) = ScanRow(
        ticker = ticker, name = ticker, price = price, performance = null, perf12m = null,
        below12m = true, rsi14 = rsi, relVolume = 1.0, sma20Pct = 0.0, sma50Pct = 0.0,
        criteria = criteria, score = score, lastTradingDay = "2026-10-23",
        yahooChart = "", yahooSummary = "", google = ""
    )

    private val rows = listOf(
        row("BBB", price = 200.0, score = 3, rsi = 55.0),
        row("AAA", price = 100.0, score = 8, rsi = 30.0),
        row("CCC", price = 150.0, score = 5, rsi = 45.0),
    )

    @Test
    fun nullKeyKeepsTheGivenOrder() {
        assertEquals(listOf("BBB", "AAA", "CCC"), rows.sortedByColumn(null, descending = true).map { it.ticker })
    }

    @Test
    fun sortsByScore() {
        assertEquals(listOf("AAA", "CCC", "BBB"), rows.sortedByColumn("Score", descending = true).map { it.ticker })
        assertEquals(listOf("BBB", "CCC", "AAA"), rows.sortedByColumn("Score", descending = false).map { it.ticker })
    }

    @Test
    fun sortsByPrice() {
        assertEquals(listOf("BBB", "CCC", "AAA"), rows.sortedByColumn("Kurs", descending = true).map { it.ticker })
    }

    @Test
    fun sortsByTickerAlphabetically() {
        assertEquals(listOf("AAA", "BBB", "CCC"), rows.sortedByColumn("Ticker", descending = false).map { it.ticker })
    }

    @Test
    fun sortsByCriterionJaBeforeNein() {
        val mixed = listOf(
            row("NEIN", criteria = mapOf("RSI im Band" to false)),
            row("JA", criteria = mapOf("RSI im Band" to true)),
        )
        assertEquals(listOf("JA", "NEIN"), mixed.sortedByColumn("RSI im Band", descending = true).map { it.ticker })
        assertEquals(listOf("NEIN", "JA"), mixed.sortedByColumn("RSI im Band", descending = false).map { it.ticker })
    }

    @Test
    fun sortIsStableForEqualValues() {
        val equal = listOf(row("A", score = 5), row("B", score = 5), row("C", score = 5))
        assertEquals(listOf("A", "B", "C"), equal.sortedByColumn("Score", descending = true).map { it.ticker })
    }

    @Test
    fun unknownColumnSortsLikeAnUnmetCriterion() {
        // Unbekannte Kriteriums-Spalte darf nicht crashen
        assertEquals(listOf("BBB", "AAA", "CCC"), rows.sortedByColumn("GibtsNicht", descending = true).map { it.ticker })
    }
}

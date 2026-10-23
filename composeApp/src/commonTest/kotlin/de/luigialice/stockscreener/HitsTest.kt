package de.luigialice.stockscreener

import de.luigialice.stockscreener.model.ScanMode
import de.luigialice.stockscreener.model.ScanRow
import de.luigialice.stockscreener.model.ScanSettings
import de.luigialice.stockscreener.ui.results.hits
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Treffer-Auswahl: Muss-Kriterien (vom Nutzer gesetzt), Trefferfilter (Score) und 12M-Regel.
 * Alle Kriterien sind grundsätzlich Kann-Kriterien – ohne Muss-Markierung filtert nur der Score.
 * Die Score-Berechnung selbst ist in ScreenerTest abgesichert.
 */
class HitsTest {

    private fun row(
        ticker: String,
        score: Int,
        criteria: Map<String, Boolean>,
        below12m: Boolean = true,
    ) = ScanRow(
        ticker = ticker,
        name = ticker,
        price = 100.0,
        performance = -15.0,
        perf12m = -20.0,
        below12m = below12m,
        rsi14 = 40.0,
        relVolume = 1.5,
        sma20Pct = 1.0,
        sma50Pct = -2.0,
        criteria = criteria,
        score = score,
        lastTradingDay = "2026-10-23",
        yahooChart = "", yahooSummary = "", google = ""
    )

    private val allCriteria = mapOf(
        "Perf-Kriterium" to true, "RSI im Band" to true, "Über SMA20" to true,
        "Unter SMA50" to true, "Volumen ok" to true, "RSI-Erholung" to true,
        "SMA20-Cross" to true, "Höheres Tief" to true,
    )

    private fun row(ticker: String, score: Int, vararg overrides: Pair<String, Boolean>, below12m: Boolean = true) =
        row(ticker, score, allCriteria + overrides.toMap(), below12m)

    private val rebound = ScanSettings(scoreMin = 1, onlyBelow12m = false, mode = ScanMode.REBOUND_START)

    // --- Muss-Kriterien (vom Nutzer markiert) ---

    @Test
    fun nothingIsFilteredWithoutMussMarks() {
        val rows = listOf(
            row("A", score = 8),
            row("B", score = 3, "RSI im Band" to false, "Perf-Kriterium" to false),
        )
        assertEquals(listOf("A", "B"), rows.hits(1, rebound).map { it.ticker })
    }

    @Test
    fun mussCriterionDropsViolatingRows() {
        val rows = listOf(
            row("IN", score = 5, "RSI im Band" to true),
            row("OUT", score = 8, "RSI im Band" to false),
        )
        assertEquals(listOf("IN"), rows.hits(1, rebound, muss = setOf("RSI im Band")).map { it.ticker })
    }

    @Test
    fun everyMussCriterionMustHold() {
        val rows = listOf(
            row("A", score = 8),
            row("B", score = 8, "Volumen ok" to false),
            row("C", score = 8, "Perf-Kriterium" to false),
            row("D", score = 8, "RSI im Band" to false),
        )
        val muss = setOf("Perf-Kriterium", "RSI im Band", "Volumen ok")
        assertEquals(listOf("A"), rows.hits(1, rebound, muss = muss).map { it.ticker })
    }

    @Test
    fun mussDoesNotChangeTheScore() {
        val row = row("A", score = 8, "RSI im Band" to false)
        // Der Score bleibt wie berechnet – Muss filtert nur, es wird nicht umgewertet
        assertEquals(8, row.score)
        assertTrue(listOf(row).hits(1, rebound, muss = setOf("RSI im Band")).isEmpty())
    }

    @Test
    fun missingCriterionColumnDoesNotBlock() {
        // Z. B. CSV-Import ohne die Spalte: das Muss-Kriterium ist dann nicht prüfbar
        val row = row("A", score = 5, mapOf("RSI im Band" to true))
        assertEquals(listOf("A"), listOf(row).hits(1, rebound, muss = setOf("SMA20-Cross")).map { it.ticker })
    }

    // --- Bestehendes Verhalten ---

    @Test
    fun scoreBelowTheFilterIsNoHit() {
        assertTrue(listOf(row("LOW", score = 4)).hits(5, rebound).isEmpty())
    }

    @Test
    fun scoreAtTheFilterIsAHit() {
        assertEquals(listOf("HIT"), listOf(row("HIT", score = 5)).hits(5, rebound).map { it.ticker })
    }

    @Test
    fun below12mFilterStillApplies() {
        val settings = rebound.copy(onlyBelow12m = true)
        assertTrue(listOf(row("UP", score = 5, below12m = false)).hits(1, settings).isEmpty())
        assertEquals(listOf("DOWN"), listOf(row("DOWN", score = 5, below12m = true)).hits(1, settings).map { it.ticker })
    }

    @Test
    fun below12mDisabledShowsRowsAboveThe12mPrice() {
        assertEquals(listOf("UP"), listOf(row("UP", score = 5, below12m = false)).hits(1, rebound).map { it.ticker })
    }

    @Test
    fun filterKeepsOrderOfTheGivenRows() {
        val rows = listOf(row("A", score = 6), row("B", score = 8), row("C", score = 7))
        assertEquals(listOf("A", "B", "C"), rows.hits(1, rebound).map { it.ticker })
    }
}

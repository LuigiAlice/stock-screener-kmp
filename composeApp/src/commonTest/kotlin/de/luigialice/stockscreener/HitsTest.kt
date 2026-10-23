package de.luigialice.stockscreener

import de.luigialice.stockscreener.model.ScanMode
import de.luigialice.stockscreener.model.ScanRow
import de.luigialice.stockscreener.model.ScanSettings
import de.luigialice.stockscreener.ui.results.hits
import de.luigialice.stockscreener.model.mandatoryCriteria
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Treffer-Auswahl: Muss-Kriterien der Rebound-Regeln, Trefferfilter (Score) und 12M-Regel.
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

    private val reboundRules = mapOf(
        "Perf-Kriterium" to true, "RSI im Band" to true, "Volumen ok" to true
    )

    private fun reboundRow(
        ticker: String = "HIT",
        score: Int = 5,
        perf: Boolean = true,
        rsiImBand: Boolean = true,
        volumenOk: Boolean = true,
        below12m: Boolean = true,
    ) = row(
        ticker, score,
        reboundRules +
            ("Perf-Kriterium" to perf) + ("RSI im Band" to rsiImBand) + ("Volumen ok" to volumenOk) +
            ("Über SMA20" to (score >= 4)) + ("Unter SMA50" to (score >= 5)) +
            ("RSI-Erholung" to (score >= 6)) + ("SMA20-Cross" to (score >= 7)) + ("Höheres Tief" to (score >= 8)),
        below12m
    )

    private fun ongoingRow(ticker: String, score: Int, rsiImBand: Boolean) = row(
        ticker, score,
        mapOf(
            "Unter 12M-Kurs" to true, "Über SMA20" to true, "Über SMA50" to true,
            "Höheres Tief" to true, "RSI im Band" to rsiImBand
        )
    )

    private val rebound = ScanSettings(scoreMin = 1, onlyBelow12m = false, mode = ScanMode.REBOUND_START)
    private val ongoing = ScanSettings(scoreMin = 1, onlyBelow12m = false, mode = ScanMode.ONGOING_RECOVERY)

    // --- Muss-Kriterien der Rebound-Regeln (neu) ---

    @Test
    fun rsiOutsideBandIsNoHitEvenWithTopScore() {
        val hits = listOf(reboundRow("OUT", score = 8, rsiImBand = false)).hits(1, rebound)
        assertTrue(hits.isEmpty(), "Aktie außerhalb des RSI-Bereichs darf kein Treffer sein")
    }

    @Test
    fun missedPerfCriterionIsNoHit() {
        val hits = listOf(reboundRow("OUT", score = 8, perf = false)).hits(1, rebound)
        assertTrue(hits.isEmpty(), "Aktie ohne Perf-Kriterium darf kein Treffer sein")
    }

    @Test
    fun missingVolumeIsNoHit() {
        val hits = listOf(reboundRow("OUT", score = 8, volumenOk = false)).hits(1, rebound)
        assertTrue(hits.isEmpty(), "Aktie ohne Mindestvolumen darf kein Treffer sein")
    }

    @Test
    fun everyMandatoryCriterionIsRequired() {
        assertEquals(listOf("Perf-Kriterium", "RSI im Band", "Volumen ok"), ScanMode.REBOUND_START.mandatoryCriteria)
        val hits = listOf(
            reboundRow("A", perf = true, rsiImBand = true, volumenOk = true),
            reboundRow("B", perf = true, rsiImBand = true, volumenOk = false),
            reboundRow("C", perf = true, rsiImBand = false, volumenOk = true),
            reboundRow("D", perf = false, rsiImBand = true, volumenOk = true),
        ).hits(1, rebound)
        assertEquals(listOf("A"), hits.map { it.ticker })
    }

    @Test
    fun ongoingModeRequiresTheRsiBand() {
        val hits = listOf(ongoingRow("OUT", score = 5, rsiImBand = false)).hits(1, ongoing)
        assertTrue(hits.isEmpty(), "RSI-Bereich gilt im Modus Laufende Erholung ebenfalls als Muss")
    }

    @Test
    fun missingCriterionCountsAsNotMet() {
        val incomplete = row("OUT", score = 5, mapOf("RSI im Band" to true))
        assertTrue(listOf(incomplete).hits(1, rebound).isEmpty(), "Fehlendes Kriterium ist nicht erfüllt")
    }

    // --- Bestehendes Verhalten (darf sich nicht ändern) ---

    @Test
    fun rowsMeetingTheRulesAndScoreAreHits() {
        val hits = listOf(reboundRow("HIT", score = 5)).hits(5, rebound)
        assertEquals(listOf("HIT"), hits.map { it.ticker })
    }

    @Test
    fun scoreBelowTheFilterIsNoHit() {
        assertTrue(listOf(reboundRow("LOW", score = 4)).hits(5, rebound).isEmpty())
    }

    @Test
    fun scoreAtTheFilterIsAHit() {
        assertEquals(listOf("HIT"), listOf(reboundRow("HIT", score = 5)).hits(5, rebound).map { it.ticker })
    }

    @Test
    fun below12mFilterStillApplies() {
        val settings = rebound.copy(onlyBelow12m = true)
        assertTrue(listOf(reboundRow("UP", below12m = false)).hits(1, settings).isEmpty())
        assertEquals(listOf("DOWN"), listOf(reboundRow("DOWN", below12m = true)).hits(1, settings).map { it.ticker })
    }

    @Test
    fun below12mDisabledShowsRowsAboveThe12mPrice() {
        val hits = listOf(reboundRow("UP", below12m = false)).hits(1, rebound)
        assertEquals(listOf("UP"), hits.map { it.ticker })
    }

    @Test
    fun filterKeepsOrderOfTheGivenRows() {
        val rows = listOf(reboundRow("A", score = 6), reboundRow("B", score = 8), reboundRow("C", score = 7))
        assertEquals(listOf("A", "B", "C"), rows.hits(1, rebound).map { it.ticker })
    }
}

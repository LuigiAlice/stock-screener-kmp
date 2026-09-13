package de.luigialice.stockscreener

import de.luigialice.stockscreener.model.Bar
import de.luigialice.stockscreener.model.FailedReason
import de.luigialice.stockscreener.model.ScanMode
import de.luigialice.stockscreener.model.ScanParams
import de.luigialice.stockscreener.model.ScanSettings
import de.luigialice.stockscreener.model.Universe
import de.luigialice.stockscreener.screener.Screener
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun frame(closes: List<Double>, volumes: List<Double>? = null): List<Bar> {
    val start = LocalDate(2025, 1, 1)
    return closes.indices.map { i ->
        Bar(start.plus(i, DateTimeUnit.DAY), closes[i], volumes?.get(i) ?: 1000.0)
    }
}

private val PERF10_PCT = (100.0 / 107.5 - 1) * 100
private val PERF20_PCT = (100.0 / 110.0 - 1) * 100
private val RELVOL = 2000.0 / 1050.0
private val CRAFTED = listOf(103.5, 104.5, 105.5, 106.5, 107.5, 108.5, 109.5, 110.5, 109.0, 107.5, 106.0, 104.5, 103.0, 101.5, 100.0)

private fun boundaryFrame(): List<Bar> =
    frame(List(85) { 110.0 } + CRAFTED, List(99) { 1000.0 } + 2000.0)

private fun frame12m(level: Double): List<Bar> =
    frame(List(285) { level } + CRAFTED, List(299) { 1000.0 } + 2000.0)

private fun frameRecovery(level: Double = 130.0): List<Bar> {
    val decline = (1..20).map { level - 2.0 * it }
    val rise = mutableListOf<Double>()
    for (k in 0 until 20) {
        rise.add(92.0 + 0.5 * k)
        rise.add(90.0 + 0.5 * (k + 1))
    }
    val tail = listOf(102.0, 100.5, 102.5, 101.0, 103.0)
    return frame(List(235) { level } + decline + rise + tail, List(299) { 1000.0 } + 2000.0)
}

private val paramsStart = ScanParams(ScanMode.REBOUND_START, days = 10, maxPerf = PERF10_PCT, rsiMin = 39.99, rsiMax = 48.0, minVolume = RELVOL)

private fun assertClose(expected: Double, actual: Double, tolerance: Double = 1e-9) {
    assertTrue("expected $expected ± $tolerance, got $actual") { kotlin.math.abs(expected - actual) <= tolerance }
}

class ScreenerTest {

    @Test
    fun fullRowValuesAndBaseScore() {
        val row = Screener.calcRow("AAA", "Alpha", boundaryFrame(), paramsStart)!!
        assertEquals(100.0, row.price)
        assertClose(PERF10_PCT, row.performance!!)
        assertClose(40.0, row.rsi14, 0.01)
        assertClose(RELVOL, row.relVolume)
        assertClose((100.0 / 106.875 - 1) * 100, row.sma20Pct)
        assertClose((100.0 / 108.75 - 1) * 100, row.sma50Pct)
        assertEquals(true, row.criteria["RSI-Erholung"])
        assertEquals(false, row.criteria["SMA20-Cross"])
        assertEquals(false, row.criteria["Höheres Tief"])
        assertEquals(true, row.criteria["Perf-Kriterium"])
        assertEquals(true, row.criteria["RSI im Band"])
        assertEquals(false, row.criteria["Über SMA20"])
        assertEquals(true, row.criteria["Unter SMA50"])
        assertEquals(true, row.criteria["Volumen ok"])
        assertEquals(5, row.score)
        assertEquals(false, row.below12m)
    }

    @Test
    fun defaultSliderParams() {
        val p = ScanParams(ScanMode.REBOUND_START, days = 21, maxPerf = -12.0, rsiMin = 35.0, rsiMax = 48.0, minVolume = 1.3)
        val row = Screener.calcRow("AAA", "A", boundaryFrame(), p)!!
        assertClose(PERF20_PCT, row.performance!!, 1e-6)
        assertEquals(4, row.score)
    }

    @Test
    fun maxPerfBoundaryInclusive() {
        val row = Screener.calcRow("AAA", "A", boundaryFrame(), paramsStart)!!
        assertEquals(5, row.score)
        val p = paramsStart.copy(maxPerf = PERF10_PCT - 1e-12)
        assertEquals(4, Screener.calcRow("AAA", "A", boundaryFrame(), p)!!.score)
    }

    @Test
    fun rsiBoundaries() {
        assertEquals(5, Screener.calcRow("AAA", "A", boundaryFrame(), paramsStart)!!.score)
        assertEquals(4, Screener.calcRow("AAA", "A", boundaryFrame(), paramsStart.copy(rsiMin = 40.001))!!.score)
        assertEquals(4, Screener.calcRow("AAA", "A", boundaryFrame(), paramsStart.copy(rsiMax = 39.98))!!.score)
    }

    @Test
    fun minVolumeBoundaryInclusive() {
        assertEquals(5, Screener.calcRow("AAA", "A", boundaryFrame(), paramsStart)!!.score)
        assertEquals(4, Screener.calcRow("AAA", "A", boundaryFrame(), paramsStart.copy(minVolume = RELVOL + 1e-12))!!.score)
    }

    @Test
    fun daysParameterChangesWindow() {
        val row10 = Screener.calcRow("AAA", "A", boundaryFrame(), paramsStart)!!
        val row20 = Screener.calcRow("AAA", "A", boundaryFrame(), paramsStart.copy(days = 20, maxPerf = PERF20_PCT))!!
        assertClose(PERF10_PCT, row10.performance!!, 1e-9)
        assertClose(PERF20_PCT, row20.performance!!, 1e-9)
    }

    @Test
    fun below12mPrice() {
        val row = Screener.calcRow("AAA", "A", frame12m(130.0), paramsStart)!!
        assertClose((100.0 / 130.0 - 1) * 100, row.perf12m!!, 1e-9)
        assertEquals(true, row.below12m)
        assertEquals(5, row.score)
    }

    @Test
    fun above12mPrice() {
        val row = Screener.calcRow("AAA", "A", frame12m(95.0), paramsStart)!!
        assertClose((100.0 / 95.0 - 1) * 100, row.perf12m!!, 1e-9)
        assertEquals(false, row.below12m)
    }

    @Test
    fun equal12mPriceCountsAsNotBelow() {
        val row = Screener.calcRow("AAA", "A", frame12m(100.0), paramsStart)!!
        assertClose(0.0, row.perf12m!!, 1e-9)
        assertEquals(false, row.below12m)
    }

    @Test
    fun shortHistoryGetsNo12mData() {
        val row = Screener.calcRow("AAA", "A", boundaryFrame(), paramsStart)!!
        assertNull(row.perf12m)
        assertEquals(false, row.below12m)
        assertEquals(5, row.score)
    }

    @Test
    fun fullOngoingRecovery() {
        val p = ScanParams(ScanMode.ONGOING_RECOVERY, rsiMin = 45.0, rsiMax = 65.0)
        val row = Screener.calcRow("NNDO", "A", frameRecovery(), p)!!
        assertClose((103.0 / 130.0 - 1) * 100, row.perf12m!!, 1e-9)
        assertClose(57.14, row.rsi14, 0.01)
        assertTrue(row.sma20Pct > 0)
        assertTrue(row.sma50Pct > 0)
        assertEquals(true, row.criteria["Unter 12M-Kurs"])
        assertEquals(true, row.criteria["Über SMA20"])
        assertEquals(true, row.criteria["Über SMA50"])
        assertEquals(true, row.criteria["Höheres Tief"])
        assertEquals(true, row.criteria["RSI im Band"])
        assertEquals(5, row.score)
        assertNull(row.performance)
    }

    @Test
    fun ongoingRsiBandBoundaries() {
        val p = ScanParams(ScanMode.ONGOING_RECOVERY, rsiMin = 45.0, rsiMax = 65.0)
        assertEquals(5, Screener.calcRow("NNDO", "A", frameRecovery(), p)!!.score)
        assertEquals(4, Screener.calcRow("NNDO", "A", frameRecovery(), p.copy(rsiMin = 57.2))!!.score)
        assertEquals(4, Screener.calcRow("NNDO", "A", frameRecovery(), p.copy(rsiMax = 57.0))!!.score)
    }

    @Test
    fun ongoingAbove12mDropsCriterion() {
        val p = ScanParams(ScanMode.ONGOING_RECOVERY, rsiMin = 45.0, rsiMax = 65.0)
        val row = Screener.calcRow("NNDO", "A", frameRecovery(level = 80.0), p)!!
        assertEquals(false, row.criteria["Unter 12M-Kurs"])
        assertEquals(4, row.score)
    }

    @Test
    fun guards() {
        val closes = List(79) { 100.0 + (it % 2) }
        assertNull(Screener.calcRow("AAA", "A", frame(closes), paramsStart))
        val ok = List(80) { 100.0 + (it % 2) }
        assertNull(Screener.calcRow("AAA", "A", frame(ok), paramsStart.copy(days = 80)))
        assertNull(Screener.calcRow("AAA", "A", frame(List(100) { 100.0 + (it % 2) }, List(100) { 0.0 }), paramsStart))
        assertNull(Screener.calcRow("AAA", "A", frame((0 until 100).map { 100.0 + it }), paramsStart))
        assertNull(Screener.calcRow("AAA", "A", frame(List(100) { 100.0 }), paramsStart))
        val days60 = List(80) { 100.0 + (it % 2) }
        assertTrue(Screener.calcRow("AAA", "A", frame(days60), paramsStart.copy(days = 60, maxPerf = -100.0)) != null)
    }

    @Test
    fun scanSortsAndCollectsFailed() {
        val universe = Universe(listOf("AAA", "BBB", "XXX"), mapOf("AAA" to "Alpha", "BBB" to "Beta", "XXX" to "X"), emptyMap())
        val bars = mapOf("AAA" to frameRecovery(), "BBB" to frame12m(130.0))
        val result = Screener.scan(
            universe,
            ScanParams(ScanMode.ONGOING_RECOVERY, rsiMin = 45.0, rsiMax = 65.0),
            ScanSettings(4, true, ScanMode.ONGOING_RECOVERY)
        ) { bars[it] }
        assertEquals(listOf("AAA", "BBB"), result.rows.map { it.ticker })
        assertEquals(mapOf("XXX" to FailedReason.NO_DATA), result.failed)
        assertTrue(result.rows.first().score >= result.rows.last().score)
    }
}

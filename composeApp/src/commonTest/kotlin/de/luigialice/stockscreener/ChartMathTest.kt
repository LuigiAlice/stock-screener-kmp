package de.luigialice.stockscreener

import de.luigialice.stockscreener.model.Bar
import de.luigialice.stockscreener.ui.chart.chartX
import de.luigialice.stockscreener.ui.chart.chartY
import de.luigialice.stockscreener.ui.chart.computeChartScale
import de.luigialice.stockscreener.ui.chart.hoverIndex
import de.luigialice.stockscreener.ui.chart.monthTicks
import de.luigialice.stockscreener.ui.chart.volumeLabel
import de.luigialice.stockscreener.ui.chart.xTickIndices
import de.luigialice.stockscreener.ui.chart.yTickValues
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChartMathTest {

    private fun bars(vararg closes: Double): List<Bar> =
        closes.mapIndexed { i, c -> Bar(LocalDate(2025, 1, 1).plus(i, DateTimeUnit.DAY), c, 1000.0 * (i + 1)) }

    @Test
    fun scaleIsNullForFewerThanTwoBars() {
        assertNull(computeChartScale(emptyList()))
        assertNull(computeChartScale(listOf(Bar(LocalDate(2025, 1, 1), 100.0, 1.0))))
    }

    @Test
    fun scaleAddsFivePercentPaddingOnBothEnds() {
        val scale = computeChartScale(bars(100.0, 200.0))!!
        assertEquals(95.0, scale.lo, 1e-9)
        assertEquals(205.0, scale.hi, 1e-9)
        assertEquals(110.0, scale.range, 1e-9)
    }

    @Test
    fun flatDataGetsFallbackRange() {
        val scale = computeChartScale(bars(100.0, 100.0, 100.0))!!
        assertEquals(99.95, scale.lo, 1e-9)
        assertEquals(100.05, scale.hi, 1e-9)
    }

    @Test
    fun chartYMapsRangeOntoPlotArea() {
        val scale = computeChartScale(bars(100.0, 200.0))!!
        assertEquals(110f, chartY(scale.lo, scale, 10f, 100f), 0.01f)
        assertEquals(10f, chartY(scale.hi, scale, 10f, 100f), 0.01f)
        val mid = chartY((scale.lo + scale.hi) / 2.0, scale, 10f, 100f)
        assertEquals(60f, mid, 0.01f)
    }

    @Test
    fun hoverIndexClampsToValidRange() {
        assertEquals(0, hoverIndex(-50f, 60f, 300f, 10))
        assertEquals(9, hoverIndex(10_000f, 60f, 300f, 10))
        assertEquals(4, hoverIndex(60f + 300f * 0.45f, 60f, 300f, 10))
    }

    @Test
    fun hoverIndexNeverDividesByZeroPlotWidth() {
        assertEquals(9, hoverIndex(1000f, 60f, 0f, 10))
    }

    @Test
    fun chartXHandlesTwoBarCount() {
        assertEquals(60f, chartX(0, 2, 60f, 300f), 0.01f)
        assertEquals(360f, chartX(1, 2, 60f, 300f), 0.01f)
    }

    @Test
    fun xTickIndicesAreDistinctAndBounded() {
        val two = xTickIndices(2)
        assertEquals(listOf(0, 1), two)
        val ten = xTickIndices(10)
        assertEquals(5, ten.size)
        assertTrue(ten.all { it in 0..9 })
        assertEquals(listOf(0, 9), listOf(ten.first(), ten.last()))
    }

    @Test
    fun yTickValuesSpanFullRange() {
        val scale = computeChartScale(bars(100.0, 200.0))!!
        val ticks = yTickValues(scale)
        assertEquals(5, ticks.size)
        assertEquals(scale.lo, ticks.first(), 1e-9)
        assertEquals(scale.hi, ticks.last(), 1e-9)
    }

    @Test
    fun monthTicksMarkMonthBoundariesAndYearStart() {
        // 70 Tage ab 1.1.2025: Jan 1 (Jahresstart), Feb 1 (Index 31), Mär 1 (Index 59)
        val ticks = monthTicks(bars(*DoubleArray(70) { 100.0 }))
        assertEquals(listOf(0, 31, 59), ticks.map { it.index })
        assertEquals(listOf(1, 2, 3), ticks.map { it.month })
        assertEquals(listOf(true, false, false), ticks.map { it.isYearStart })

        // Jahreswechsel im Verlauf: 15.12.2024 + 40 Tage → Dez-Tick + Jan-2025-Tick
        val yearBars = List(50) { i -> Bar(LocalDate(2024, 12, 15).plus(i, DateTimeUnit.DAY), 100.0, 1.0) }
        val yearTicks = monthTicks(yearBars)
        assertTrue(yearTicks.any { it.isYearStart && it.year == 2025 && it.month == 1 })
        assertEquals(2024, yearTicks.first().year)
        assertTrue(yearTicks.first().isYearStart)
    }

    @Test
    fun monthTicksEmptyForNoBars() {
        assertTrue(monthTicks(emptyList()).isEmpty())
    }

    @Test
    fun volumeLabelUsesSensibleUnits() {
        assertEquals("999", volumeLabel(999.0))
        assertTrue(volumeLabel(1_500.0).endsWith(" Tsd"))
        assertTrue(volumeLabel(2_500_000.0).endsWith("Mio"))
        assertTrue(volumeLabel(1_000_000_000.0).endsWith("Mrd"))
    }
}

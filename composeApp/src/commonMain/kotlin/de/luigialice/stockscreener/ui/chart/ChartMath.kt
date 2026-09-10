package de.luigialice.stockscreener.ui.chart

import de.luigialice.stockscreener.model.Bar
import kotlin.math.roundToInt

data class ChartScale(val lo: Double, val hi: Double, val range: Double)

fun computeChartScale(bars: List<Bar>): ChartScale? {
    if (bars.size < 2) return null
    val minV = bars.minOf { it.close }
    val maxV = bars.maxOf { it.close }
    val rawRange = (maxV - minV).takeIf { it > 0.0 } ?: 1.0
    val pad = rawRange * 0.05
    val lo = minV - pad
    val hi = maxV + pad
    return ChartScale(lo, hi, hi - lo)
}

fun chartY(v: Double, scale: ChartScale, plotTop: Float, plotH: Float): Float =
    (plotTop + plotH * (1.0 - (v - scale.lo) / scale.range)).toFloat()

fun chartX(i: Int, barCount: Int, padLeft: Float, plotW: Float): Float =
    padLeft + plotW * i / (barCount - 1)

fun hoverIndex(hx: Float, padLeft: Float, plotW: Float, barCount: Int): Int =
    (((hx - padLeft) / plotW.coerceAtLeast(1f) * (barCount - 1)).roundToInt()).coerceIn(0, barCount - 1)

fun xTickIndices(barCount: Int, ticks: Int = 5): List<Int> =
    List(ticks) { t -> ((barCount - 1) * t / (ticks - 1).coerceAtLeast(1)).coerceIn(0, barCount - 1) }.distinct()

fun yTickValues(scale: ChartScale, ticks: Int = 5): List<Double> =
    List(ticks) { t -> scale.lo + scale.range * t / (ticks - 1) }

data class MonthTick(val index: Int, val year: Int, val month: Int, val isYearStart: Boolean)

/** Index des ersten Handelstags jedes Monats; isYearStart markiert Jahreswechsel. */
fun monthTicks(bars: List<Bar>): List<MonthTick> {
    if (bars.isEmpty()) return emptyList()
    val out = mutableListOf<MonthTick>()
    var lastMonth = -1
    var lastYear = -1
    bars.forEachIndexed { i, bar ->
        val d = bar.date
        if (d.monthNumber != lastMonth || d.year != lastYear) {
            out += MonthTick(i, d.year, d.monthNumber, d.year != lastYear)
            lastMonth = d.monthNumber
            lastYear = d.year
        }
    }
    return out
}

fun volumeLabel(volume: Double): String = when {
    volume >= 1e9 -> String.format(java.util.Locale.GERMAN, "%,.2f Mrd", volume / 1e9)
    volume >= 1e6 -> String.format(java.util.Locale.GERMAN, "%,.2f Mio", volume / 1e6)
    volume >= 1e3 -> String.format(java.util.Locale.GERMAN, "%,.1f Tsd", volume / 1e3)
    else -> String.format(java.util.Locale.GERMAN, "%,.0f", volume)
}

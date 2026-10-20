package de.luigialice.stockscreener

import de.luigialice.stockscreener.model.Bar
import de.luigialice.stockscreener.model.ScanRow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

internal fun sampleRow(
    ticker: String = "AAPL",
    name: String = "Apple Inc",
    score: Int = 6,
    criteria: Map<String, Boolean> = (1..8).associate { "Kriterium $it" to (it <= 6) },
): ScanRow = ScanRow(
    ticker = ticker,
    name = name,
    price = 190.5,
    performance = -12.3,
    perf12m = -20.0,
    below12m = true,
    rsi14 = 42.0,
    relVolume = 1.5,
    sma20Pct = 1.2,
    sma50Pct = 2.3,
    criteria = criteria,
    score = score,
    lastTradingDay = "2026-10-16",
    yahooChart = "https://chart.test/$ticker",
    yahooSummary = "https://summary.test/$ticker",
    google = "https://google.test/$ticker",
)

internal fun sampleBars(count: Int = 40): List<Bar> = (0 until count).map { i ->
    Bar(LocalDate(2025, 1, 1).plus(i, DateTimeUnit.DAY), 100.0 + i, 1_000_000.0 + i)
}

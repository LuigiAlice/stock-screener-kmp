package de.luigialice.stockscreener.screener

import de.luigialice.stockscreener.model.Bar
import de.luigialice.stockscreener.model.FailedReason
import de.luigialice.stockscreener.model.ScanParams
import de.luigialice.stockscreener.model.ScanResult
import de.luigialice.stockscreener.model.ScanRow
import de.luigialice.stockscreener.model.ScanMode
import de.luigialice.stockscreener.model.ScanSettings
import de.luigialice.stockscreener.model.Universe

private fun mean(values: List<Double>): Double = values.sum() / values.size

private fun List<Double>.sma(window: Int): Double = mean(takeLast(window))

    private fun List<Double>.rsi14(atIndex: Int): Double? {
        val first = maxOf(1, atIndex - 13)
        var gain = 0.0
        var loss = 0.0
        var count = 0
        for (i in first..atIndex) {
            val change = this[i] - this[i - 1]
            if (change > 0) gain += change else loss += -change
            count++
        }
        val avgGain = gain / count
        val avgLoss = loss / count
        if (avgLoss == 0.0) return null
        return 100.0 - 100.0 / (1.0 + avgGain / avgLoss)
    }

object Screener {

    fun calcRow(ticker: String, name: String, bars: List<Bar>, p: ScanParams, exchange: String = ""): ScanRow? {
        if (bars.size < 80) return null
        val closes = bars.map { it.close }
        val volumes = bars.map { it.volume }
        val last = bars.size - 1
        val days = p.days

        val rsiNow = closes.rsi14(last)
        val rsiPrevious = closes.rsi14(last - 1)
        if (rsiNow == null || rsiPrevious == null) return null
        val vol20 = volumes.sma(20)
        if (vol20 <= 0.0) return null
        if (bars.size <= days) return null

        val price = closes[last]
        val relVolume = volumes[last] / vol20
        val perf12m = if (bars.size > 252) (price / closes[last - 252] - 1) * 100 else null
        val sma20 = closes.sma(20)
        val sma50 = closes.sma(50)
        val above20 = price > sma20
        val below50 = price < sma50
        val above50 = price > sma50
        val rsiRecovery = rsiPrevious < p.rsiMin && rsiNow >= p.rsiMin
        val smaCross = closes[last - 1] <= closes.sma(20, last - 1) && above20
        val higherLow = closes.subList(last - 4, last + 1).min() > closes.subList(last - 9, last - 4).min()

        val criteria: LinkedHashMap<String, Boolean>
        val performance: Double?
        val ongoing = p.mode == ScanMode.ONGOING_RECOVERY
        if (ongoing) {
            performance = null
            criteria = linkedMapOf(
                "Unter 12M-Kurs" to (perf12m != null && perf12m < 0.0),
                "Über SMA20" to above20,
                "Über SMA50" to above50,
                "Höheres Tief" to higherLow,
                "RSI im Band" to (p.rsiMin <= rsiNow && rsiNow <= p.rsiMax)
            )
        } else {
            performance = (price / closes[last - days] - 1) * 100
            criteria = linkedMapOf(
                "Perf-Kriterium" to (performance <= p.maxPerf),
                "RSI im Band" to (p.rsiMin <= rsiNow && rsiNow <= p.rsiMax),
                "Über SMA20" to above20,
                "Unter SMA50" to below50,
                "Volumen ok" to (relVolume >= p.minVolume),
                "RSI-Erholung" to rsiRecovery,
                "SMA20-Cross" to smaCross,
                "Höheres Tief" to higherLow
            )
        }

        return ScanRow(
            ticker = ticker,
            name = name,
            price = price,
            performance = performance,
            perf12m = perf12m,
            below12m = perf12m != null && perf12m < 0.0,
            rsi14 = rsiNow,
            relVolume = relVolume,
            sma20Pct = (price / sma20 - 1) * 100,
            sma50Pct = (price / sma50 - 1) * 100,
            criteria = criteria,
            score = criteria.values.count { it },
            lastTradingDay = bars[last].date.toString(),
            yahooChart = yahooUrl(ticker),
            yahooSummary = yahooSummaryUrl(ticker),
            google = googleFinanceUrl(ticker, exchange)
        )
    }

    private fun List<Double>.sma(window: Int, index: Int): Double = mean(subList(index - window + 1, index + 1))

    fun scan(
        universe: Universe,
        params: ScanParams,
        settings: ScanSettings,
        loader: (String) -> List<Bar>?
    ): ScanResult {
        val rows = mutableListOf<ScanRow>()
        val failed = mutableMapOf<String, FailedReason>()
        universe.tickers.forEach { ticker ->
            val bars = loader(ticker)
            val row = bars?.let { calcRow(ticker, universe.names[ticker] ?: "—", it, params, universe.exchanges[ticker] ?: "") }
            if (row == null) failed[ticker] = FailedReason.NO_DATA else rows.add(row)
        }
        return ScanResult(
            rows = rows.sortedWith(compareByDescending<ScanRow> { it.score }.thenByDescending { it.relVolume }),
            failed = failed,
            settings = settings
        )
    }
}

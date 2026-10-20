package de.luigialice.stockscreener.model

import kotlinx.datetime.LocalDate

enum class ScanMode(val label: String, val maxScore: Int) {
    REBOUND_START("Erholungsbeginn", 8),
    ONGOING_RECOVERY("Laufende Erholung", 5)
}

data class Bar(val date: LocalDate, val close: Double, val volume: Double)

data class ScanParams(
    val mode: ScanMode,
    val days: Int = 21,
    val maxPerf: Double = -12.0,
    val rsiMin: Double = 35.0,
    val rsiMax: Double = 48.0,
    val minVolume: Double = 1.3
)

/** Fester RSI-Bereich des Modus "Laufende Erholung" (nicht einstellbar). */
val ONGOING_RSI_BAND: ClosedFloatingPointRange<Double> = 45.0..65.0

/**
 * ScanParameter je Modus: für "Laufende Erholung" ist der RSI-Bereich fest
 * ([ONGOING_RSI_BAND]), die Regler des Rebound-Modus (Tage, max. Performance,
 * relatives Volumen) werden dort nicht verwendet.
 */
fun scanParams(
    mode: ScanMode,
    days: Int,
    maxPerf: Double,
    rsiMin: Double,
    rsiMax: Double,
    minVolume: Double,
): ScanParams = when (mode) {
    ScanMode.REBOUND_START ->
        ScanParams(mode, days, maxPerf, rsiMin, rsiMax, minVolume)
    ScanMode.ONGOING_RECOVERY ->
        ScanParams(mode, rsiMin = ONGOING_RSI_BAND.start, rsiMax = ONGOING_RSI_BAND.endInclusive)
}

data class ScanRow(
    val ticker: String,
    val name: String,
    val price: Double,
    val performance: Double?,
    val perf12m: Double?,
    val below12m: Boolean?,
    val rsi14: Double,
    val relVolume: Double,
    val sma20Pct: Double,
    val sma50Pct: Double,
    val criteria: Map<String, Boolean>,
    val score: Int,
    val lastTradingDay: String,
    val yahooChart: String,
    val yahooSummary: String,
    val google: String
)

data class ScanSettings(
    val scoreMin: Int,
    val onlyBelow12m: Boolean,
    val mode: ScanMode
)

enum class FailedReason { RATE_LIMITED, NETWORK_ERROR, NO_DATA }

data class ScanResult(
    val rows: List<ScanRow>,
    val failed: Map<String, FailedReason>,
    val settings: ScanSettings
)

data class Universe(
    val tickers: List<String>,
    val names: Map<String, String>,
    val exchanges: Map<String, String>
)

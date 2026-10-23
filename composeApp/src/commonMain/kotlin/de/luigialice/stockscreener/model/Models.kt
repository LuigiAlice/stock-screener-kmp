package de.luigialice.stockscreener.model

import kotlinx.datetime.LocalDate

enum class ScanMode(val label: String, val maxScore: Int) {
    REBOUND_START("Erholungsbeginn", 8),
    ONGOING_RECOVERY("Laufende Erholung", 5)
}

data class Bar(val date: LocalDate, val close: Double, val volume: Double)

data class ScanParams(
    val mode: ScanMode,
    val days: Int = 30,
    val maxPerf: Double = -8.0,
    val rsiMin: Double = 30.0,
    val rsiMax: Double = 60.0,
    val minVolume: Double = 1.0
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

/**
 * Startwert des Trefferfilters: entscheidet allein die Anzeige ([ScanResult.settings]),
 * der Scan selbst filtert nicht nach Score. Per Regler in den Ergebnissen verschiebbar.
 * Muss-Kriterien sind im Score bereits enthalten (3 Punkte), deshalb bewertet eine 4
 * "ein Zusatzkriterium mehr" statt "4 von 8".
 */
const val DEFAULT_SCORE_MIN: Int = 4

data class ScanSettings(
    val scoreMin: Int,
    val onlyBelow12m: Boolean,
    val mode: ScanMode,
    /**
     * Fenster der Spalte „Perf %" in Handelstagen (der Rückgangs-Regler) – damit die Tabelle
     * anzeigen kann, über welchen Zeitraum die Performance gerechnet ist. Ohne Performance
     * (Modus Laufende Erholung, CSV-Import) null, dann bleibt die Spalte bei „Perf %".
     */
    val perfDays: Int? = null
)

enum class FailedReason { RATE_LIMITED, NETWORK_ERROR, NO_DATA }

data class ScanResult(
    val rows: List<ScanRow>,
    val failed: Map<String, FailedReason>,
    val settings: ScanSettings
)

data class StockInfo(
    val name: String = "—",
    val exchange: String = ""
)

/** Ticker-keyed stock metadata; one entry keeps the universe fields together. */
data class Universe(val stocks: Map<String, StockInfo>) {
    val tickers: List<String> = stocks.keys.toList()
}

/**
 * Anzeigename: der aus dem Universum, sonst der aus den Kursdaten. Manuell eingegebene Ticker
 * (Default-Liste) haben keinen Namen – Yahoo liefert ihn in den Chart-Metadaten.
 */
fun StockInfo.displayName(fetched: String? = null): String =
    name.takeIf { it.isNotBlank() && it != "—" }
        ?: fetched?.takeIf { it.isNotBlank() }
        ?: name

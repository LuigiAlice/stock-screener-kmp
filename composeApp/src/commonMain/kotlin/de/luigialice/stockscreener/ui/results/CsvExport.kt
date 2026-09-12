package de.luigialice.stockscreener.ui.results

import de.luigialice.stockscreener.data.splitCsvLine
import de.luigialice.stockscreener.model.ScanMode
import de.luigialice.stockscreener.model.ScanResult
import de.luigialice.stockscreener.model.ScanRow
import de.luigialice.stockscreener.model.ScanSettings
import de.luigialice.stockscreener.ui.fmtDouble

private fun fmtCsv(v: Double, digits: Int = 2): String = fmtDouble(v, digits, '.', null)

private fun csvField(text: String): String = "\"${text.replace("\"", "\"\"")}\""

internal fun List<ScanRow>.toCsv(): String {
    val criteriaKeys = first().criteria.keys
    val rows = mutableListOf(
        (listOf("ticker", "name", "kurs", "perf", "perf12m", "rsi", "relvol", "sma20_pct", "sma50_pct", "score", "tag") +
            criteriaKeys.map { it.replace(" ", "_").lowercase() } + listOf("yahoo", "summary", "google")).joinToString(",")
    )
    forEach { row ->
        val values = listOf(
            csvField(row.ticker), csvField(row.name), fmtCsv(row.price), row.performance?.let { fmtCsv(it) } ?: "", row.perf12m?.let { fmtCsv(it) } ?: "",
            fmtCsv(row.rsi14, 1), fmtCsv(row.relVolume), fmtCsv(row.sma20Pct), fmtCsv(row.sma50Pct), row.score.toString(), row.lastTradingDay
        ) + criteriaKeys.map { if (row.criteria[it] == true) "Ja" else "Nein" } + listOf(row.yahooChart, row.yahooSummary, row.google)
        rows.add(values.joinToString(","))
    }
    return rows.joinToString("\n")
}

/**
 * Liest eine exportierte Treffer-CSV wieder ein und rekonstruiert ScanRow + ScanSettings.
 */
internal fun parseHitsCsv(text: String): List<ScanRow> {
    val lines = text.lines().filter { it.isNotBlank() }
    if (lines.isEmpty()) return emptyList()
    val header = splitCsvLine(lines[0]).map { it.trim().lowercase() }
    val idx = header.withIndex().associate { it.value to it.index }
    val yahooIdx = idx["yahoo"] ?: return emptyList()
    val tagIdx = idx["tag"] ?: return emptyList()
    val criteriaHeaders = header.subList(tagIdx + 1, yahooIdx)

    return lines.drop(1).mapNotNull { line ->
        val cols = splitCsvLine(line)
        if (cols.size < header.size) return@mapNotNull null
        ScanRow(
            ticker = cols[idx["ticker"] ?: 0],
            name = cols[idx["name"] ?: 1],
            price = cols[idx["kurs"] ?: 2].toDoubleOrNull() ?: 0.0,
            performance = cols[idx["perf"] ?: 3].toDoubleOrNull(),
            perf12m = cols[idx["perf12m"] ?: 4].toDoubleOrNull(),
            below12m = null,
            rsi14 = cols[idx["rsi"] ?: 5].toDoubleOrNull() ?: 0.0,
            relVolume = cols[idx["relvol"] ?: 6].toDoubleOrNull() ?: 0.0,
            sma20Pct = cols[idx["sma20_pct"] ?: 7].toDoubleOrNull() ?: 0.0,
            sma50Pct = cols[idx["sma50_pct"] ?: 8].toDoubleOrNull() ?: 0.0,
            criteria = criteriaHeaders.mapIndexed { i, h ->
                h.replace("_", " ") to cols[tagIdx + 1 + i].equals("Ja", true)
            }.toMap(),
            score = cols[idx["score"] ?: 9].toIntOrNull() ?: 0,
            lastTradingDay = cols[tagIdx],
            yahooChart = cols[yahooIdx],
            yahooSummary = cols[idx["summary"] ?: yahooIdx + 1],
            google = cols[idx["google"] ?: yahooIdx + 2]
        )
    }
}

internal fun buildImportedResult(rows: List<ScanRow>): ScanResult {
    val maxScore = rows.firstOrNull()?.criteria?.size ?: ScanMode.ONGOING_RECOVERY.maxScore
    val mode = if (maxScore == ScanMode.REBOUND_START.maxScore) ScanMode.REBOUND_START else ScanMode.ONGOING_RECOVERY
    return ScanResult(
        rows = rows.sortedWith(compareByDescending<ScanRow> { it.score }.thenByDescending { it.relVolume }),
        failed = emptyList(),
        settings = ScanSettings(scoreMin = 1, onlyBelow12m = false, mode = mode)
    )
}

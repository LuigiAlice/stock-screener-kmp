package de.luigialice.stockscreener.data

import de.luigialice.stockscreener.model.Universe
import de.luigialice.stockscreener.screener.parseTickers

object CsvUniverse {

    fun parse(text: String): Universe {
        val lines = text.split("\n").map { it.trim('\r') }.filter { it.isNotBlank() }
        val headerIndex = lines.indexOfFirst { it.startsWith("ticker,") || it.startsWith("symbol,") }
        if (headerIndex < 0) error("CSV braucht eine Headerzeile mit `ticker` oder `symbol`.")
        val header = splitCsvLine(lines[headerIndex]).map { it.trim().lowercase() }
        val tickerCol = header.indexOfFirst { it == "ticker" || it == "symbol" }
        val nameCol = header.indexOfFirst { it in setOf("name", "company", "unternehmen", "aktienname") }
        val exchangeCol = header.indexOfFirst { it == "exchange" }

        val tickers = mutableListOf<String>()
        val names = mutableMapOf<String, String>()
        val exchanges = mutableMapOf<String, String>()
        for (line in lines.subList(headerIndex + 1, lines.size)) {
            val cells = splitCsvLine(line)
            val raw = cells.getOrNull(tickerCol)?.trim() ?: continue
            val ticker = parseTickers(raw.ifEmpty { "" }).firstOrNull() ?: continue
            if (ticker in names) continue
            tickers.add(ticker)
            names[ticker] = nameCol.takeIf { it >= 0 }?.let { cells.getOrNull(it)?.trim().orEmpty().ifEmpty { "—" } } ?: "—"
            exchanges[ticker] = exchangeCol.takeIf { it >= 0 }?.let { cells.getOrNull(it)?.trim().orEmpty() } ?: ""
        }
        return Universe(tickers, names, exchanges)
    }

    private fun splitCsvLine(line: String): List<String> {
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        for (char in line) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> { cells.add(current.toString()); current.clear() }
                else -> current.append(char)
            }
        }
        cells.add(current.toString())
        return cells
    }
}

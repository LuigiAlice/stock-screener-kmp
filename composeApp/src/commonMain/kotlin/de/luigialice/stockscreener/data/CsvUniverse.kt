package de.luigialice.stockscreener.data

import de.luigialice.stockscreener.model.StockInfo
import de.luigialice.stockscreener.model.Universe
import de.luigialice.stockscreener.screener.parseTickers

object CsvUniverse {

    fun parse(text: String): Universe {
        val lines = text.split("\n").map { it.trim('\r') }.filter { it.isNotBlank() }
        val headerIndex = lines.indexOfFirst { line ->
            splitCsvLine(line.removePrefix("\uFEFF"))
                .any { it.trim().lowercase() in setOf("ticker", "symbol") }
        }
        if (headerIndex < 0) error("CSV braucht eine Headerzeile mit `ticker` oder `symbol`.")
        val header = splitCsvLine(lines[headerIndex].removePrefix("\uFEFF")).map { it.trim().lowercase() }
        val tickerCol = header.indexOfFirst { it == "ticker" || it == "symbol" }
        val nameCol = header.indexOfFirst { it in setOf("name", "company", "unternehmen", "aktienname") }
        val exchangeCol = header.indexOfFirst { it == "exchange" }

        val stocks = linkedMapOf<String, StockInfo>()
        for (line in lines.subList(headerIndex + 1, lines.size)) {
            val cells = splitCsvLine(line)
            val raw = cells.getOrNull(tickerCol)?.trim() ?: continue
            val ticker = parseTickers(raw.ifEmpty { "" }).firstOrNull() ?: continue
            if (ticker in stocks) continue
            val name = nameCol.takeIf { it >= 0 }?.let { cells.getOrNull(it)?.trim().orEmpty().ifEmpty { "—" } } ?: "—"
            val exchange = exchangeCol.takeIf { it >= 0 }?.let { cells.getOrNull(it)?.trim().orEmpty() } ?: ""
            stocks[ticker] = StockInfo(name, exchange)
        }
        return Universe(stocks)
    }
}

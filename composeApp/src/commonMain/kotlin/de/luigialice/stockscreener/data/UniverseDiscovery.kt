package de.luigialice.stockscreener.data

import de.luigialice.stockscreener.createYahooClient
import de.luigialice.stockscreener.model.Universe
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

private val skipNameRegex = Regex(
    "\\b(warrants?|rights?|units?|preferred|depositary|notes?|debentures?)\\b",
    RegexOption.IGNORE_CASE
)

/**
 * Ermittelt automatisch ein US-Aktien-Universum aus den offiziellen
 * NASDAQ-Trader-Symboldateien (NASDAQ + NYSE/AMEX, ohne ETFs, Warrants etc.).
 */
class UniverseDiscovery(private val client: HttpClient = createYahooClient()) {

    suspend fun discoverUniverse(): Universe {
        val entries = (
            parseNasdaqListed(fetchLines("https://www.nasdaqtrader.com/dynamic/SymDir/nasdaqlisted.txt")) +
                parseOtherListed(fetchLines("https://www.nasdaqtrader.com/dynamic/SymDir/otherlisted.txt"))
            )
            .toMap() // Deduplizierung nach Ticker
            .filter { (symbol, name) -> isCommonStock(symbol, name) }

        if (entries.isEmpty()) error("Keine Aktien gefunden")

        return Universe(
            tickers = entries.keys.sorted(),
            names = entries,
            exchanges = entries.mapValues { "" }
        )
    }

    /**
     * S&P-500-Konstituenten aus dem DataHub-Datensatz (Symbol, Name, Sektor).
     */
    suspend fun discoverSp500(): Universe {
        val entries = parseSp500Csv(
            fetchLines("https://raw.githubusercontent.com/datasets/s-and-p-500-companies/main/data/constituents.csv")
        ).toMap()

        if (entries.isEmpty()) error("Keine S&P-500-Aktien gefunden")

        return Universe(
            tickers = entries.keys.sorted(),
            names = entries,
            exchanges = entries.mapValues { "" }
        )
    }

    private suspend fun fetchLines(url: String): List<String> {
        val response = client.get(url) { header("User-Agent", "Mozilla/5.0") }
        if (!response.status.isSuccess()) error("HTTP ${response.status.value} für $url")
        return response.bodyAsText().lines()
    }
}

/** Yahoo-Format: Klassenshares "BRK.B" -> "BRK-B" */
internal fun symbolToYahoo(symbol: String): String = symbol.replace(".", "-")

/** Spalten: Symbol|Security Name|Market Category|Test Issue|Financial Status|Round Lot Size|ETF|NextShares */
internal fun parseNasdaqListed(lines: List<String>): List<Pair<String, String>> =
    lines.drop(1).mapNotNull { line ->
        val cols = line.split("|")
        if (cols.size < 8 || cols[0].isBlank() || line.startsWith("#")) return@mapNotNull null
        if (cols[3].equals("Y", true) || cols[6].equals("Y", true) || cols[7].equals("Y", true)) return@mapNotNull null
        symbolToYahoo(cols[0].trim()) to cols[1].trim()
    }

/** Spalten: ACT Symbol|Security Name|Exchange|CQS Symbol|ETF|Round Lot Size|Test Issue|NASDAQ Symbol */
internal fun parseOtherListed(lines: List<String>): List<Pair<String, String>> =
    lines.drop(1).mapNotNull { line ->
        val cols = line.split("|")
        if (cols.size < 8 || cols[0].isBlank() || line.startsWith("#")) return@mapNotNull null
        if (cols[4].equals("Y", true) || cols[6].equals("Y", true)) return@mapNotNull null
        symbolToYahoo(cols[0].trim()) to cols[1].trim()
    }

internal fun isCommonStock(symbol: String, name: String): Boolean {
    if (symbol.length > 5) return false
    if (symbol.any { !(it in 'A'..'Z' || it in '0'..'9' || it == '-') }) return false
    if (skipNameRegex.containsMatchIn(name)) return false
    return true
}

/** CSV mit Headern: Symbol,Name,Sector[,...]; Namen können in Anführungszeichen stehen. */
internal fun parseSp500Csv(lines: List<String>): List<Pair<String, String>> =
    lines.drop(1).mapNotNull { line ->
        if (line.isBlank()) return@mapNotNull null
        val cols = splitCsvLine(line)
        if (cols.size < 2) return@mapNotNull null
        val symbol = cols[0].trim()
        if (symbol.isEmpty() || symbol.startsWith("#")) return@mapNotNull null
        symbolToYahoo(symbol) to cols[1].trim()
    }

internal fun splitCsvLine(line: String): List<String> {
    val cols = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < line.length) {
        val c = line[i]
        when {
            c == '"' -> {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    current.append('"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            }
            c == ',' && !inQuotes -> {
                cols.add(current.toString())
                current.clear()
            }
            else -> current.append(c)
        }
        i++
    }
    cols.add(current.toString())
    return cols
}

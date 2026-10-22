package de.luigialice.stockscreener.data

import de.luigialice.stockscreener.createYahooClient
import de.luigialice.stockscreener.model.StockInfo
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

private fun universeFromNames(names: Map<String, String>): Universe =
    Universe(names.entries.sortedBy { it.key }.associate { (ticker, name) -> ticker to StockInfo(name) })

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

        return universeFromNames(entries)
    }

    /**
     * S&P-500-Konstituenten aus dem DataHub-Datensatz (Symbol, Name, Sektor).
     */
    suspend fun discoverSp500(): Universe {
        val entries = parseSp500Csv(
            fetchLines("https://raw.githubusercontent.com/datasets/s-and-p-500-companies/main/data/constituents.csv")
        ).toMap()

        if (entries.isEmpty()) error("Keine S&P-500-Aktien gefunden")

        return universeFromNames(entries)
    }

    /** Nasdaq-100 (US, ohne Suffix); Komponentenliste ist ein eigener Artikel. */
    suspend fun discoverNasdaq100(): Universe =
        discoverWikipedia("https://en.wikipedia.org/wiki/List_of_NASDAQ-100_companies", "", "Nasdaq 100")

    /** Dow Jones Industrial Average (US, ohne Suffix); Komponentenliste ist ein eigener Artikel. */
    suspend fun discoverDowJones(): Universe =
        discoverWikipedia("https://en.wikipedia.org/wiki/List_of_Dow_Jones_Industrial_Average_companies", "", "Dow Jones")

    /** DAX (XETRA, Yahoo-Suffix .DE). */
    suspend fun discoverDax(): Universe =
        discoverWikipedia("https://en.wikipedia.org/wiki/DAX", ".DE", "DAX")

    /** MDAX (XETRA, Yahoo-Suffix .DE). */
    suspend fun discoverMdax(): Universe =
        discoverWikipedia("https://en.wikipedia.org/wiki/MDAX", ".DE", "MDAX")

    private suspend fun discoverWikipedia(url: String, suffix: String, label: String): Universe {
        val entries = parseIndexTable(fetchText(url))
            .map { (symbol, name) -> indexSymbol(symbol, suffix) to name }
            .toMap()

        if (entries.isEmpty()) error("Keine Aktien für $label gefunden")

        return universeFromNames(entries)
    }

    private suspend fun fetchLines(url: String): List<String> = fetchText(url).lines()

    private suspend fun fetchText(url: String): String {
        val response = client.get(url) { header("User-Agent", "Mozilla/5.0") }
        if (!response.status.isSuccess()) error("HTTP ${response.status.value} für $url")
        return response.bodyAsText()
    }
}

/** Yahoo-Format: Klassenshares "BRK.B" -> "BRK-B" */
internal fun symbolToYahoo(symbol: String): String = symbol.replace(".", "-")

/**
 * Index-Ticker + Börsensuffix: "ADS" + ".DE" -> "ADS.DE";
 * enthält der Ticker schon einen Punkt (z. B. "SAP.DE"), bleibt er unverändert.
 */
internal fun indexSymbol(symbol: String, suffix: String): String =
    if (symbol.contains('.') || suffix.isEmpty()) symbol else symbol + suffix

private val tagsRegex = Regex("<[^>]*>")
private val refRegex = Regex("\\[\\s*\\d+\\s*]")
private val cellRegex = Regex("<t([dh])[^>]*>([\\s\\S]*?)</t\\1>", RegexOption.IGNORE_CASE)
private val rowRegex = Regex("<tr[^>]*>([\\s\\S]*?)</tr>", RegexOption.IGNORE_CASE)
private val tableRegex = Regex("<table[^>]*>([\\s\\S]*?)</table>", RegexOption.IGNORE_CASE)

private fun cleanCell(html: String): String =
    html.replace(tagsRegex, " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&#160;", " ")
        .replace(refRegex, "")
        .replace(Regex("\\s+"), " ")
        .trim()

/**
 * Liest aus einer Wikipedia-Seite die erste Tabelle mit Ticker-/Symbol-Spalte
 * und liefert (Ticker, Firmenname)-Paare.
 */
internal fun parseIndexTable(html: String, symbolKeywords: List<String> = listOf("ticker", "symbol")): List<Pair<String, String>> {
    for (table in tableRegex.findAll(html)) {
        val rows = rowRegex.findAll(table.groupValues[1]).map { it.groupValues[1] }.toList()
        if (rows.size < 5) continue

        val header = cellRegex.findAll(rows.first()).map { cleanCell(it.groupValues[2]) }.toList()
        val symbolIdx = header.indexOfFirst { h -> symbolKeywords.any { h.lowercase().contains(it) } }
        if (symbolIdx < 0) continue
        val nameIdx = header.indexOfFirst { h ->
            val l = h.lowercase()
            l.contains("company") || l.contains("name") || l.contains("unternehmen")
        }.takeIf { it >= 0 } ?: 0

        val entries = rows.drop(1).mapNotNull { row ->
            val cells = cellRegex.findAll(row).map { cleanCell(it.groupValues[2]) }.toList()
            if (cells.size <= maxOf(symbolIdx, nameIdx)) return@mapNotNull null
            val symbol = cells[symbolIdx]
            val name = cells[nameIdx]
            if (symbol.isBlank() || name.isBlank()) return@mapNotNull null
            symbol to name
        }
        val distinct = entries.distinctBy { it.first }
        if (distinct.size >= 5) return distinct
    }
    return emptyList()
}

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

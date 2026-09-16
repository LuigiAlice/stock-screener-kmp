package de.luigialice.stockscreener

import de.luigialice.stockscreener.data.indexSymbol
import de.luigialice.stockscreener.data.isCommonStock
import de.luigialice.stockscreener.data.parseIndexTable
import de.luigialice.stockscreener.data.parseNasdaqListed
import de.luigialice.stockscreener.data.parseOtherListed
import de.luigialice.stockscreener.data.parseSp500Csv
import de.luigialice.stockscreener.data.splitCsvLine
import de.luigialice.stockscreener.data.symbolToYahoo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UniverseDiscoveryTest {

    private val nasdaqLines = listOf(
        "Symbol|Security Name|Market Category|Test Issue|Financial Status|Round Lot Size|ETF|NextShares",
        "AAPL|Apple Inc. Common Stock|Q|N|Q|100|N|N",
        "BRK.B|Berkshire Hathaway Inc. Class B|N|N|Q|100|N|N",
        "TSTW|Test Company Warrant|Q|N|Q|100|N|N",
        "TEST|Test Issue Company|Q|Y|Q|100|N|N",
        "VOO|Vanguard S&P 500 ETF|Q|N|Q|100|Y|N",
        "# File Creation Time: 01012024"
    )

    private val otherLines = listOf(
        "ACT Symbol|Security Name|Exchange|CQS Symbol|ETF|Round Lot Size|Test Issue|NASDAQ Symbol",
        "GE|General Electric Company|N|GE|N|100|N|GE",
        "ABC-P|Preferred Test Inc|N|ABC-P|N|100|N|ABC-P",
        "WRNT|Warrant Test Company|A|WRNT|N|100|N|WRNT",
        "XNTK|NASDAQ Internet ETF|P|XNTK|Y|100|N|XNTK"
    )

    @Test
    fun parsesNasdaqListedAndSkipsTestIssuesEtfAndFooter() {
        val parsed = parseNasdaqListed(nasdaqLines)
        assertEquals(listOf("AAPL", "BRK-B", "TSTW"), parsed.map { it.first })
        assertEquals("Apple Inc. Common Stock", parsed[0].second)
    }

    @Test
    fun parsesOtherListedAndSkipsEtf() {
        val parsed = parseOtherListed(otherLines)
        assertEquals(listOf("GE", "ABC-P", "WRNT"), parsed.map { it.first })
    }

    @Test
    fun convertsClassShareDotToYahooDash() {
        assertEquals("BRK-B", symbolToYahoo("BRK.B"))
        assertEquals("GE", symbolToYahoo("GE"))
    }

    @Test
    fun parsesSp500CsvWithQuotedNames() {
        val csv = listOf(
            "Symbol,Name,Sector",
            "MMM,3M Company,Industrials",
            "\"BRK.B\",\"Berkshire Hathaway Inc.\",Financials",
            "",
            "# comment line"
        )
        val parsed = parseSp500Csv(csv)
        assertEquals(listOf("MMM" to "3M Company", "BRK-B" to "Berkshire Hathaway Inc."), parsed)
    }

    @Test
    fun splitCsvLineHandlesQuotesAndEscapes() {
        assertEquals(listOf("a", "b,c", "d\"e"), splitCsvLine("a,\"b,c\",\"d\"\"e\""))
    }

    @Test
    fun commonStockFilterKeepsStocksDropsDerivatives() {
        assertTrue(isCommonStock("AAPL", "Apple Inc. Common Stock"))
        assertTrue(isCommonStock("BRK-B", "Berkshire Hathaway Inc. Class B"))
        assertFalse(isCommonStock("TSTW", "Test Company Warrant"))
        assertFalse(isCommonStock("WRNT", "Warrant Test Company"))
        assertFalse(isCommonStock("ABC-P", "Preferred Test Inc"))
        assertFalse(isCommonStock("TOOLONG", "Too Long Name Inc"))
        assertFalse(isCommonStock("A\$B", "Dollar Test Inc"))
    }

    @Test
    fun parsesIndexTableFromWikipediaHtml() {
        // Struktur wie auf Wikipedia: Tabelle mit Ticker-/Firmenspalte, Fußnoten, Entitäten
        val html = """
            <table><tr><td>andere Tabelle ohne Symbolspalte</td></tr></table>
            <table class="wikitable">
              <tr><th id="a">Ticker</th><th id="b">Company</th><th id="c">Sector</th></tr>
              <tr><td>ADS</td><td><a href="/wiki/Adidas">Adidas</a></td><td>Consumer</td></tr>
              <tr><td>AIR.PA</td><td>Airbus<sup>[1]</sup></td><td>Industrials</td></tr>
              <tr><td>ALV</td><td>Allianz SE</td><td>Financials</td></tr>
              <tr><td>MRK</td><td>Merck KGaA &amp; Co.</td><td>Healthcare</td></tr>
              <tr><td>RHM</td><td>Rheinmetall</td><td>Industrials</td></tr>
            </table>
        """.trimIndent()
        val parsed = parseIndexTable(html)
        assertEquals(5, parsed.size)
        assertEquals("ADS" to "Adidas", parsed[0])
        assertEquals("AIR.PA" to "Airbus", parsed[1])
        assertEquals("Merck KGaA & Co.", parsed[3].second)
    }

    @Test
    fun indexSymbolAppendsExchangeSuffixOnlyWhenMissing() {
        assertEquals("ADS.DE", indexSymbol("ADS", ".DE"))
        assertEquals("SAP.DE", indexSymbol("SAP.DE", ".DE")) // bereits vollständig
        assertEquals("AIR.PA", indexSymbol("AIR.PA", ".DE")) // fremde Börse bleibt
        assertEquals("AAPL", indexSymbol("AAPL", ""))         // US ohne Suffix
    }

    @Test
    fun parseIndexTableIgnoresTablesWithoutSymbolColumn() {
        val html = """
            <table>
              <tr><th>Logo</th><th>Name</th><th>Industry</th></tr>
              <tr><td>x</td><td>A</td><td>B</td></tr>
              <tr><td>x</td><td>C</td><td>D</td></tr>
              <tr><td>x</td><td>E</td><td>F</td></tr>
              <tr><td>x</td><td>G</td><td>H</td></tr>
              <tr><td>x</td><td>I</td><td>J</td></tr>
            </table>
        """.trimIndent()
        assertTrue(parseIndexTable(html).isEmpty())
    }
}

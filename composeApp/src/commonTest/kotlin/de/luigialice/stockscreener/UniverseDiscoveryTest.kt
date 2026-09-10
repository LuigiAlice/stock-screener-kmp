package de.luigialice.stockscreener

import de.luigialice.stockscreener.data.isCommonStock
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
}

package de.luigialice.stockscreener

import de.luigialice.stockscreener.data.parseChartResponse
import de.luigialice.stockscreener.model.StockInfo
import de.luigialice.stockscreener.model.displayName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Aktiennamen für Universen ohne Namen (manuell eingegebene Ticker, Default-Liste):
 * Yahoo liefert longName/shortName in den Chart-Metadaten.
 */
class StockNameTest {

    private val chartJson = """
        {"chart":{"result":[{"meta":{"symbol":"AAPL","longName":"Apple Inc.","shortName":"Apple"},
        "timestamp":[1761177600,1761264000],"indicators":{"quote":[
        {"close":[250.1,251.2],"volume":[1000,2000]}]}},{"meta":{"symbol":"X"}}]}}
    """.trimIndent()

    @Test
    fun nameComesFromTheChartMeta() {
        val fetched = parseChartResponse(chartJson)
        assertEquals("Apple Inc.", fetched.name)
        assertEquals(2, fetched.bars.size)
    }

    @Test
    fun shortNameIsTheFallback() {
        val json = """{"chart":{"result":[{"meta":{"symbol":"X","shortName":"Fallback AG"},
            "timestamp":[1],"indicators":{"quote":[{"close":[1.0],"volume":[10]}]}}]}}"""
        assertEquals("Fallback AG", parseChartResponse(json).name)
    }

    @Test
    fun missingMetaYieldsNoName() {
        val json = """{"chart":{"result":[{"meta":{"symbol":"X"},
            "timestamp":[1],"indicators":{"quote":[{"close":[1.0],"volume":[10]}]}}]}}"""
        assertNull(parseChartResponse(json).name)
    }

    @Test
    fun emptyChartYieldsNothing() {
        val fetched = parseChartResponse("""{"chart":{"result":[]}}""")
        assertNull(fetched.name)
        assertEquals(0, fetched.bars.size)
    }

    // --- Auflösung des Anzeigenamens ---

    @Test
    fun universeNameWinsOverTheFetchedName() {
        assertEquals("Deutsche Telekom AG", StockInfo("Deutsche Telekom AG").displayName("T-Online"))
    }

    @Test
    fun placeholderNameFallsBackToTheFetchedName() {
        assertEquals("Apple Inc.", StockInfo("—").displayName("Apple Inc."))
        assertEquals("Apple Inc.", StockInfo("").displayName("Apple Inc."))
    }

    @Test
    fun withoutAnyNameThePlaceholderStays() {
        assertEquals("—", StockInfo("—").displayName(null))
        assertEquals("—", StockInfo("—").displayName("  "))
    }
}

package de.luigialice.stockscreener

import de.luigialice.stockscreener.screener.googleFinanceUrl
import de.luigialice.stockscreener.screener.yahooSummaryUrl
import de.luigialice.stockscreener.screener.yahooUrl
import kotlin.test.Test
import kotlin.test.assertEquals

class LinksTest {

    @Test
    fun yahooChartAndSummary() {
        assertEquals("https://finance.yahoo.com/quote/SAP.DE/chart/?range=1y", yahooUrl("SAP.DE"))
        assertEquals("https://finance.yahoo.com/quote/SAP.DE", yahooSummaryUrl("SAP.DE"))
    }

    @Test
    fun googleFinanceExchangeMapping() {
        assertEquals("https://www.google.com/finance/quote/7974:TYO", googleFinanceUrl("7974.T"))
        assertEquals("https://www.google.com/finance/quote/SAP:ETR", googleFinanceUrl("SAP.DE"))
        assertEquals("https://www.google.com/finance/quote/VOW3:ETR", googleFinanceUrl("VOW3.DE"))
        assertEquals("https://www.google.com/finance/quote/AZN:AMS", googleFinanceUrl("AZN.AS"))
        assertEquals("https://www.google.com/finance/quote/ENEL:BIT", googleFinanceUrl("ENEL.MI"))
        assertEquals("https://www.google.com/finance/quote/TEVA:TLV", googleFinanceUrl("TEVA.TA"))
    }

    @Test
    fun googleFinanceUsExchange() {
        assertEquals("https://www.google.com/finance/quote/AAPL:NASDAQ", googleFinanceUrl("AAPL", "NASDAQ"))
        assertEquals("https://www.google.com/finance/quote/BRK.B:NYSE", googleFinanceUrl("BRK-B", "NYSE"))
    }

    @Test
    fun googleFinanceFallbackIsSearch() {
        assertEquals("https://www.google.com/search?q=AAPL+stock+quote", googleFinanceUrl("AAPL"))
        assertEquals("https://www.google.com/search?q=AAPL+stock+quote", googleFinanceUrl("AAPL", "OTC Markets"))
    }
}

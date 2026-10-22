package de.luigialice.stockscreener.screener

val GOOGLE_EXCHANGES = mapOf(
    "DE" to "ETR", "AS" to "AMS", "L" to "LON", "PA" to "EPA", "MI" to "BIT",
    "MC" to "BME", "BR" to "EBR", "SW" to "SWX", "TO" to "TSE", "T" to "TYO",
    "KS" to "KRX", "TW" to "TPE", "HK" to "HKG", "NS" to "NSE", "SS" to "SHA",
    "SZ" to "SHE", "AX" to "ASX", "CO" to "CPH", "HE" to "HEL", "TA" to "TLV"
)

val GOOGLE_US_EXCHANGES = mapOf(
    "NASDAQ" to "NASDAQ", "NYSE" to "NYSE", "NYSE American" to "NYSEMKT", "NYSE Arca" to "NYSEARCA"
)

fun yahooUrl(ticker: String) = "https://finance.yahoo.com/quote/$ticker/chart/?range=1y"

fun yahooSummaryUrl(ticker: String) = "https://finance.yahoo.com/quote/$ticker"

fun googleFinanceUrl(ticker: String, exchange: String = ""): String {
    val parts = ticker.split(".")
    if (parts.size == 2) {
        val suffix = parts[1]
        if (suffix in GOOGLE_EXCHANGES) return "https://www.google.com/finance/quote/${parts[0]}:${GOOGLE_EXCHANGES[suffix]}"
    }
    if (exchange in GOOGLE_US_EXCHANGES) {
        val quote = ticker.replace("-", ".")
        return "https://www.google.com/finance/quote/$quote:${GOOGLE_US_EXCHANGES[exchange]}"
    }
    return "https://www.google.com/search?q=$ticker+stock+quote"
}

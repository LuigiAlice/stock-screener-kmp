package de.luigialice.stockscreener.screener

private val EXCHANGE_SUFFIXES = setOf(
    "DE", "AS", "L", "PA", "MI", "MC", "BR", "SW", "TO", "T", "KS",
    "TW", "HK", "NS", "SS", "SZ", "AX", "OL", "CO", "HE", "TA"
)

fun normalizeTicker(value: String): String {
    val ticker = value.trim().uppercase()
    if (ticker.isEmpty() || ticker == "NAN") return ""
    val parts = ticker.split(".")
    if (parts.size == 2 && parts[1] in EXCHANGE_SUFFIXES) return ticker
    return ticker.replace(".", "-")
}

fun parseTickers(text: String): List<String> =
    text.replace(",", " ")
        .replace(";", " ")
        .split(Regex("\\s+"))
        .map(::normalizeTicker)
        .filter { it.isNotEmpty() }
        .distinct()

val DEFAULT_TICKERS = """SAP.DE SIE.DE ALV.DE DTE.DE BAS.DE MBG.DE BMW.DE VOW3.DE IFX.DE RWE.DE
AAPL MSFT NVDA AMZN GOOGL META TSLA AMD NFLX JPM V MA COST AVGO LLY"""

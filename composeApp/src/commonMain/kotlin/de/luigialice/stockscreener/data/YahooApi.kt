package de.luigialice.stockscreener.data

import de.luigialice.stockscreener.createYahooClient
import de.luigialice.stockscreener.model.Bar
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.ExperimentalTime

private val json = Json { ignoreUnknownKeys = true }
private val utc = TimeZone.UTC

@Serializable
private data class ChartEnvelope(val chart: Chart)

@Serializable
private data class Chart(val result: List<ChartResult> = emptyList())

@Serializable
private data class ChartResult(val timestamp: List<Double?> = emptyList(), val indicators: Indicators)

@Serializable
private data class Indicators(val quote: List<Quote> = emptyList())

@Serializable
private data class Quote(val close: List<Double?> = emptyList(), val volume: List<Double?> = emptyList())

class YahooApi(private val client: HttpClient = createYahooClient()) {

    @OptIn(ExperimentalTime::class)
    suspend fun fetchBars(ticker: String, range: String = "2y"): List<Bar> {
        val url = "https://query1.finance.yahoo.com/v8/finance/chart/$ticker?range=$range&interval=1d"
        val response = client.get(url) { header("User-Agent", "Mozilla/5.0") }
        if (!response.status.isSuccess()) error("Yahoo HTTP ${response.status.value} für $ticker")
        val envelope = json.decodeFromString(ChartEnvelope.serializer(), response.bodyAsText())
        val result = envelope.chart.result.firstOrNull() ?: return emptyList()
        val quote = result.indicators.quote.firstOrNull() ?: return emptyList()
        val bars = mutableListOf<Bar>()
        for (i in result.timestamp.indices) {
            val ts = result.timestamp[i] ?: continue
            val close = quote.close.getOrNull(i) ?: continue
            val volume = quote.volume.getOrNull(i) ?: continue
            val date = Instant.fromEpochSeconds(ts.toLong()).toLocalDateTime(utc).date
            bars.add(Bar(date, close, volume))
        }
        return bars
    }
}

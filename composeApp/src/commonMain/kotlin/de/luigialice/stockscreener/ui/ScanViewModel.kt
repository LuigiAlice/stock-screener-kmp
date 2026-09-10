package de.luigialice.stockscreener.ui

import de.luigialice.stockscreener.data.YahooApi
import de.luigialice.stockscreener.model.Bar
import de.luigialice.stockscreener.model.ScanParams
import de.luigialice.stockscreener.model.ScanResult
import de.luigialice.stockscreener.model.ScanRow
import de.luigialice.stockscreener.model.ScanSettings
import de.luigialice.stockscreener.model.Universe
import de.luigialice.stockscreener.screener.Screener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ScanState {
    data object Idle : ScanState
    data class Running(val done: Int, val total: Int, val current: String) : ScanState
    data class Done(
        val result: ScanResult,
        val scanned: Int = 0,
        val total: Int = 0
    ) : ScanState
    data class Failed(val message: String) : ScanState
}

expect abstract class PlatformViewModel()

val ScanState.isScanning: Boolean
    get() = this is ScanState.Running || (this is ScanState.Done && scanned < total)

class ScanViewModel(private val api: YahooApi = YahooApi()) : PlatformViewModel() {

    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    val state: StateFlow<ScanState> = _state
    private val prices = MutableStateFlow<Map<String, List<Bar>>>(emptyMap())
    private val barJobs = mutableMapOf<String, Job>()
    private var job: Job? = null

    fun cancel() {
        job?.cancel()
        _state.value = ScanState.Idle
    }

    /** Ergebnissatz aus importierter Treffer-CSV anzeigen. */
    fun loadResults(result: ScanResult) {
        job?.cancel()
        prices.value = emptyMap()
        val total = result.rows.size + result.failed.size
        _state.value = ScanState.Done(result, scanned = total, total = total)
    }

    /** Bars für einen Ticker nachladen (z. B. nach Treffer-CSV-Import). */
    fun loadBars(ticker: String, scope: CoroutineScope) {
        if (prices.value[ticker] != null || barJobs[ticker]?.isActive == true) return
        barJobs[ticker] = scope.launch {
            try {
                val bars = withContext(Dispatchers.Default) { runCatching { api.fetchBars(ticker) }.getOrNull() }
                if (!bars.isNullOrEmpty()) prices.value = prices.value + (ticker to bars)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // ignorieren: Chart bleibt leer
            } finally {
                barJobs.remove(ticker)
            }
        }
    }

    fun runScan(
        universe: Universe,
        params: ScanParams,
        settings: ScanSettings,
        batchSize: Int,
        pauseSeconds: Int,
        scope: CoroutineScope
    ) {
        job?.cancel()
        _state.value = ScanState.Running(0, universe.tickers.size, "")
        prices.value = emptyMap()
        job = scope.launch {
            try {
                val rows = mutableListOf<ScanRow>()
                val failed = mutableListOf<String>()
                val loadedPrices = mutableMapOf<String, List<Bar>>()
                val total = universe.tickers.size

                universe.tickers.chunked(batchSize).forEachIndexed { batchIndex, batch ->
                    // Ticker eines Batches parallel laden; Batch-Größe = Parallelität
                    val batchResults: List<Pair<String, List<Bar>?>> = batch.map { ticker ->
                        async {
                            try {
                                ticker to withContext(Dispatchers.Default) { api.fetchBars(ticker) }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                ticker to null
                            }
                        }
                    }.awaitAll()

                    batchResults.forEach { (ticker, bars) ->
                        if (bars.isNullOrEmpty()) {
                            failed.add(ticker)
                        } else {
                            val row = Screener.calcRow(ticker, universe.names[ticker] ?: "—", bars, params, universe.exchanges[ticker] ?: "")
                            if (row == null) failed.add(ticker) else { rows.add(row); loadedPrices[ticker] = bars }
                        }
                    }

                    // Inkrementelles Zwischenergebnis nach jedem Batch
                    prices.value = loadedPrices.toMap()
                    _state.value = ScanState.Done(
                        ScanResult(
                            rows = rows.sortedWith(compareByDescending<ScanRow> { it.score }.thenByDescending { it.relVolume }),
                            failed = failed.toList(),
                            settings = settings
                        ),
                        scanned = rows.size + failed.size,
                        total = total
                    )

                    if (pauseSeconds > 0 && batchIndex < (total + batchSize - 1) / batchSize - 1) {
                        delay(pauseSeconds * 1000L)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = ScanState.Failed(e.message ?: "Unbekannter Fehler")
            }
        }
    }

    /** Bars als beobachtbaren State (recomponiert beim Nachladen). */
    @Composable
    fun rememberBars(ticker: String): List<Bar> = prices.collectAsState().value[ticker] ?: emptyList()
}

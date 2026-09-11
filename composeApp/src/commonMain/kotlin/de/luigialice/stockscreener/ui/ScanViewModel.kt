package de.luigialice.stockscreener.ui

import de.luigialice.stockscreener.data.BarCache
import de.luigialice.stockscreener.data.YahooApi
import de.luigialice.stockscreener.data.barCacheDirectory
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

class ScanViewModel(
    private val api: YahooApi = YahooApi(),
    private val barCache: BarCache = BarCache(barCacheDirectory())
) : PlatformViewModel() {

    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    val state: StateFlow<ScanState> = _state
    private val _universe = MutableStateFlow<Universe?>(null)
    val universe: StateFlow<Universe?> = _universe
    private val prices = MutableStateFlow<Map<String, List<Bar>>>(emptyMap())
    private val barJobs = mutableMapOf<String, Job>()
    private var job: Job? = null
    private var lastUniverse: Universe? = null
    private var lastApplied: Pair<ScanParams, ScanSettings>? = null

    fun cancel() {
        job?.cancel()
        _state.value = ScanState.Idle
    }

    /** Universum setzen (CSV, automatische Ermittlung). */
    fun setUniverse(u: Universe?) {
        _universe.value = u
    }

    /** Ergebnissatz aus importierter Treffer-CSV anzeigen; Universum wird für Re-Score übernommen. */
    fun loadResults(result: ScanResult) {
        job?.cancel()
        prices.value = emptyMap()
        val rows = result.rows
        val uni = Universe(
            tickers = rows.map { it.ticker }.distinct(),
            names = rows.associate { it.ticker to it.name },
            exchanges = rows.associate { it.ticker to "" }
        )
        _universe.value = uni
        lastUniverse = uni
        lastApplied = null
        val total = rows.size + result.failed.size
        _state.value = ScanState.Done(result, scanned = total, total = total)
    }

    /** Bars für einen Ticker nachladen (z. B. nach Treffer-CSV-Import); Cache-first. */
    fun loadBars(ticker: String, scope: CoroutineScope) {
        if (prices.value[ticker] != null || barJobs[ticker]?.isActive == true) return
        barJobs[ticker] = scope.launch {
            try {
                val bars = barCache.freshBars(ticker)
                    ?: withContext(Dispatchers.Default) {
                        runCatching { api.fetchBars(ticker) }.getOrNull().also { if (!it.isNullOrEmpty()) barCache.store(ticker, it) }
                    }
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
        lastUniverse = universe
        lastApplied = params to settings
        _universe.value = universe
        _state.value = ScanState.Running(0, universe.tickers.size, "")
        prices.value = emptyMap()
        job = scope.launch {
            try {
                val rows = mutableListOf<ScanRow>()
                val failed = mutableListOf<String>()
                val loadedPrices = mutableMapOf<String, List<Bar>>()
                val total = universe.tickers.size

                universe.tickers.chunked(batchSize).forEachIndexed { batchIndex, batch ->
                    // Ticker eines Batches parallel laden; Batch-Größe = Parallelität; Cache-first
                    var networkFetches = 0
                    val batchResults: List<Pair<String, List<Bar>?>> = batch.map { ticker ->
                        async {
                            val cached = barCache.freshBars(ticker)
                            if (cached != null) return@async ticker to cached
                            networkFetches++
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
                            barCache.store(ticker, bars)
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

                    // Pause nur wenn wirklich Netzwerklast war (Rate-Limiting-Schonung)
                    if (networkFetches > 0 && pauseSeconds > 0 && batchIndex < (total + batchSize - 1) / batchSize - 1) {
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

    /**
     * Regeländerung sofort validieren:
     * - Kurse vorhanden → lokaler Re-Score ohne Netzwerk
     * - keine Kurse (z. B. nach Treffer-CSV-Import) → einmaliger Download-Scan des Universums,
     *   danach laufen weitere Regeländerungen instant
     * Überspringen bei: unveränderten Regeln, abweichendem aktiven Universum, laufendem Scan.
     */
    fun revalidate(
        params: ScanParams,
        settings: ScanSettings,
        activeTickers: List<String>,
        batchSize: Int,
        pauseSeconds: Int,
        scope: CoroutineScope
    ) {
        val uni = lastUniverse ?: return
        if (uni.tickers != activeTickers) return // aktives Universum weicht vom gescannten ab
        val signature = params to settings
        if (signature == lastApplied) return
        if (state.value is ScanState.Running) return
        if (prices.value.isEmpty()) {
            runScan(uni, params, settings, batchSize, pauseSeconds, scope)
        } else {
            lastApplied = signature
            job?.cancel()
            // Kein Running-Emit: ResultsPane bleibt in der Komposition (Slider/ Auswahl bleiben erhalten),
            // die alten Ergebnisse bleiben sichtbar, bis der neue Stand fertig ist.
            job = scope.launch {
                try {
                    val rows = withContext(Dispatchers.Default) {
                        uni.tickers.mapNotNull { ticker ->
                            prices.value[ticker]?.let { bars ->
                                Screener.calcRow(ticker, uni.names[ticker] ?: "—", bars, params, uni.exchanges[ticker] ?: "")
                            }
                        }
                    }
                    _state.value = ScanState.Done(
                        ScanResult(
                            rows = rows.sortedWith(compareByDescending<ScanRow> { it.score }.thenByDescending { it.relVolume }),
                            failed = emptyList(),
                            settings = settings
                        ),
                        scanned = uni.tickers.size,
                        total = uni.tickers.size
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.value = ScanState.Failed(e.message ?: "Unbekannter Fehler")
                }
            }
        }
    }

    /** Bars als beobachtbaren State (recomponiert beim Nachladen). */
    @Composable
    fun rememberBars(ticker: String): List<Bar> = prices.collectAsState().value[ticker] ?: emptyList()

    @Composable
    fun rememberHasBars(): Boolean = prices.collectAsState().value.isNotEmpty()
}

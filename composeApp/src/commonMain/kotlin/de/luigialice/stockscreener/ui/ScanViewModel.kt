package de.luigialice.stockscreener.ui

import de.luigialice.stockscreener.data.BarCache
import de.luigialice.stockscreener.data.BarHistoryLoader
import de.luigialice.stockscreener.data.YahooApi
import de.luigialice.stockscreener.data.YahooHttpException
import de.luigialice.stockscreener.data.createBarStore
import de.luigialice.stockscreener.data.currentTimeMs
import de.luigialice.stockscreener.model.Bar
import de.luigialice.stockscreener.model.FailedReason
import de.luigialice.stockscreener.model.ScanParams
import de.luigialice.stockscreener.model.ScanResult
import de.luigialice.stockscreener.model.ScanRow
import de.luigialice.stockscreener.model.ScanSettings
import de.luigialice.stockscreener.model.StockInfo
import de.luigialice.stockscreener.model.displayName
import de.luigialice.stockscreener.model.Universe
import de.luigialice.stockscreener.screener.Screener
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

private data class FetchOutcome(val bars: List<Bar>? = null, val name: String? = null, val reason: FailedReason? = null)

class ScanViewModel(
    private val api: YahooApi = YahooApi(),
    private val barCache: BarCache = BarCache(createBarStore())
) : PlatformViewModel() {

    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    val state: StateFlow<ScanState> = _state
    private val _universe = MutableStateFlow<Universe?>(null)
    val universe: StateFlow<Universe?> = _universe
    private val barHistory = BarHistoryLoader(api, barCache)
    internal val bars: StateFlow<Map<String, List<Bar>>> get() = barHistory.bars
    private var job: Job? = null
    private var lastUniverse: Universe? = null
    private var lastApplied: Pair<ScanParams, ScanSettings>? = null

    /** Letzter vollständig abgeschlossener Ergebnisstand (für Abbrechen ohne Datenverlust). */
    private var lastCompleteState: ScanState.Done? = null

    /** Fehlerursachen des letzten Download-Scans (bleibt beim lokalen Re-Score erhalten). */
    private var lastFailed: Map<String, FailedReason> = emptyMap()

    /** Aus den Kursdaten übernommene Namen – für Universen ohne eigene (manuelle Ticker). */
    private val fetchedNames = mutableMapOf<String, String>()

    /**
     * Lauf-Token: wird bei jedem neuen Scan/Re-Score/Load/Cancel erhöht.
     * Emissionen eines überholten Laufs werden verworfen, damit ein abgebrochener
     * Job keinen neueren (vollständigen) Stand mehr überschreiben kann.
     */
    private var runId = 0

    /** Läuft gerade ein echter Download-Scan (nicht nur ein lokaler Re-Score)? */
    private var downloadRunning = false

    fun cancel() {
        val restore = if (!downloadRunning) lastCompleteState else null
        runId++
        downloadRunning = false
        job?.cancel()
        barHistory.cancelLoads()
        // Abbrechen eines lokalen Re-Scores darf die vorhandene Ergebnisliste nicht löschen
        _state.value = restore ?: ScanState.Idle
    }

    /** Ist gerade ein Scan/Re-Score-Job aktiv? */
    fun jobActive(): Boolean = job?.isActive == true

    /** Läuft ein Download-Scan (Netzwerk) — im Unterschied zu einem lokalen Re-Score? */
    fun downloadRunning(): Boolean = downloadRunning

    /**
     * Selbstheilung: hängt der State ohne aktiven Job (z. B. durch eine überholte
     * Status-Emission oder ein leeres Universum), wird er in einen sauberen Stand gebracht.
     */
    fun healIfStuck(snapshot: ScanState) {
        if (state.value !== snapshot || jobActive()) return
        when {
            snapshot is ScanState.Done && snapshot.isScanning ->
                _state.value = snapshot.copy(scanned = snapshot.total)
            snapshot is ScanState.Running -> {
                downloadRunning = false
                _state.value = ScanState.Idle
            }
        }
    }

    /** Universum setzen (CSV, automatische Ermittlung). */
    fun setUniverse(u: Universe?) {
        _universe.value = u
    }

    /** Ergebnissatz aus importierter Treffer-CSV anzeigen; Universum wird für Re-Score übernommen. */
    fun loadResults(result: ScanResult) {
        runId++
        downloadRunning = false
        job?.cancel()
        barHistory.clear()
        val rows = result.rows
        val uni = Universe(rows.associate { it.ticker to StockInfo(it.name) })
        _universe.value = uni
        lastUniverse = uni
        lastApplied = null
        val total = rows.size + result.failed.size
        val done = ScanState.Done(result, scanned = total, total = total)
        lastCompleteState = done
        lastFailed = result.failed
        _state.value = done
    }

    /** Bars für einen Ticker nachladen (z. B. nach Treffer-CSV-Import); Cache-first. */
    fun loadBars(ticker: String, scope: CoroutineScope) {
        barHistory.load(ticker, scope)
    }

    fun runScan(
        universe: Universe,
        params: ScanParams,
        settings: ScanSettings,
        batchSize: Int,
        pauseSeconds: Int,
        scope: CoroutineScope
    ) {
        val tickers = universe.tickers
        if (tickers.isEmpty()) {
            runId++
            downloadRunning = false
            job?.cancel()
            _state.value = ScanState.Failed("Kein Ticker im Universum angegeben")
            return
        }
        runId++
        val myRun = runId
        job?.cancel()
        lastUniverse = universe
        lastApplied = params to settings
        _universe.value = universe
        downloadRunning = true
        _state.value = ScanState.Running(0, tickers.size, "")
        barHistory.clear()
        job = scope.launch {
            try {
                val rows = mutableListOf<ScanRow>()
                val failed = mutableMapOf<String, FailedReason>()
                val loadedPrices = mutableMapOf<String, List<Bar>>()
                val total = tickers.size

                tickers.chunked(batchSize.coerceAtLeast(1)).forEachIndexed { batchIndex, batch ->
                    // Ticker eines Batches parallel laden; Batch-Größe = Parallelität; Cache-first
                    var networkFetches = 0
                    val batchResults: List<Pair<String, FetchOutcome>> = batch.map { ticker ->
                        async {
                            val cached = barCache.freshBars(ticker)
                            val cachedName = barCache.cachedName(ticker)
                            // Ohne Namen aus Universum und Cache nochmal laden: sonst bleibt die
                            // Spalte „Aktienname" leer (die Kursdaten kennen den Namen mit).
                            val knownName = universe.stocks[ticker]
                                ?.let { it.name.isNotBlank() && it.name != "—" } == true
                            if (cached != null && (knownName || cachedName != null)) {
                                return@async ticker to FetchOutcome(bars = cached, name = cachedName)
                            }
                            networkFetches++
                            try {
                                val fetched = withContext(Dispatchers.Default) { api.fetchBars(ticker) }
                                ticker to FetchOutcome(bars = fetched.bars, name = fetched.name)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: YahooHttpException) {
                                val reason = if (e.status == 429) FailedReason.RATE_LIMITED else FailedReason.NETWORK_ERROR
                                ticker to FetchOutcome(reason = reason)
                            } catch (e: Exception) {
                                ticker to FetchOutcome(reason = FailedReason.NETWORK_ERROR)
                            }
                        }
                    }.awaitAll()

                    batchResults.forEach { (ticker, outcome) ->
                        when {
                            outcome.bars.isNullOrEmpty() ->
                                failed[ticker] = outcome.reason ?: FailedReason.NO_DATA
                            else -> {
                                barCache.store(ticker, outcome.bars, outcome.name)
                                val stock = universe.stocks[ticker] ?: StockInfo()
                                outcome.name?.let { fetchedNames[ticker] = it }
                                val row = Screener.calcRow(ticker, stock.displayName(outcome.name), outcome.bars, params, stock.exchange)
                                if (row == null) failed[ticker] = FailedReason.NO_DATA
                                else { rows.add(row); loadedPrices[ticker] = outcome.bars }
                            }
                        }
                    }

                    // Inkrementelles Zwischenergebnis nach jedem Batch (nur für den aktuellen Lauf)
                    if (!isCurrent(myRun)) return@launch
                    barHistory.replaceFromScan(loadedPrices)
                    val done = ScanState.Done(
                        ScanResult(
                            rows = rows.sortedWith(compareByDescending<ScanRow> { it.score }.thenByDescending { it.relVolume }),
                            failed = failed.toMap(),
                            settings = settings
                        ),
                        scanned = rows.size + failed.size,
                        total = total
                    )
                    if (done.scanned >= total) {
                        lastFailed = failed.toMap()
                        lastCompleteState = done
                    }
                    _state.value = done

                    // Pause nur wenn wirklich Netzwerklast war (Rate-Limiting-Schonung)
                    if (networkFetches > 0 && pauseSeconds > 0 && batchIndex < (total + batchSize - 1) / batchSize - 1) {
                        delay(pauseSeconds * 1000L)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isCurrent(myRun)) _state.value = ScanState.Failed(e.message ?: "Unbekannter Fehler")
            } finally {
                if (isCurrent(myRun)) downloadRunning = false
            }
        }
    }

    /**
     * Regeländerung sofort validieren:
     * - Kurse vorhanden → lokaler Re-Score ohne Netzwerk
     * - keine Kurse (z. B. nach Treffer-CSV-Import) → einmaliger Download-Scan des Universums,
     *   danach laufen weitere Regeländerungen instant
     * Überspringen bei: unveränderten Regeln, abweichendem aktiven Universum. Läuft gerade ein
     * echter Download-Scan, entscheidet der Aufrufer (Neustart via runScan) — hier kein Eingriff.
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
        if (downloadRunning) return // echter Download läuft → der Scan erledigt das
        val signature = params to settings
        if (signature == lastApplied) return
        if (barHistory.bars.value.isEmpty()) {
            runScan(uni, params, settings, batchSize, pauseSeconds, scope)
        } else {
            runId++
            val myRun = runId
            lastApplied = signature
            job?.cancel()
            // Kein Running-Emit: ResultsPane bleibt in der Komposition (Slider/Auswahl bleiben erhalten),
            // die alten Ergebnisse bleiben sichtbar, bis der neue Stand fertig ist.
            // Fortschritt über Done-Snapshots pro Batch (isScanning zeigt den Balken).
            job = scope.launch {
                try {
                    val loaded = barHistory.bars.value
                    val rows = mutableListOf<ScanRow>()
                    val securities = uni.stocks.entries.toList()
                    val total = securities.size
                    var lastEmit = 0L
                    val chunkSize = batchSize.coerceAtLeast(1)
                    val chunks = securities.chunked(chunkSize)
                    chunks.forEachIndexed { i, chunk ->
                        val chunkRows = withContext(Dispatchers.Default) {
                            chunk.mapNotNull { (ticker, stock) ->
                                loaded[ticker]?.let { bars ->
                                    Screener.calcRow(ticker, stock.displayName(fetchedNames[ticker]), bars, params, stock.exchange)
                                }
                            }
                        }
                        if (!isCurrent(myRun)) return@launch
                        rows.addAll(chunkRows)
                        // Snapshots drosseln: erste Meldung sofort, danach max. alle 200ms, letzte garantiert
                        val now = currentTimeMs()
                        val isLast = i == chunks.lastIndex
                        if (isLast || now - lastEmit >= 200) {
                            lastEmit = now
                            val done = ScanState.Done(
                                ScanResult(
                                    rows = rows.sortedWith(compareByDescending<ScanRow> { it.score }.thenByDescending { it.relVolume }),
                                    // Fehlerursachen des Downloads beibehalten (nur Ticker dieses Universums)
                                    failed = lastFailed.filterKeys { it in uni.stocks },
                                    settings = settings
                                ),
                                scanned = (i * chunkSize + chunk.size).coerceAtMost(total),
                                total = total
                            )
                            if (isLast) lastCompleteState = done
                            _state.value = done
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (isCurrent(myRun)) _state.value = ScanState.Failed(e.message ?: "Unbekannter Fehler")
                }
            }
        }
    }

    private fun isCurrent(run: Int): Boolean = run == runId && job?.isActive != false

    /** Sind bereits Kursdaten geladen (nicht-reaktiv, z. B. für Tests)? */
    fun hasBars(): Boolean = barHistory.bars.value.isNotEmpty()

}

package de.luigialice.stockscreener.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.luigialice.stockscreener.csvFileDialogsAvailable
import de.luigialice.stockscreener.data.CsvUniverse
import de.luigialice.stockscreener.data.UniverseDiscovery
import de.luigialice.stockscreener.model.DEFAULT_SCORE_MIN
import de.luigialice.stockscreener.model.ONGOING_RSI_BAND
import de.luigialice.stockscreener.model.ScanMode
import de.luigialice.stockscreener.model.ScanSettings
import de.luigialice.stockscreener.model.Universe
import de.luigialice.stockscreener.model.scanParams
import de.luigialice.stockscreener.pickCsvText
import de.luigialice.stockscreener.screener.DEFAULT_TICKERS
import de.luigialice.stockscreener.screener.parseTickers
import de.luigialice.stockscreener.ui.DimText
import de.luigialice.stockscreener.ui.ScanState
import de.luigialice.stockscreener.ui.ScanViewModel
import de.luigialice.stockscreener.ui.SliderRow
import de.luigialice.stockscreener.ui.fmt
import de.luigialice.stockscreener.ui.isScanning
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun SettingsPane(
    state: ScanState,
    viewModel: ScanViewModel,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    var mode by remember { mutableStateOf(ScanMode.REBOUND_START) }
    var days by remember { mutableStateOf(21f) }
    var maxPerf by remember { mutableStateOf(-12f) }
    var rsiRange by remember { mutableStateOf(35f..48f) }
    var minVolume by remember { mutableStateOf(1.3f) }
    var onlyBelow12m by remember { mutableStateOf(true) }
    var batchSize by remember { mutableStateOf(25) }
    var pause by remember { mutableStateOf(1) }
    var tickerText by remember { mutableStateOf(DEFAULT_TICKERS) }
    val activeUniverse by viewModel.universe.collectAsState()

    fun paramsFor(m: ScanMode) =
        scanParams(m, days.toInt(), maxPerf.toDouble(), rsiRange.start.toDouble(), rsiRange.endInclusive.toDouble(), minVolume.toDouble())
    fun settingsFor(m: ScanMode) = ScanSettings(DEFAULT_SCORE_MIN.coerceAtMost(m.maxScore), onlyBelow12m, m)

    Column(modifier.padding(16.dp)) {
        Text("Rebound Screener", style = MaterialTheme.typography.titleLarge)
        DimText("Kein Kaufsignal, keine Anlageberatung.")
        Spacer(Modifier.height(16.dp))

        Text("Scan-Modus", fontWeight = FontWeight.SemiBold)
        fun applyMode(m: ScanMode) {
            val tickers = activeUniverse?.tickers ?: parseTickers(tickerText)
            if (tickers.isEmpty()) return // nichts zu bewerten
            mode = m
            if (viewModel.downloadRunning()) {
                // Laufenden Download mit dem neuen Modus neu starten:
                // bereits geladene Ticker kommen aus dem Cache und kosten keine Zeit
                val uni = activeUniverse ?: Universe(tickers, tickers.associateWith { "—" }, tickers.associateWith { "" })
                viewModel.runScan(uni, paramsFor(m), settingsFor(m), batchSize, pause, scope)
            } else {
                // lokaler Re-Score (auch wenn gerade ein Re-Score läuft: der neue überholt den alten)
                viewModel.revalidate(paramsFor(m), settingsFor(m), tickers, batchSize, pause, scope)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScanMode.entries.forEach { m ->
                TextButton(onClick = { applyMode(m) }) {
                    Text(m.label, color = if (mode == m) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        Text("Rebound-Regeln", fontWeight = FontWeight.SemiBold)
        if (mode == ScanMode.REBOUND_START) {
            SliderRow("Rückgang (Handelstage)", "${days.toInt()}", days, 10f, 60f) { days = it }
            SliderRow("Max. Performance im Zeitraum (%)", "${maxPerf.toInt()} %", maxPerf, -60f, 0f) { maxPerf = it }
            Text("RSI-Bereich: ${rsiRange.start.toInt()} – ${rsiRange.endInclusive.toInt()}")
            RangeSlider(rsiRange, valueRange = 10f..70f, onValueChange = { rsiRange = it })
            SliderRow("Min. relatives Volumen", fmt(minVolume.toDouble(), 1), minVolume, 0.5f, 4f) { minVolume = it }
        } else {
            DimText("RSI-Bereich: ${ONGOING_RSI_BAND.start.toInt()} – ${ONGOING_RSI_BAND.endInclusive.toInt()} (fest)")
            DimText("Kriterien: 12M-Verlierer, über SMA20/SMA50, höheres Tief, RSI im Band.")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = onlyBelow12m, onCheckedChange = { onlyBelow12m = it })
            Text("Nur Aktien unter Kurs von vor 12 Monaten", fontSize = 13.sp)
        }
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        Text("Universum", fontWeight = FontWeight.SemiBold)
        TextField(
            value = tickerText,
            onValueChange = { v ->
                tickerText = v
                // bearbeitete Ticker-Liste gilt wieder statt des geladenen Universums
                if (activeUniverse != null && parseTickers(v) != activeUniverse?.tickers) viewModel.setUniverse(null)
            },
            label = { Text("Ticker") },
            modifier = Modifier.fillMaxWidth().height(80.dp)
        )
        Spacer(Modifier.height(4.dp))
        var csvError by remember { mutableStateOf<String?>(null) }
        val discovery = remember { UniverseDiscovery() }
        var fetchingLabel by remember { mutableStateOf<String?>(null) }

        val csvDialogs = csvFileDialogsAvailable()
        var menuOpen by remember { mutableStateOf(false) }

        fun loadUniverse(label: String, fetch: suspend () -> Universe) {
            fetchingLabel = label
            scope.launch {
                runCatching { fetch() }
                    .onSuccess { uni ->
                        viewModel.setUniverse(uni)
                        tickerText = uni.tickers.joinToString(" ")
                        csvError = null
                    }
                    .onFailure { e -> csvError = "$label fehlgeschlagen: ${e.message}" }
                fetchingLabel = null
            }
        }

        fun loadFromCsvFile() {
            pickCsvText { picked ->
                picked?.let { text ->
                    runCatching { CsvUniverse.parse(text) }
                        .onSuccess {
                            viewModel.setUniverse(it)
                            tickerText = it.tickers.joinToString(" ")
                            csvError = null
                        }
                        .onFailure { csvError = "CSV konnte nicht gelesen werden: ${it.message}" }
                }
            }
        }

        Box {
            Button(
                enabled = fetchingLabel == null,
                onClick = { menuOpen = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text(fetchingLabel ?: "Universum laden …") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Kompletter US-Markt (NASDAQ/NYSE/AMEX)") },
                    onClick = { menuOpen = false; loadUniverse("Ermittle US-Aktien …") { discovery.discoverUniverse() } }
                )
                DropdownMenuItem(
                    text = { Text("S&P 500 (~500 Large Caps)") },
                    onClick = { menuOpen = false; loadUniverse("Lade S&P 500 …") { discovery.discoverSp500() } }
                )
                DropdownMenuItem(
                    text = { Text("Nasdaq 100") },
                    onClick = { menuOpen = false; loadUniverse("Lade Nasdaq 100 …") { discovery.discoverNasdaq100() } }
                )
                DropdownMenuItem(
                    text = { Text("Dow Jones (30)") },
                    onClick = { menuOpen = false; loadUniverse("Lade Dow Jones …") { discovery.discoverDowJones() } }
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("DAX (40, XETRA)") },
                    onClick = { menuOpen = false; loadUniverse("Lade DAX …") { discovery.discoverDax() } }
                )
                DropdownMenuItem(
                    text = { Text("MDAX (XETRA)") },
                    onClick = { menuOpen = false; loadUniverse("Lade MDAX …") { discovery.discoverMdax() } }
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(if (csvDialogs) "CSV-Datei laden … (ticker, name, exchange)" else "CSV-Import nur am Desktop") },
                    enabled = csvDialogs,
                    onClick = { menuOpen = false; loadFromCsvFile() }
                )
            }
        }
        csvError?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
        activeUniverse?.let { DimText("Universum aktiv: ${it.tickers.size} Aktien") }
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        Text("Scan-Ablauf", fontWeight = FontWeight.SemiBold)
        SliderRow("Batch-Größe (parallel)", "$batchSize", batchSize.toFloat(), 5f, 50f) { batchSize = it.toInt() }
        SliderRow("Pause zwischen Batches (Sek.)", "$pause", pause.toFloat(), 0f, 15f) { pause = it.toInt() }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                val uni = activeUniverse ?: run {
                    val tickers = parseTickers(tickerText)
                    Universe(tickers, tickers.associateWith { "—" }, tickers.associateWith { "" })
                }
                viewModel.runScan(uni, paramsFor(mode), settingsFor(mode), batchSize, pause, scope)
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Screener starten") }

        // Regeländerungen sofort validieren: mit geladenen Kursen instant,
        // ohne Kurse (z. B. nach Import) einmaliger Download-Scan des aktiven Universums
        val hasBars = viewModel.rememberHasBars()
        LaunchedEffect(mode, days, maxPerf, rsiRange, minVolume, onlyBelow12m, hasBars, activeUniverse, state.isScanning, tickerText) {
            if (viewModel.downloadRunning()) return@LaunchedEffect // echter Download läuft
            val tickers = activeUniverse?.tickers ?: parseTickers(tickerText)
            if (tickers.isEmpty()) return@LaunchedEffect
            delay(300.milliseconds) // Debounce bei Slider-Bewegungen
            viewModel.revalidate(paramsFor(mode), settingsFor(mode), tickers, batchSize, pause, scope)
        }
        if (state.isScanning) {
            TextButton(onClick = { viewModel.cancel() }) { Text("Abbrechen") }
        }
    }
}

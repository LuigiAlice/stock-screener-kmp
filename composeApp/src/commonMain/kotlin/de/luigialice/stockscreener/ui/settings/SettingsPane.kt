package de.luigialice.stockscreener.ui.settings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.luigialice.stockscreener.data.CsvUniverse
import de.luigialice.stockscreener.data.UniverseDiscovery
import de.luigialice.stockscreener.model.ScanMode
import de.luigialice.stockscreener.model.ScanParams
import de.luigialice.stockscreener.model.ScanSettings
import de.luigialice.stockscreener.model.Universe
import de.luigialice.stockscreener.pickCsvText
import de.luigialice.stockscreener.screener.DEFAULT_TICKERS
import de.luigialice.stockscreener.screener.parseTickers
import de.luigialice.stockscreener.ui.ScanState
import de.luigialice.stockscreener.ui.ScanViewModel
import de.luigialice.stockscreener.ui.SliderRow
import de.luigialice.stockscreener.ui.fmt
import de.luigialice.stockscreener.ui.green
import de.luigialice.stockscreener.ui.isScanning
import de.luigialice.stockscreener.ui.red
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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
    var minScore by remember { mutableStateOf(5) }
    var batchSize by remember { mutableStateOf(25) }
    var pause by remember { mutableStateOf(3) }
    var tickerText by remember { mutableStateOf(DEFAULT_TICKERS) }
    var universe by remember { mutableStateOf<Universe?>(null) }
    var csvError by remember { mutableStateOf<String?>(null) }

    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("📈 Rebound Screener", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Kein Kaufsignal, keine Anlageberatung.", fontSize = 12.sp, color = Color.Gray)
        Spacer(Modifier.height(16.dp))

        Text("Scan-Modus", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScanMode.entries.forEach { m ->
                TextButton(onClick = { mode = m }) {
                    Text(m.label, color = if (mode == m) MaterialTheme.colorScheme.primary else Color.Gray)
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
            Text("RSI-Bereich: 45 – 65 (fest)", fontSize = 12.sp, color = Color.Gray)
            Text("Kriterien: 12M-Verlierer, über SMA20/SMA50, höheres Tief, RSI im Band.", fontSize = 12.sp, color = Color.Gray)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = onlyBelow12m, onCheckedChange = { onlyBelow12m = it })
            Text("Nur Aktien unter Kurs von vor 12 Monaten", fontSize = 13.sp)
        }
        SliderRow("Mindest-Score als Treffer (von ${mode.maxScore})", "$minScore", minScore.toFloat(), 1f, mode.maxScore.toFloat()) { minScore = it.toInt() }
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        Text("Universum", fontWeight = FontWeight.SemiBold)
        TextField(
            value = tickerText,
            onValueChange = { tickerText = it },
            label = { Text("Ticker") },
            modifier = Modifier.fillMaxWidth().height(120.dp)
        )
        Spacer(Modifier.height(4.dp))
        var csvError by remember { mutableStateOf<String?>(null) }
        val discovery = remember { UniverseDiscovery() }
        var fetchingLabel by remember { mutableStateOf<String?>(null) }

        TextButton(
            onClick = {
                pickCsvText()?.let { text ->
                    runCatching { CsvUniverse.parse(text) }
                        .onSuccess {
                            universe = it
                            tickerText = it.tickers.joinToString(" ")
                            csvError = null
                        }
                        .onFailure { csvError = "CSV konnte nicht gelesen werden: ${it.message}" }
                }
            }
        ) { Text("CSV-Datei laden (ticker, name, exchange)") }
        TextButton(
            enabled = fetchingLabel == null,
            onClick = {
                fetchingLabel = "Ermittle US-Aktien …"
                scope.launch {
                    runCatching { discovery.discoverUniverse() }
                        .onSuccess { uni ->
                            universe = uni
                            tickerText = uni.tickers.joinToString(" ")
                            csvError = null
                        }
                        .onFailure { e ->
                            csvError = "Aktien-Ermittlung fehlgeschlagen: ${e.message}"
                        }
                    fetchingLabel = null
                }
            }
        ) { Text(if (fetchingLabel == "Ermittle US-Aktien …") "Ermittle US-Aktien …" else "US-Aktien automatisch ermitteln") }
        TextButton(
            enabled = fetchingLabel == null,
            onClick = {
                fetchingLabel = "Lade S&P 500 …"
                scope.launch {
                    runCatching { discovery.discoverSp500() }
                        .onSuccess { uni ->
                            universe = uni
                            tickerText = uni.tickers.joinToString(" ")
                            csvError = null
                        }
                        .onFailure { e ->
                            csvError = "S&P 500 laden fehlgeschlagen: ${e.message}"
                        }
                    fetchingLabel = null
                }
            }
        ) { Text(if (fetchingLabel == "Lade S&P 500 …") "Lade S&P 500 …" else "S&P 500 laden (~500 Large Caps)") }
        csvError?.let { Text(it, fontSize = 12.sp, color = red) }
        universe?.let { Text("Universum geladen: ${it.tickers.size} Aktien", fontSize = 12.sp, color = green) }
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        Text("Scan-Ablauf", fontWeight = FontWeight.SemiBold)
        SliderRow("Batch-Größe (parallel)", "$batchSize", batchSize.toFloat(), 5f, 50f) { batchSize = it.toInt() }
        SliderRow("Pause zwischen Batches (Sek.)", "$pause", pause.toFloat(), 0f, 15f) { pause = it.toInt() }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                val uni = universe ?: run {
                    val tickers = parseTickers(tickerText)
                    Universe(tickers, tickers.associateWith { "—" }, tickers.associateWith { "" })
                }
                viewModel.runScan(
                    uni,
                    ScanParams(mode, days.toInt(), maxPerf.toDouble(), rsiRange.start.toDouble(), rsiRange.endInclusive.toDouble(), minVolume.toDouble()),
                    ScanSettings(minScore, onlyBelow12m, mode),
                    batchSize, pause, scope
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Screener starten") }
        if (state.isScanning) {
            TextButton(onClick = { viewModel.cancel() }) { Text("Abbrechen") }
        }
    }
}

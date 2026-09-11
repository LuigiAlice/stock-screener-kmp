package de.luigialice.stockscreener.ui.results

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.luigialice.stockscreener.exportCsv
import de.luigialice.stockscreener.model.ScanRow
import de.luigialice.stockscreener.pickCsvText
import de.luigialice.stockscreener.ui.LinkText
import de.luigialice.stockscreener.ui.Metric
import de.luigialice.stockscreener.ui.ScanState
import de.luigialice.stockscreener.ui.ScanViewModel
import de.luigialice.stockscreener.ui.SliderRow
import de.luigialice.stockscreener.ui.chart.LineChart
import de.luigialice.stockscreener.ui.fmtDate
import de.luigialice.stockscreener.ui.results.buildImportedResult
import de.luigialice.stockscreener.ui.results.parseHitsCsv

@Composable
internal fun ResultsPane(done: ScanState.Done, viewModel: ScanViewModel) {
    val result = done.result
    // Key: Scan-Modus (bleibt über Re-Scores stabil); initial = Mitte des Score-Bereichs
    var displayScore by remember(result.settings.mode) {
        mutableStateOf((result.settings.mode.maxScore + 1) / 2)
    }
    // Auswahl über Ticker statt Index, damit sie Re-Scores übersteht
    var selectedTicker by remember { mutableStateOf<String?>(null) }
    val uriHandler = LocalUriHandler.current
    val hits = result.rows
        .filter { it.score >= displayScore }
        .filter { !result.settings.onlyBelow12m || it.below12m == true }
    val selectedIndex = hits.indexOfFirst { it.ticker == selectedTicker }.takeIf { it >= 0 } ?: 0

    // Pfeiltasten-Navigation: die ganze Ergebnis-Spalte hält den Fokus (Klicks in sie stehlen ihn nicht),
    // onKeyEvent (nicht Preview) damit Slider/Buttons Pfeiltasten vorab nutzen können
    val focusRequester = remember { FocusRequester() }
    val onKey: (Int) -> Unit = { i -> selectedTicker = hits.getOrNull(i)?.ticker }
    Column(
        Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionLeft, Key.DirectionUp -> if (selectedIndex > 0) { onKey(selectedIndex - 1); true } else false
                    Key.DirectionRight, Key.DirectionDown -> if (selectedIndex < hits.size - 1) { onKey(selectedIndex + 1); true } else false
                    else -> false
                }
            }
    ) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { focusRequester.requestFocus() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Metric("Geprüfte Aktien", result.rows.size.toString())
            Metric("Treffer", hits.size.toString())
            Metric("Modus", result.settings.mode.label)
        }
        SliderRow(
            "Trefferfilter (ohne neuen Scan)", "$displayScore",
            displayScore.toFloat(), 1f, result.settings.mode.maxScore.toFloat()
        ) { displayScore = it.toInt() }
        Text(
            "Score = Anzahl erfüllter Kriterien (0–${result.settings.mode.maxScore}). " +
                result.rows.firstOrNull()?.criteria?.keys?.joinToString(" · ").orEmpty(),
            fontSize = 12.sp, color = Color.Gray
        )
        if (result.failed.isNotEmpty()) {
            Text("${result.failed.size} Ticker ohne verwertbare Daten", fontSize = 12.sp, color = Color(0xFFB45309))
        }
        Spacer(Modifier.height(8.dp))
        Text("Treffer (${hits.size})", fontWeight = FontWeight.SemiBold)
        if (hits.isEmpty()) {
            Text("Keine Treffer. Trefferfilter senken oder Regeln anpassen.", color = Color.Gray)
        } else {
            ResultsTable(
                hits,
                selectedIndex = selectedIndex.coerceIn(0, hits.size - 1),
                onSelect = { i -> selectedTicker = hits.getOrNull(i)?.ticker },
                uriHandler = uriHandler
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { exportCsv(result.rows.toCsv()) }) { Text("Alle Treffer als CSV herunterladen") }
                TextButton(onClick = { exportCsv(hits.toCsv()) }) { Text("Nur sichtbare Treffer") }
                ImportButton(viewModel)
            }
            HitChartSelector(
                hits,
                selectedIndex = selectedIndex,
                onSelect = { i -> selectedTicker = hits.getOrNull(i)?.ticker },
                viewModel = viewModel
            )
        }
    }
}

/** Import-Button mit eigener Fehlermeldung; auch im Idle-Zustand sichtbar. */
@Composable
internal fun ImportButton(viewModel: ScanViewModel) {
    var importError by remember { mutableStateOf<String?>(null) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = {
            pickCsvText()?.let { text ->
                val rows = runCatching { parseHitsCsv(text) }.getOrElse { emptyList() }
                if (rows.isNotEmpty()) {
                    viewModel.loadResults(buildImportedResult(rows))
                    importError = null
                } else {
                    importError = "Treffer-CSV konnte nicht gelesen werden (erwartet: Export der Trefferliste)"
                }
            }
        }) { Text("Treffer-CSV importieren") }
        importError?.let { Text(it, fontSize = 12.sp, color = Color(0xFFB45309)) }
    }
}

@Composable
private fun HitChartSelector(
    hits: List<ScanRow>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    viewModel: ScanViewModel,
) {
    val index = selectedIndex.coerceIn(0, hits.size - 1)
    val selected = hits[index]
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val bars = viewModel.rememberBars(selected.ticker)

    LaunchedEffect(selected.ticker) { viewModel.loadBars(selected.ticker, scope) }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { onSelect(index - 1) }, enabled = index > 0) { Text("◀ Zurück") }
        Text("${index + 1}/${hits.size} — ${selected.ticker} · ${selected.name}", fontWeight = FontWeight.SemiBold)
        Button(onClick = { onSelect(index + 1) }, enabled = index < hits.size - 1) { Text("Weiter ▶") }
    }
    if (bars.isNotEmpty()) {
        LineChart(
            bars,
            Modifier.fillMaxWidth().height(340.dp).padding(vertical = 8.dp),
            title = "${selected.ticker} · ${selected.name} · ${fmtDate(bars.first().date)} – ${fmtDate(bars.last().date)}"
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LinkText("Yahoo Summary", selected.yahooSummary, uriHandler)
        LinkText("Google Finance", selected.google, uriHandler)
    }
}

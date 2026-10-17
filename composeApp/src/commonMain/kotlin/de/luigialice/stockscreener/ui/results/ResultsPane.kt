package de.luigialice.stockscreener.ui.results

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.luigialice.stockscreener.csvFileDialogsAvailable
import de.luigialice.stockscreener.exportCsv
import de.luigialice.stockscreener.model.FailedReason
import de.luigialice.stockscreener.model.ScanMode
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
import de.luigialice.stockscreener.ui.theme.AppColors
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus

/**
 * UI-Zustand der Ergebnisansicht, der Scan-/Moduswechsel überleben soll
 * (wird in MainPane gehalten, weil ResultsPane bei laufendem Scan die Komposition verlässt).
 */
internal class ResultsUiState {
    var scoresByMode by mutableStateOf<Map<ScanMode, Int>>(emptyMap())
    var selectedTicker by mutableStateOf<String?>(null)
}

@Composable
internal fun ResultsPane(done: ScanState.Done, viewModel: ScanViewModel, ui: ResultsUiState) {
    val result = done.result
    val mode = result.settings.mode
    // Trefferfilter pro Scan-Modus gemerkter Zustand; initial = Mitte des Score-Bereichs
    val displayScore = ui.scoresByMode[mode] ?: ((mode.maxScore + 1) / 2)
    // Auswahl über Ticker statt Index, damit sie Re-Scores übersteht
    val selectedTicker = ui.selectedTicker
    val uriHandler = LocalUriHandler.current
    val hits = result.rows
        .filter { it.score >= displayScore }
        .filter { !result.settings.onlyBelow12m || it.below12m == true }
    val selectedIndex = hits.indexOfFirst { it.ticker == selectedTicker }.takeIf { it >= 0 } ?: 0

    // Pfeiltasten-Navigation: die ganze Ergebnis-Spalte hält den Fokus (Klicks in sie stehlen ihn nicht),
    // onKeyEvent (nicht Preview) damit Slider/Buttons Pfeiltasten vorab nutzen können
    val focusRequester = remember { FocusRequester() }
    val onKey: (Int) -> Unit = { i -> ui.selectedTicker = hits.getOrNull(i)?.ticker }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val bounded = constraints.hasBoundedHeight
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
            val tableModifier = if (bounded) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().height(180.dp)
            val chartModifier = if (bounded) Modifier.fillMaxWidth().weight(2f).padding(vertical = 8.dp)
            else Modifier.fillMaxWidth().height(340.dp).padding(vertical = 8.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Metric("Geprüfte Aktien", result.rows.size.toString())
            Metric("Treffer", hits.size.toString())
            Metric("Modus", result.settings.mode.label)
        }
        SliderRow(
            "Trefferfilter (ohne neuen Scan)", "$displayScore",
            displayScore.toFloat(), 1f, result.settings.mode.maxScore.toFloat()
        ) { ui.scoresByMode = ui.scoresByMode + (mode to it.toInt()) }
        Text(
            "Score = Anzahl erfüllter Kriterien (0–${result.settings.mode.maxScore}). " +
                result.rows.firstOrNull()?.criteria?.keys?.joinToString(" · ").orEmpty(),
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (result.failed.isNotEmpty()) {
            val rateLimited = result.failed.count { it.value == FailedReason.RATE_LIMITED }
            val network = result.failed.count { it.value == FailedReason.NETWORK_ERROR }
            val noData = result.failed.count { it.value == FailedReason.NO_DATA }
            Text(
                "${result.failed.size} Ticker ohne verwertbare Daten — " +
                    listOfNotNull(
                        if (rateLimited > 0) "Rate-Limit: $rateLimited" else null,
                        if (network > 0) "Netzwerkfehler: $network" else null,
                        if (noData > 0) "keine Kursdaten: $noData" else null
                    ).joinToString(" · "),
                fontSize = 12.sp, color = AppColors.warn
            )
        }
        Spacer(Modifier.height(8.dp))
        Text("Treffer (${hits.size})", fontWeight = FontWeight.SemiBold)
        if (hits.isEmpty()) {
            Text("Keine Treffer. Trefferfilter senken oder Regeln anpassen.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            ResultsTable(
                hits,
                selectedIndex = selectedIndex.coerceIn(0, hits.size - 1),
                onSelect = { i -> ui.selectedTicker = hits.getOrNull(i)?.ticker },
                uriHandler = uriHandler,
                tableModifier = tableModifier
            )
            Spacer(Modifier.height(12.dp))
            if (csvFileDialogsAvailable()) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { exportCsv(result.rows.toCsv()) }) { Text("Alle Treffer als CSV herunterladen") }
                    TextButton(onClick = { exportCsv(hits.toCsv()) }) { Text("Nur sichtbare Treffer") }
                    ImportButton(viewModel)
                }
            } else {
                Text("CSV-Import/-Export nur am Desktop verfügbar", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HitChartSelector(
                hits,
                selectedIndex = selectedIndex,
                onSelect = { i -> ui.selectedTicker = hits.getOrNull(i)?.ticker },
                viewModel = viewModel,
                chartModifier = chartModifier
            )
        }
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
        importError?.let { Text(it, fontSize = 12.sp, color = AppColors.warn) }
    }
}

@Composable
private fun HitChartSelector(
    hits: List<ScanRow>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    viewModel: ScanViewModel,
    chartModifier: Modifier = Modifier.fillMaxWidth().height(340.dp).padding(vertical = 8.dp),
) {
    val index = selectedIndex.coerceIn(0, hits.size - 1)
    val selected = hits[index]
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val bars = viewModel.rememberBars(selected.ticker)

    LaunchedEffect(selected.ticker) { viewModel.loadBars(selected.ticker, scope) }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { onSelect(index - 1) }, enabled = index > 0) { Text("◀ Zurück") }
        Button(onClick = { onSelect(index + 1) }, enabled = index < hits.size - 1) { Text("Weiter ▶") }
        Text(
            "${index + 1}/${hits.size} — ${selected.ticker} · ${selected.name}",
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
    val lastDate = bars.lastOrNull()?.date
    val chartBars = remember(bars) {
        if (lastDate == null) bars else bars.filter { it.date >= lastDate.minus(DatePeriod(years = 1)) }
    }
    if (chartBars.isNotEmpty()) {
        LineChart(
            chartBars,
            chartModifier,
            title = "${selected.ticker} · ${selected.name} · ${fmtDate(chartBars.first().date)} – ${fmtDate(chartBars.last().date)}"
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LinkText("Yahoo Summary", selected.yahooSummary, uriHandler)
        LinkText("Google Finance", selected.google, uriHandler)
    }
}

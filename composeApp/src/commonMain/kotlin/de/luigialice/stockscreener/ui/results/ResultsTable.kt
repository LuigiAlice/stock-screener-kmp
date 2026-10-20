package de.luigialice.stockscreener.ui.results

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.luigialice.stockscreener.model.ScanRow
import de.luigialice.stockscreener.ui.fmt
import de.luigialice.stockscreener.ui.fmtIsoDate
import de.luigialice.stockscreener.ui.green
import de.luigialice.stockscreener.ui.red

private val HEADER_WIDTHS = listOf(70, 170, 60, 70, 60, 50, 60, 60, 60)

/** Breite der Kriteriums-Spalten – passt zum längsten Kriteriumsnamen in einer Zeile. */
private const val CRITERION_WIDTH = 100

@Composable
internal fun ResultsTable(
    rows: List<ScanRow>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    uriHandler: UriHandler,
    tableModifier: Modifier = Modifier.fillMaxWidth().height(180.dp),
) {
    Column(tableModifier.horizontalScroll(rememberScrollState())) {
        Row(Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
            listOf("Ticker", "Aktienname", "Kurs", "Perf %", "12M %", "RSI", "relVol", "SMA20 %", "SMA50 %").forEachIndexed { i, h ->
                Cell(h, HEADER_WIDTHS[i], bold = true, singleLine = true)
            }
            rows.firstOrNull()?.criteria?.keys?.forEach { Cell(it, CRITERION_WIDTH, bold = true, singleLine = true) }
            Cell("Score", 70, bold = true, singleLine = true)
            Cell("Tag", 90, bold = true, singleLine = true)
            Cell("Links", 130, bold = true, singleLine = true)
        }
        val listState = rememberLazyListState()
    // Auswahl nachführen: nur scrollen, wenn die Zeile nicht (voll) sichtbar ist
    LaunchedEffect(selectedIndex, rows.size) {
        if (rows.isEmpty()) return@LaunchedEffect
        val target = selectedIndex.coerceIn(0, rows.size - 1)
        val visible = listState.layoutInfo.visibleItemsInfo
        if (visible.none { it.index == target }) {
            listState.animateScrollToItem(target)
        }
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(rows) { i, row ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            if (i == selectedIndex) MaterialTheme.colorScheme.secondaryContainer
                            else Color.Transparent
                        )
                        .clickable { onSelect(i) }
                ) {
                    Cell(row.ticker, HEADER_WIDTHS[0], bold = true)
                    Cell(row.name, HEADER_WIDTHS[1])
                    Cell(fmt(row.price), HEADER_WIDTHS[2])
                    Cell(row.performance?.let { fmt(it) } ?: "—", HEADER_WIDTHS[3])
                    Cell(row.perf12m?.let { fmt(it) } ?: "—", HEADER_WIDTHS[4])
                    Cell(fmt(row.rsi14, 1), HEADER_WIDTHS[5])
                    Cell(fmt(row.relVolume), HEADER_WIDTHS[6])
                    Cell(fmt(row.sma20Pct), HEADER_WIDTHS[7])
                    Cell(fmt(row.sma50Pct), HEADER_WIDTHS[8])
                    row.criteria.forEach { (_, value) ->
                        Text(
                            if (value) "Ja" else "Nein",
                            color = if (value) green else red,
                            fontSize = 12.sp,
                            modifier = Modifier.width(CRITERION_WIDTH.dp).padding(horizontal = 4.dp, vertical = 6.dp)
                        )
                    }
                    Column(Modifier.width(70.dp)) {
                        Text("${row.score}/${row.criteria.size}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        LinearProgressIndicator(
                            progress = { row.score.toFloat() / row.criteria.size },
                            modifier = Modifier.width(60.dp).height(6.dp)
                        )
                    }
                    Cell(fmtIsoDate(row.lastTradingDay), 90)
                    Row(Modifier.width(130.dp)) {
                        Text("Summary", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, modifier = Modifier.clickable { uriHandler.openUri(row.yahooSummary) })
                        Text(" · ", fontSize = 12.sp)
                        Text("Google", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, modifier = Modifier.clickable { uriHandler.openUri(row.google) })
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun Cell(text: String, width: Int, bold: Boolean = false, singleLine: Boolean = false) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = if (singleLine) 1 else Int.MAX_VALUE,
        modifier = Modifier.width(width.dp).padding(horizontal = 4.dp, vertical = 6.dp)
    )
}

package de.luigialice.stockscreener.ui.results

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.luigialice.stockscreener.csvFileDialogsAvailable
import de.luigialice.stockscreener.ui.DimText
import de.luigialice.stockscreener.ui.ScanState
import de.luigialice.stockscreener.ui.ScanViewModel
import de.luigialice.stockscreener.ui.isScanning

@Composable
internal fun MainPane(state: ScanState, viewModel: ScanViewModel, modifier: Modifier = Modifier) {
    // UI-Zustand hier halten: ResultsPane verlässt bei laufendem Scan die Komposition
    MainPane(state, viewModel, remember { ResultsUiState() }, modifier)
}

@Composable
internal fun MainPane(state: ScanState, viewModel: ScanViewModel, ui: ResultsUiState, modifier: Modifier) {
    Column(modifier.padding(16.dp)) {
        when (val s = state) {
            is ScanState.Idle -> {
                DimText("Aktien-Universum wählen (CSV, automatisch oder S&P 500) und dann „Screener starten“.", fontSize = 14.sp)
                if (csvFileDialogsAvailable()) {
                    Spacer(Modifier.height(12.dp))
                    ImportButton(viewModel)
                    DimText("Alternativ: Trefferliste aus einem früheren Lauf als CSV importieren.")
                }
            }
            is ScanState.Running -> {
                Text("Scanne … ${s.done}/${s.total} (${s.current})", fontWeight = FontWeight.SemiBold)
                LinearProgressIndicator(
                    progress = { if (s.total > 0) s.done.toFloat() / s.total else 0f },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            is ScanState.Failed -> Text("Scan fehlgeschlagen: ${s.message}", color = MaterialTheme.colorScheme.error)
            is ScanState.Done -> {
                if (s.isScanning) {
                    Text("Scanne … ${s.scanned}/${s.total}", fontWeight = FontWeight.SemiBold)
                    LinearProgressIndicator(
                        progress = { if (s.total > 0) s.scanned.toFloat() / s.total else 0f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                }
                ResultsPane(s, viewModel, ui)
            }
        }
    }
}

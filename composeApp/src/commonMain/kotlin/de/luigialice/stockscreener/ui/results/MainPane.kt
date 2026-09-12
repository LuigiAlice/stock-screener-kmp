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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.luigialice.stockscreener.ui.ScanState
import de.luigialice.stockscreener.ui.ScanViewModel
import de.luigialice.stockscreener.ui.isScanning
import de.luigialice.stockscreener.ui.red

@Composable
internal fun MainPane(state: ScanState, viewModel: ScanViewModel, modifier: Modifier = Modifier) {
    Column(modifier.padding(16.dp)) {
        when (val s = state) {
            is ScanState.Idle -> {
                Text("CSV hochladen oder Ticker einfügen und dann „Screener starten“.", color = Color.Gray)
                Spacer(Modifier.height(12.dp))
                ImportButton(viewModel)
                Text(
                    "Alternativ: Trefferliste aus einem früheren Lauf als CSV importieren.",
                    fontSize = 12.sp, color = Color.Gray
                )
            }
            is ScanState.Running -> {
                Text("Scanne … ${s.done}/${s.total} (${s.current})", fontWeight = FontWeight.SemiBold)
                LinearProgressIndicator(progress = { s.done.toFloat() / s.total }, modifier = Modifier.fillMaxWidth())
            }
            is ScanState.Failed -> Text("Scan fehlgeschlagen: ${s.message}", color = red)
            is ScanState.Done -> {
                if (s.isScanning) {
                    Text("Scanne … ${s.scanned}/${s.total}", fontWeight = FontWeight.SemiBold)
                    LinearProgressIndicator(progress = { s.scanned.toFloat() / s.total }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                }
                ResultsPane(s, viewModel)
            }
        }
    }
}

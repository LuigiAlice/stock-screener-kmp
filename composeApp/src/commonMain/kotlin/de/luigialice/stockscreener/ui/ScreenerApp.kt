package de.luigialice.stockscreener.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.luigialice.stockscreener.rememberScanViewModel
import de.luigialice.stockscreener.ui.results.MainPane
import de.luigialice.stockscreener.ui.settings.SettingsPane

@Composable
fun ScreenerApp() {
    val scope = rememberCoroutineScope()
    val viewModel = rememberScanViewModel()
    val state by viewModel.state.collectAsState()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Row(Modifier.fillMaxSize()) {
            SettingsPane(
                state, viewModel, scope,
                Modifier.width(360.dp).fillMaxHeight()
            )
            MainPane(state, viewModel, Modifier.weight(1f))
        }
    }
}

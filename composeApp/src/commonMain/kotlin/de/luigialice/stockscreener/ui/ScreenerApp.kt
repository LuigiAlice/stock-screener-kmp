package de.luigialice.stockscreener.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.luigialice.stockscreener.rememberScanViewModel
import de.luigialice.stockscreener.ui.results.MainPane
import de.luigialice.stockscreener.ui.settings.SettingsPane
import de.luigialice.stockscreener.ui.theme.AppBackground
import de.luigialice.stockscreener.ui.theme.AppColors
import de.luigialice.stockscreener.ui.theme.AppTheme
import kotlinx.coroutines.delay

@Composable
fun ScreenerApp() {
    val scope = rememberCoroutineScope()
    val viewModel = rememberScanViewModel()
    val state by viewModel.state.collectAsState()

    // Selbstheilung: hängt ein "Scanne …"-Zustand ohne aktiven Job, nach 3s sauber abschließen
    val scanning = state.isScanning
    LaunchedEffect(scanning) {
        if (scanning) {
            delay(3000)
            viewModel.healIfStuck(viewModel.state.value)
        }
    }

    AppTheme {
        AppBackground(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                if (maxWidth >= 700.dp) {
                    Row(Modifier.fillMaxSize()) {
                        SettingsPane(
                            state, viewModel, scope,
                            Modifier
                                .width(360.dp)
                                .fillMaxHeight()
                                .background(AppColors.panel)
                                .verticalScroll(rememberScrollState())
                        )
                        VerticalDivider(color = AppColors.panelEdge)
                        MainPane(state, viewModel, Modifier.weight(1f))
                    }
                } else {
                    // Schmales Layout (Handy): alles in einem gemeinsamen Scroll, Panes selbst nicht scrollend
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        SettingsPane(state, viewModel, scope, Modifier.fillMaxWidth().background(AppColors.panel))
                        HorizontalDivider(color = AppColors.panelEdge)
                        MainPane(state, viewModel, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

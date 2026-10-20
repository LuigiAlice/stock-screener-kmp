package de.luigialice.stockscreener

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import de.luigialice.stockscreener.ui.PlatformViewModel
import de.luigialice.stockscreener.ui.ScanViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout

actual fun createYahooClient(): HttpClient = HttpClient(Darwin) {
    install(HttpTimeout) {
        connectTimeoutMillis = 20_000
        requestTimeoutMillis = 60_000
    }
}

@Composable
actual fun rememberScanViewModel(): ScanViewModel = remember { ScanViewModel() }

actual fun pickCsvText(onPicked: (String?) -> Unit) = onPicked(null)

actual fun exportCsv(content: String) = Unit

actual fun csvFileDialogsAvailable(): Boolean = false

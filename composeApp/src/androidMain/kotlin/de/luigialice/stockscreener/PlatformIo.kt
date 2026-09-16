package de.luigialice.stockscreener

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import de.luigialice.stockscreener.ui.ScanViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import java.util.concurrent.TimeUnit

actual fun createYahooClient(): HttpClient = HttpClient(OkHttp) {
    install(HttpTimeout) {
        connectTimeoutMillis = TimeUnit.SECONDS.toMillis(20)
        requestTimeoutMillis = TimeUnit.SECONDS.toMillis(60)
    }
}

@Composable
actual fun rememberScanViewModel(): ScanViewModel = viewModel { ScanViewModel() }

actual fun pickCsvText(): String? = null

actual fun exportCsv(content: String) = Unit

actual fun csvFileDialogsAvailable(): Boolean = false

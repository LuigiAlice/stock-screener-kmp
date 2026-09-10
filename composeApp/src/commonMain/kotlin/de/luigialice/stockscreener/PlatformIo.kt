package de.luigialice.stockscreener

import androidx.compose.runtime.Composable
import de.luigialice.stockscreener.ui.ScanViewModel
import io.ktor.client.HttpClient

expect fun pickCsvText(): String?

expect fun exportCsv(content: String)

expect fun createYahooClient(): HttpClient

@Composable
expect fun rememberScanViewModel(): ScanViewModel

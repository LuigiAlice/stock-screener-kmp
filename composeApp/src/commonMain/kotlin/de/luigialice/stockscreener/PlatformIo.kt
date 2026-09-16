package de.luigialice.stockscreener

import androidx.compose.runtime.Composable
import de.luigialice.stockscreener.ui.ScanViewModel
import io.ktor.client.HttpClient

expect fun pickCsvText(): String?

expect fun exportCsv(content: String)

/** Sind Datei-Dialoge für CSV-Import/-Export verfügbar? (nur Desktop) */
expect fun csvFileDialogsAvailable(): Boolean

expect fun createYahooClient(): HttpClient

@Composable
expect fun rememberScanViewModel(): ScanViewModel

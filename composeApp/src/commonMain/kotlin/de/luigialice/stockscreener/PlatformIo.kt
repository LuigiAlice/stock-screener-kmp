package de.luigialice.stockscreener

import androidx.compose.runtime.Composable
import de.luigialice.stockscreener.ui.ScanViewModel
import io.ktor.client.HttpClient

/**
 * Zeigt einen CSV-Öffnen-Dialog und liefert den Text asynchron an `onPicked`
 * (null = abgebrochen/nicht unterstützt).
 */
expect fun pickCsvText(onPicked: (String?) -> Unit)

expect fun exportCsv(content: String)

/** Sind Datei-Dialoge für CSV-Import/-Export verfügbar? (Desktop + Web) */
expect fun csvFileDialogsAvailable(): Boolean

expect fun createYahooClient(): HttpClient

@Composable
expect fun rememberScanViewModel(): ScanViewModel

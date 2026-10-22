package de.luigialice.stockscreener.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import de.luigialice.stockscreener.model.Bar

/** Compose adapters stay outside the scan state coordinator. */
@Composable
internal fun ScanViewModel.rememberBars(ticker: String): List<Bar> =
    bars.collectAsState().value[ticker] ?: emptyList()

@Composable
internal fun ScanViewModel.rememberHasBars(): Boolean =
    bars.collectAsState().value.isNotEmpty()

package de.luigialice.stockscreener

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.window.WindowPlacement
import de.luigialice.stockscreener.ui.ScreenerApp

fun main() = application {
    val state = rememberWindowState(placement = WindowPlacement.Maximized)
    Window(
        onCloseRequest = ::exitApplication,
        title = "Rebound Screener",
        state = state
    ) {
        ScreenerApp()
    }
}

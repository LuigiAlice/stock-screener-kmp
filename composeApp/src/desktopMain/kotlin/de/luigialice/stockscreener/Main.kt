package de.luigialice.stockscreener

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import de.luigialice.stockscreener.ui.ScreenerApp

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Rebound Screener") {
        ScreenerApp()
    }
}

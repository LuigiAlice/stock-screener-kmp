package de.luigialice.stockscreener

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import de.luigialice.stockscreener.ui.ScreenerApp

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport {
        ScreenerApp()
    }
}

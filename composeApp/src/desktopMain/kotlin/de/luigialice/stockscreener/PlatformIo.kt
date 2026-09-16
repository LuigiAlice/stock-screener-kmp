package de.luigialice.stockscreener

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import de.luigialice.stockscreener.ui.ScanViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import java.io.File
import java.util.concurrent.TimeUnit
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

actual fun createYahooClient(): HttpClient = HttpClient(OkHttp) {
    install(HttpTimeout) {
        connectTimeoutMillis = TimeUnit.SECONDS.toMillis(20)
        requestTimeoutMillis = TimeUnit.SECONDS.toMillis(60)
    }
}

@Composable
actual fun rememberScanViewModel(): ScanViewModel = remember { ScanViewModel() }

internal fun runOnEdt(block: () -> Int): Int =
    if (SwingUtilities.isEventDispatchThread()) block()
    else {
        var result = -1
        runCatching {
            SwingUtilities.invokeAndWait { result = block() }
        }
        result
    }

actual fun pickCsvText(): String? {
    val chooser = JFileChooser().apply {
        fileFilter = FileNameExtensionFilter("CSV-Dateien", "csv")
        dialogTitle = "Universe-CSV wählen"
    }
    if (runOnEdt { chooser.showOpenDialog(null) } != JFileChooser.APPROVE_OPTION) return null
    val file = chooser.selectedFile ?: return null
    return runCatching { file.readText() }.getOrNull()
}

actual fun csvFileDialogsAvailable(): Boolean = true

actual fun exportCsv(content: String) {
    val chooser = JFileChooser().apply {
        dialogTitle = "Treffer speichern"
        selectedFile = File("rebound_treffer.csv")
    }
    if (runOnEdt { chooser.showSaveDialog(null) } != JFileChooser.APPROVE_OPTION) return
    chooser.selectedFile?.let { file ->
        val target = if (file.name.endsWith(".csv")) file else File(file.parentFile, file.name + ".csv")
        runCatching { target.writeText(content) }
    }
}

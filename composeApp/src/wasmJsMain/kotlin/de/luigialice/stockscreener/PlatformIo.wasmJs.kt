@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package de.luigialice.stockscreener

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import de.luigialice.stockscreener.ui.ScanViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.URLProtocol
import kotlinx.browser.document
import kotlinx.browser.window
import kotlin.js.JsArray
import kotlin.js.JsString
import kotlin.js.toJsString
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import org.w3c.files.FileReader

/**
 * Externe Hosts laufen im Browser über lokale Apache-/Dev-Server-Proxypfade
 * (CORS: die Zielhost antworten ohne Access-Control-Allow-Origin).
 * Reihenfolge egal, da Prefix-Matching auf den ganzen Host beginnt.
 */
private val proxyHosts = mapOf(
    "query1.finance.yahoo.com" to "proxy/yahoo",
    "www.nasdaqtrader.com" to "proxy/nasdaqtrader",
    "raw.githubusercontent.com" to "proxy/githubraw",
    "en.wikipedia.org" to "proxy/wikipedia",
)

private val LocalProxy = createClientPlugin("LocalProxy") {
    onRequest { request, _ ->
        val mapped = proxyHosts[request.url.host] ?: return@onRequest
        val loc = window.location
        request.url.protocol = if (loc.protocol == "https:") URLProtocol.HTTPS else URLProtocol.HTTP
        request.url.host = loc.hostname
        request.url.port = loc.port.toIntOrNull() ?: if (loc.protocol == "https:") 443 else 80
        // Basispfad der ausgelieferten Seite ("" lokal, "stock-screener" unter /stock-screener/)
        val basePath = loc.pathname.substringBeforeLast('/', "").split('/').filter { it.isNotEmpty() }
        request.url.pathSegments =
            basePath + mapped.split('/') + request.url.pathSegments.filter { it.isNotEmpty() }
    }
}

actual fun createYahooClient(): HttpClient = HttpClient(Js) {
    install(HttpTimeout) {
        connectTimeoutMillis = 20_000
        requestTimeoutMillis = 60_000
    }
    install(LocalProxy)
}

@Composable
actual fun rememberScanViewModel(): ScanViewModel = remember { ScanViewModel() }

actual fun pickCsvText(onPicked: (String?) -> Unit) {
    val input = document.createElement("input") as HTMLInputElement
    input.type = "file"
    input.accept = ".csv,text/csv,text/plain"
    input.onchange = { _: Event ->
        val file = input.files?.item(0)
        if (file == null) {
            onPicked(null)
        } else {
            val reader = FileReader()
            reader.onload = { _: Event -> onPicked((reader.result as? JsString)?.toString()) }
            reader.onerror = { _: Event -> onPicked(null) }
            reader.readAsText(file)
        }
    }
    input.click()
}

actual fun csvFileDialogsAvailable(): Boolean = true

actual fun exportCsv(content: String) {
    val parts = JsArray<JsAny?>()
    parts[0] = content.toJsString()
    val blob = Blob(parts, BlobPropertyBag(type = "text/csv;charset=utf-8"))
    val url = URL.createObjectURL(blob)
    val a = document.createElement("a") as HTMLAnchorElement
    a.href = url
    a.download = "rebound_treffer.csv"
    document.body?.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
}

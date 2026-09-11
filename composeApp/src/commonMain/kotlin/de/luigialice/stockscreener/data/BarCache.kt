package de.luigialice.stockscreener.data

import de.luigialice.stockscreener.model.Bar
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.io.FileInputStream
import java.io.ByteArrayOutputStream

private val cacheJson = Json { ignoreUnknownKeys = true }

@Serializable
internal data class CachedBar(val date: String, val close: Double, val volume: Double)

@Serializable
internal data class CachedBars(val fetchedAtMs: Long, val bars: List<CachedBar>)

/**
 * Festplatten-Cache für Kurshistorie: eine gzippte JSON-Datei pro Ticker.
 * Einträge gelten `ttlMs` (Standard: 1 Stunde); ältere werden beim Scan aufgefrischt.
 */
class BarCache(private val dir: File?, private val ttlMs: Long = 60 * 60 * 1000L) {

    private val memory = mutableMapOf<String, CachedBars>()

    /** Bars nur wenn innerhalb der Gültigkeitsfrist, sonst null. */
    fun freshBars(ticker: String): List<Bar>? {
        val entry = readEntry(ticker) ?: return null
        if (System.currentTimeMillis() - entry.fetchedAtMs > ttlMs) return null
        return entry.bars.map { Bar(kotlinx.datetime.LocalDate.parse(it.date), it.close, it.volume) }
    }

    /** Bars unabhängig von der Frist (z. B. für Auswertungen). */
    fun anyBars(ticker: String): List<Bar>? = readEntry(ticker)?.bars?.map {
        Bar(kotlinx.datetime.LocalDate.parse(it.date), it.close, it.volume)
    }

    fun store(ticker: String, bars: List<Bar>) {
        val base = dir ?: return
        if (bars.isEmpty()) return
        runCatching {
            base.mkdirs()
            val entry = CachedBars(
                fetchedAtMs = System.currentTimeMillis(),
                bars = bars.map { CachedBar(it.date.toString(), it.close, it.volume) }
            )
            writeGzip(fileFor(ticker), cacheJson.encodeToString(CachedBars.serializer(), entry))
            memory[ticker] = entry
        }
    }

    private fun fileFor(ticker: String): File =
        File(dir, ticker.replace(Regex("[^A-Za-z0-9-]"), "_") + ".json.gz")

    private fun readEntry(ticker: String): CachedBars? {
        memory[ticker]?.let { return it }
        val f = fileFor(ticker)
        if (!f.isFile) return null
        val text = runCatching { readGzip(f) }.getOrNull() ?: return null
        val entry = runCatching { cacheJson.decodeFromString(CachedBars.serializer(), text) }.getOrNull() ?: return null
        memory[ticker] = entry
        return entry
    }

    private fun writeGzip(file: File, content: String) {
        GZIPOutputStream(FileOutputStream(file).buffered()).use { it.write(content.encodeToByteArray()) }
    }

    private fun readGzip(file: File): String =
        GZIPInputStream(FileInputStream(file).buffered()).use { ins ->
            val out = ByteArrayOutputStream()
            ins.copyTo(out)
            out.toByteArray().decodeToString()
        }
}

/** Cache-Verzeichnis der Plattform; null = Cache deaktiviert. */
expect fun barCacheDirectory(): File?

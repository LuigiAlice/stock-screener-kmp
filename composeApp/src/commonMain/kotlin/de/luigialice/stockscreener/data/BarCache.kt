package de.luigialice.stockscreener.data

import de.luigialice.stockscreener.model.Bar
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.GzipSink
import okio.GzipSource
import okio.Path
import okio.buffer
import okio.use

private val cacheJson = Json { ignoreUnknownKeys = true }

@Serializable
internal data class CachedBar(val date: String, val close: Double, val volume: Double)

@Serializable
internal data class CachedBars(val fetchedAtMs: Long, val bars: List<CachedBar>)

/**
 * Festplatten-Cache für Kurshistorie: eine gzippte JSON-Datei pro Ticker (okio, multiplattform).
 * Einträge gelten `ttlMs` (Standard: 1 Stunde); ältere werden beim Scan aufgefrischt.
 */
class BarCache(private val baseDir: Path?, private val ttlMs: Long = 60 * 60 * 1000L) {

    private val memory = mutableMapOf<String, CachedBars>()
    private val fs = FileSystem.SYSTEM

    /** Bars nur wenn innerhalb der Gültigkeitsfrist, sonst null. */
    fun freshBars(ticker: String): List<Bar>? {
        val entry = readEntry(ticker) ?: return null
        if (currentTimeMs() - entry.fetchedAtMs > ttlMs) return null
        return entry.bars.map { Bar(kotlinx.datetime.LocalDate.parse(it.date), it.close, it.volume) }
    }

    /** Bars unabhängig von der Frist (z. B. für Auswertungen). */
    fun anyBars(ticker: String): List<Bar>? = readEntry(ticker)?.bars?.map {
        Bar(kotlinx.datetime.LocalDate.parse(it.date), it.close, it.volume)
    }

    fun store(ticker: String, bars: List<Bar>) {
        val base = baseDir ?: return
        if (bars.isEmpty()) return
        runCatching {
            fs.createDirectories(base)
            val entry = CachedBars(
                fetchedAtMs = currentTimeMs(),
                bars = bars.map { CachedBar(it.date.toString(), it.close, it.volume) }
            )
            fs.sink(fileFor(base, ticker)).use { raw ->
                GzipSink(raw).buffer().use { gz ->
                    gz.writeUtf8(cacheJson.encodeToString(CachedBars.serializer(), entry))
                }
            }
            memory[ticker] = entry
        }
    }

    internal fun fileFor(base: Path, ticker: String): Path =
        base / (ticker.replace(Regex("[^A-Za-z0-9-]"), "_") + ".json.gz")

    private fun readEntry(ticker: String): CachedBars? {
        memory[ticker]?.let { return it }
        val base = baseDir ?: return null
        val f = fileFor(base, ticker)
        if (!fs.exists(f)) return null
        val text = runCatching {
            fs.source(f).use { raw ->
                GzipSource(raw).buffer().readUtf8()
            }
        }.getOrNull() ?: return null
        val entry = runCatching { cacheJson.decodeFromString(CachedBars.serializer(), text) }.getOrNull() ?: return null
        memory[ticker] = entry
        return entry
    }
}

internal expect fun currentTimeMs(): Long

/** Cache-Verzeichnis der Plattform; null = Cache deaktiviert. */
expect fun barCacheDirectory(): Path?

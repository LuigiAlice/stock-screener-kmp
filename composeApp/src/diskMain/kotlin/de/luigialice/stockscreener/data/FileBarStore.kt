package de.luigialice.stockscreener.data

import okio.FileSystem
import okio.GzipSink
import okio.GzipSource
import okio.Path
import okio.buffer
import okio.use

/**
 * Datei-basierter BarStore: eine gzippte JSON-Datei pro Schlüssel unter `baseDir`.
 * Läuft auf Desktop, Android und iOS (okio, kein wasmJs-Variant – daher hier und
 * nicht in commonMain).
 */
class FileBarStore(private val baseDir: Path) : BarStore {

    private val fs = FileSystem.SYSTEM

    override fun load(key: String): String? {
        val f = fileFor(key)
        if (!fs.exists(f)) return null
        return runCatching {
            fs.source(f).use { raw ->
                GzipSource(raw).buffer().readUtf8()
            }
        }.getOrNull()
    }

    override fun save(key: String, value: String) {
        runCatching {
            fs.createDirectories(baseDir)
            fs.sink(fileFor(key)).use { raw ->
                GzipSink(raw).buffer().use { gz ->
                    gz.writeUtf8(value)
                }
            }
        }
    }

    internal fun fileFor(key: String): Path = baseDir / "$key.json.gz"
}

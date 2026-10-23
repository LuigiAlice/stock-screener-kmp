package de.luigialice.stockscreener.data

import de.luigialice.stockscreener.model.Bar
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val cacheJson = Json { ignoreUnknownKeys = true }

@Serializable
internal data class CachedBar(val date: String, val close: Double, val volume: Double)

@Serializable
internal data class CachedBars(
    val fetchedAtMs: Long,
    val bars: List<CachedBar>,
    /** Name aus den Chart-Metadaten; fehlt bei Einträgen aus älteren Versionen. */
    val name: String? = null
)

/**
 * Persistenz für den Kurshistorie-Cache: schlüsselbasiert, Codierung Sache der
 * Implementierung (Datei mit gzip auf Desktop/Android/iOS, localStorage im Browser).
 */
interface BarStore {
    fun load(key: String): String?
    fun save(key: String, value: String)
}

/** Einfacher In-Memory-Store (Tests, Fallback ohne Persistenz). */
class InMemoryBarStore : BarStore {
    private val data = mutableMapOf<String, String>()

    val keys: Set<String> get() = data.keys

    override fun load(key: String): String? = data[key]

    override fun save(key: String, value: String) {
        data[key] = value
    }
}

/** Ticker -> sicherer Speicherschlüssel (Dateiname auf Desktop/Android/iOS). */
internal fun barStoreKey(ticker: String): String =
    ticker.replace(Regex("[^A-Za-z0-9-]"), "_")

/**
 * In-Memory-Cache für Kurshistorie mit TTL (Standard: 1 Stunde) und optionalem
 * plattformspezifischem Persistent-Store. Einträge gelten `ttlMs`; ältere werden
 * beim Scan aufgefrischt.
 */
class BarCache(private val store: BarStore?, private val ttlMs: Long = 60 * 60 * 1000L) {

    private val memory = mutableMapOf<String, CachedBars>()

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

    /** Zum Ticker gespeicherter Name (aus den Kursdaten), sofern beim Ablegen bekannt. */
    fun cachedName(ticker: String): String? = readEntry(ticker)?.name?.takeIf { it.isNotBlank() }

    fun store(ticker: String, bars: List<Bar>, name: String? = null) {
        if (bars.isEmpty()) return
        val entry = CachedBars(
            fetchedAtMs = currentTimeMs(),
            bars = bars.map { CachedBar(it.date.toString(), it.close, it.volume) },
            name = name?.takeIf { it.isNotBlank() }
        )
        runCatching {
            store?.save(barStoreKey(ticker), cacheJson.encodeToString(CachedBars.serializer(), entry))
            memory[ticker] = entry
        }
    }

    private fun readEntry(ticker: String): CachedBars? {
        memory[ticker]?.let { return it }
        val text = store?.load(barStoreKey(ticker)) ?: return null
        val entry = runCatching { cacheJson.decodeFromString(CachedBars.serializer(), text) }.getOrNull() ?: return null
        memory[ticker] = entry
        return entry
    }
}

internal expect fun currentTimeMs(): Long

/** Persistent-Store der Plattform; null = Cache nur in-memory. */
expect fun createBarStore(): BarStore?

package de.luigialice.stockscreener

import de.luigialice.stockscreener.data.BarCache
import de.luigialice.stockscreener.data.InMemoryBarStore
import de.luigialice.stockscreener.data.barStoreKey
import de.luigialice.stockscreener.model.Bar
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BarCacheTest {

    private fun bars(vararg closes: Double): List<Bar> =
        closes.mapIndexed { i, c -> Bar(LocalDate(2026, 1, 1).plus(i, DateTimeUnit.DAY), c, 1000.0 * (i + 1)) }

    @Test
    fun storesAndReloadsBars() {
        val store = InMemoryBarStore()
        val data = bars(100.0, 101.5, 99.0)
        BarCache(store).store("AAPL", data)

        val reloaded = BarCache(store).freshBars("AAPL")
        assertNotNull(reloaded)
        assertEquals(3, reloaded.size)
        assertEquals(101.5, reloaded[1].close, 1e-9)
        assertEquals(2000.0, reloaded[1].volume, 1e-9)
        assertEquals(LocalDate(2026, 1, 2), reloaded[1].date)
    }

    @Test
    fun freshWithinTtlStaleAfterTtl() {
        val data = bars(100.0)

        val fresh = BarCache(InMemoryBarStore(), 60 * 60 * 1000)
        fresh.store("MSFT", data)
        assertNotNull(fresh.freshBars("MSFT"))

        // Negative TTL: jeder Eintrag ist sofort abgelaufen
        val store = InMemoryBarStore()
        val stale = BarCache(store, -1)
        stale.store("MSFT", data)
        assertNull(stale.freshBars("MSFT")) // abgelaufen
        assertNotNull(stale.anyBars("MSFT")) // Rohdaten trotzdem da
    }

    @Test
    fun sanitizesStoreKeys() {
        assertEquals("_GSPC", barStoreKey("^GSPC"))
        assertEquals("BRK-B", barStoreKey("BRK-B"))
        assertEquals("ABC_DEF", barStoreKey("ABC.DEF"))

        val store = InMemoryBarStore()
        val cache = BarCache(store)
        cache.store("^GSPC", bars(5000.0))
        cache.store("BRK-B", bars(450.0))
        assertTrue(store.keys.isNotEmpty())
        assertTrue(store.keys.all { it.matches(Regex("[A-Za-z0-9_-]+")) })
        assertEquals(5000.0, BarCache(store).freshBars("^GSPC")!![0].close, 1e-9)
    }

    @Test
    fun missingTickerReturnsNull() {
        val cache = BarCache(InMemoryBarStore())
        assertNull(cache.freshBars("UNKNOWN"))
        assertNull(cache.anyBars("UNKNOWN"))
    }

    @Test
    fun emptyBarsNotStored() {
        val store = InMemoryBarStore()
        val cache = BarCache(store)
        cache.store("EMPTY", emptyList())
        assertNull(cache.freshBars("EMPTY"))
        assertTrue(store.keys.isEmpty())
    }

    @Test
    fun nullStoreDisablesPersistence() {
        val cache = BarCache(null)
        cache.store("AAPL", bars(100.0))
        assertNotNull(cache.freshBars("AAPL")) // In-Memory greift auch ohne Store
        assertNull(BarCache(null).freshBars("AAPL")) // aber keine Persistenz
    }
}

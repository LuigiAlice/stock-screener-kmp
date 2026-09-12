package de.luigialice.stockscreener

import de.luigialice.stockscreener.data.BarCache
import de.luigialice.stockscreener.model.Bar
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BarCacheTest {

    private val fs = FileSystem.SYSTEM

    private fun bars(vararg closes: Double): List<Bar> =
        closes.mapIndexed { i, c -> Bar(LocalDate(2026, 1, 1).plus(i, DateTimeUnit.DAY), c, 1000.0 * (i + 1)) }

    private fun cacheWithTtl(ttlMs: Long): Pair<BarCache, Path> {
        val dir = (System.getProperty("java.io.tmpdir") + "/barcache-test-" + System.nanoTime()).toPath()
        fs.deleteRecursively(dir)
        return BarCache(dir, ttlMs) to dir
    }

    private fun cleanup(dir: Path) = fs.deleteRecursively(dir)

    @Test
    fun storesAndReloadsBars() {
        val (cache, dir) = cacheWithTtl(60 * 60 * 1000)
        val data = bars(100.0, 101.5, 99.0)
        cache.store("AAPL", data)

        val reloaded = BarCache(dir, 60 * 60 * 1000).freshBars("AAPL")
        assertNotNull(reloaded)
        assertEquals(3, reloaded.size)
        assertEquals(101.5, reloaded[1].close, 1e-9)
        assertEquals(2000.0, reloaded[1].volume, 1e-9)
        assertEquals(LocalDate(2026, 1, 2), reloaded[1].date)
        cleanup(dir)
    }

    @Test
    fun freshWithinTtlStaleAfterTtl() {
        val (shortTtl, dir) = cacheWithTtl(50)
        val data = bars(100.0)
        shortTtl.store("MSFT", data)
        assertNotNull(shortTtl.freshBars("MSFT"))
        Thread.sleep(80)
        assertNull(shortTtl.freshBars("MSFT")) // abgelaufen
        assertNotNull(shortTtl.anyBars("MSFT")) // Rohdaten trotzdem da
        cleanup(dir)
    }

    @Test
    fun sanitizesTickerForFilename() {
        val (cache, dir) = cacheWithTtl(60 * 60 * 1000)
        cache.store("^GSPC", bars(5000.0))
        cache.store("BRK-B", bars(450.0))
        val names = fs.list(dir).map { it.name }
        assertTrue(names.all { it.matches(Regex("[A-Za-z0-9_-]+\\.json\\.gz")) })
        assertEquals(5000.0, BarCache(dir, 60 * 60 * 1000).freshBars("^GSPC")!![0].close, 1e-9)
        cleanup(dir)
    }

    @Test
    fun missingTickerReturnsNull() {
        val (cache, dir) = cacheWithTtl(60 * 60 * 1000)
        assertNull(cache.freshBars("UNKNOWN"))
        assertNull(cache.anyBars("UNKNOWN"))
        cleanup(dir)
    }

    @Test
    fun emptyBarsNotStored() {
        val (cache, dir) = cacheWithTtl(60 * 60 * 1000)
        cache.store("EMPTY", emptyList())
        assertNull(cache.freshBars("EMPTY"))
        assertTrue(!fs.exists(dir) || fs.list(dir).isEmpty())
        cleanup(dir)
    }
}

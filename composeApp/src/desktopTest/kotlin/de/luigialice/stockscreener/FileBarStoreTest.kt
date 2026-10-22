package de.luigialice.stockscreener

import de.luigialice.stockscreener.data.BarCache
import de.luigialice.stockscreener.data.FileBarStore
import de.luigialice.stockscreener.data.barStoreKey
import de.luigialice.stockscreener.model.Bar
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FileBarStoreTest {

    private val fs = FileSystem.SYSTEM

    private fun tempDir() =
        (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "filebarstore-test-${kotlin.random.Random.nextInt(1000000)}").also {
            fs.deleteRecursively(it)
        }

    private fun bars(vararg closes: Double): List<Bar> =
        closes.mapIndexed { i, c -> Bar(LocalDate(2026, 1, 1).plus(i, DateTimeUnit.DAY), c, 1000.0 * (i + 1)) }

    @Test
    fun persistsAcrossStoreInstances() {
        val dir = tempDir()
        val data = bars(100.0, 101.5, 99.0)
        BarCache(FileBarStore(dir)).store("AAPL", data)

        val reloaded = BarCache(FileBarStore(dir)).freshBars("AAPL")
        assertNotNull(reloaded)
        assertEquals(3, reloaded.size)
        assertEquals(101.5, reloaded[1].close, 1e-9)
        assertEquals(2000.0, reloaded[1].volume, 1e-9)
        assertEquals(LocalDate(2026, 1, 2), reloaded[1].date)
        fs.deleteRecursively(dir)
    }

    @Test
    fun writesSanitizedFilenames() {
        val dir = tempDir()
        val store = FileBarStore(dir)
        BarCache(store).store("^GSPC", bars(5000.0))
        BarCache(store).store("BRK-B", bars(450.0))

        val names = fs.list(dir).map { it.name }
        assertTrue(names.isNotEmpty())
        assertTrue(names.all { it.matches(Regex("[A-Za-z0-9_-]+\\.json\\.gz")) }, "Dateinamen: $names")
        assertEquals("_GSPC.json.gz", store.fileFor(barStoreKey("^GSPC")).name)
        assertEquals(5000.0, BarCache(FileBarStore(dir)).freshBars("^GSPC")!![0].close, 1e-9)
        fs.deleteRecursively(dir)
    }

    @Test
    fun loadMissingReturnsNull() {
        val dir = tempDir()
        assertEquals(null, FileBarStore(dir).load("MISSING"))
        fs.deleteRecursively(dir)
    }
}

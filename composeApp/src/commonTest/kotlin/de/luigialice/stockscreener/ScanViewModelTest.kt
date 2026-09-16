package de.luigialice.stockscreener

import de.luigialice.stockscreener.data.BarCache
import de.luigialice.stockscreener.data.currentTimeMs
import de.luigialice.stockscreener.model.Bar
import de.luigialice.stockscreener.model.Universe
import de.luigialice.stockscreener.model.ScanMode
import de.luigialice.stockscreener.model.ScanParams
import de.luigialice.stockscreener.model.ScanSettings
import de.luigialice.stockscreener.ui.ScanState
import de.luigialice.stockscreener.ui.ScanViewModel
import de.luigialice.stockscreener.ui.isScanning
import de.luigialice.stockscreener.ui.results.buildImportedResult
import de.luigialice.stockscreener.screener.Screener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScanViewModelTest {

    private val fs = FileSystem.SYSTEM

    private fun tempDir(): Path =
        FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "svm-test-${currentTimeMs()}-${Random.nextInt(100000)}"

    private fun bars(): List<Bar> {
        // 320 "Handelstage": 60 Tage Abwärtstrend, danach Erholung mit wechselnden Ausschlägen
        val out = mutableListOf<Bar>()
        var close = 200.0
        for (i in 0 until 320) {
            close = if (i < 60) close - 0.8 else close * (1.0 + 0.002) + (if (i % 5 == 0) -0.9 else 0.7)
            out.add(Bar(LocalDate(2024, 1, 1).plus(i, DateTimeUnit.DAY), close, 1_000_000.0 + i * 1000))
        }
        return out
    }

    private fun seedCache(dir: Path): String {
        BarCache(dir, 60 * 60 * 1000).store("TEST", bars())
        return "TEST"
    }

    private fun params(mode: ScanMode) = ScanParams(mode, 21, -12.0, 35.0, 48.0, 1.3)

    private suspend fun awaitBars(vm: ScanViewModel, timeoutMs: Long = 5000) {
        val deadline = currentTimeMs() + timeoutMs
        while (currentTimeMs() < deadline) {
            if (vm.hasBars()) return
            delay(25)
        }
        error("Kurse wurden nicht geladen (kein Netzwerk im Test erlaubt)")
    }

    private suspend fun awaitMode(vm: ScanViewModel, mode: ScanMode, timeoutMs: Long = 5000): ScanState.Done {
        val deadline = currentTimeMs() + timeoutMs
        while (currentTimeMs() < deadline) {
            val s = vm.state.value
            if (s is ScanState.Done && !s.isScanning && s.result.settings.mode == mode) return s
            delay(25)
        }
        error("Modus $mode kam nie an; letzter Stand: ${vm.state.value}")
    }

    @Test
    fun modeSwitchRescansWithNewCriteria() = kotlinx.coroutines.runBlocking {
        val dir = tempDir()
        fs.deleteRecursively(dir)
        val ticker = seedCache(dir)
        val vm = ScanViewModel(barCache = BarCache(dir, 60 * 60 * 1000))
        val scope = CoroutineScope(Job() + Dispatchers.Default)

        val rowA = Screener.calcRow(ticker, "Test", bars(), params(ScanMode.REBOUND_START))!!
        vm.loadResults(buildImportedResult(listOf(rowA)))
        vm.loadBars(ticker, scope)
        awaitBars(vm)

        vm.revalidate(params(ScanMode.REBOUND_START), ScanSettings(1, false, ScanMode.REBOUND_START), listOf(ticker), 10, 0, scope)
        assertEquals(ScanMode.REBOUND_START, awaitMode(vm, ScanMode.REBOUND_START).result.settings.mode)

        vm.revalidate(params(ScanMode.ONGOING_RECOVERY), ScanSettings(1, false, ScanMode.ONGOING_RECOVERY), listOf(ticker), 10, 0, scope)
        val doneB = awaitMode(vm, ScanMode.ONGOING_RECOVERY)
        assertEquals(5, doneB.result.rows.first().criteria.size)
        assertTrue(doneB.result.rows.first().criteria.keys.any { it.contains("SMA50") })

        fs.deleteRecursively(dir)
    }

    @Test
    fun rapidModeTogglingEndsInLastModeAndNotScanning() = kotlinx.coroutines.runBlocking {
        val dir = tempDir()
        fs.deleteRecursively(dir)
        val ticker = seedCache(dir)
        val vm = ScanViewModel(barCache = BarCache(dir, 60 * 60 * 1000))
        val scope = CoroutineScope(Job() + Dispatchers.Default)

        val rowA = Screener.calcRow(ticker, "Test", bars(), params(ScanMode.REBOUND_START))!!
        vm.loadResults(buildImportedResult(listOf(rowA)))
        vm.loadBars(ticker, scope)
        awaitBars(vm)

        repeat(6) { i ->
            val mode = if (i % 2 == 0) ScanMode.ONGOING_RECOVERY else ScanMode.REBOUND_START
            vm.revalidate(params(mode), ScanSettings(1, false, mode), listOf(ticker), 21, 0, scope)
            delay(40)
        }

        val done = awaitMode(vm, ScanMode.REBOUND_START)
        assertEquals(1, done.scanned) // vollständig abgeschlossen
        fs.deleteRecursively(dir)
    }

    @Test
    fun cancelKeepsResultsWhenNoDownloadIsRunning() = kotlinx.coroutines.runBlocking {
        val dir = tempDir()
        fs.deleteRecursively(dir)
        val ticker = seedCache(dir)
        val vm = ScanViewModel(barCache = BarCache(dir, 60 * 60 * 1000))
        val scope = CoroutineScope(Job() + Dispatchers.Default)

        val row = Screener.calcRow(ticker, "Test", bars(), params(ScanMode.REBOUND_START))!!
        vm.loadResults(buildImportedResult(listOf(row)))
        vm.loadBars(ticker, scope)
        awaitBars(vm)

        vm.revalidate(params(ScanMode.ONGOING_RECOVERY), ScanSettings(1, false, ScanMode.ONGOING_RECOVERY), listOf(ticker), 10, 0, scope)
        awaitMode(vm, ScanMode.ONGOING_RECOVERY)

        vm.cancel()
        val s = vm.state.value
        assertTrue(s is ScanState.Done, "Ergebnisliste darf beim Abbrechen eines Re-Scores nicht verschwinden, war $s")
        assertTrue(!s.isScanning)
        fs.deleteRecursively(dir)
    }

    @Test
    fun emptyUniverseFailsInsteadOfScanningForever() = kotlinx.coroutines.runBlocking {
        val vm = ScanViewModel(barCache = BarCache(null))
        val scope = CoroutineScope(Job() + Dispatchers.Default)
        vm.runScan(
            Universe(emptyList(), emptyMap(), emptyMap()),
            params(ScanMode.REBOUND_START), ScanSettings(1, false, ScanMode.REBOUND_START),
            25, 0, scope
        )
        val s = vm.state.value
        assertTrue(s is ScanState.Failed, "Erwartet Failed, war $s")
        assertTrue(!vm.downloadRunning())
    }

    @Test
    fun healIfStuckIsNoOpForHealthyStates() = kotlinx.coroutines.runBlocking {
        val dir = tempDir()
        fs.deleteRecursively(dir)
        val ticker = seedCache(dir)
        val vm = ScanViewModel(barCache = BarCache(dir, 60 * 60 * 1000))
        val scope = CoroutineScope(Job() + Dispatchers.Default)

        // Idle bleibt Idle
        vm.healIfStuck(vm.state.value)
        assertEquals(ScanState.Idle, vm.state.value)

        // fertig abgeschlossener Done bleibt unangetastet
        val row = Screener.calcRow(ticker, "Test", bars(), params(ScanMode.REBOUND_START))!!
        vm.loadResults(buildImportedResult(listOf(row)))
        val before = vm.state.value
        vm.healIfStuck(before)
        assertEquals(before, vm.state.value)
        fs.deleteRecursively(dir)
    }
}

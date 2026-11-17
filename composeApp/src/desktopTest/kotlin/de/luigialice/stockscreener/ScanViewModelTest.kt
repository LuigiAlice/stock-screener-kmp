package de.luigialice.stockscreener

import de.luigialice.stockscreener.data.BarCache
import de.luigialice.stockscreener.data.FileBarStore
import de.luigialice.stockscreener.data.currentTimeMs
import de.luigialice.stockscreener.model.Bar
import de.luigialice.stockscreener.model.StockInfo
import de.luigialice.stockscreener.model.Universe
import de.luigialice.stockscreener.model.ScanMode
import de.luigialice.stockscreener.model.ScanParams
import de.luigialice.stockscreener.model.ScanSettings
import de.luigialice.stockscreener.ui.ScanState
import de.luigialice.stockscreener.ui.ScanViewModel
import de.luigialice.stockscreener.ui.isScanning
import de.luigialice.stockscreener.ui.results.buildImportedResult
import de.luigialice.stockscreener.screener.Screener
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.runBlocking
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
        BarCache(FileBarStore(dir), 60 * 60 * 1000).store("TEST", bars())
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

 @Test fun ruleChangeAfterCancellationCompletesTheUniverse() = runBlocking {
  val cache=BarCache(null)
  val bars=List(300) { Bar(LocalDate(2025,1,1),100.0+it%7,1000.0) }
  cache.store("AAA",bars,"Alpha"); cache.store("BBB",bars,"Beta")
  val vm=ScanViewModel(barCache=cache)
  val p=ScanParams(ScanMode.REBOUND_START)
  val settings=ScanSettings(1,false,p.mode)
  val stopper=launch(start=CoroutineStart.UNDISPATCHED) {
   vm.state.first { it is ScanState.Done && it.scanned==1 }
   vm.cancel()
  }
  vm.runScan(Universe(mapOf("AAA" to StockInfo("Alpha"),"BBB" to StockInfo("Beta"))),p,settings,1,0,this)
  stopper.join()
  while(vm.jobActive()) delay(1)
  vm.revalidate(p.copy(maxPerf=-20.0),settings,listOf("AAA","BBB"),1,0,this)
  while(vm.jobActive()) delay(1)
  val done=vm.state.value as ScanState.Done
  assertEquals(2,done.scanned)
  assertEquals(2,done.result.rows.size)
  assertEquals(0,done.result.failed.size)
 }

    @Test
    fun importUpdatesSelectedScanMode() {
        val vm = ScanViewModel(barCache = BarCache(null))
        val mode = ScanMode.ONGOING_RECOVERY
        val row = Screener.calcRow("TEST", "Test", bars(), params(mode))!!
        vm.loadResults(buildImportedResult(listOf(row)))
        assertEquals(mode, vm.selectedMode.value)
        vm.selectMode(ScanMode.REBOUND_START)
        vm.loadResults(buildImportedResult(listOf(row)))
        assertEquals(mode, vm.selectedMode.value)
    }

    @Test
    fun cachedScanDoesNotRenewDownloadTimestamp() = kotlinx.coroutines.runBlocking {
        val store = de.luigialice.stockscreener.data.InMemoryBarStore()
        val cache = BarCache(store)
        cache.store("TEST", bars(), "Test")
        val key = store.keys.single()
        val original = store.load(key)
        delay(10)
        val vm = ScanViewModel(barCache = cache)
        vm.runScan(Universe(mapOf("TEST" to de.luigialice.stockscreener.model.StockInfo("Test"))),
            params(ScanMode.REBOUND_START), ScanSettings(1, false, ScanMode.REBOUND_START), 10, 0, this)
        while (vm.jobActive()) delay(10)
        assertEquals(original, store.load(key))
        assertEquals(1, (vm.state.value as ScanState.Done).result.rows.size)
    }

    @Test
    fun automaticRevalidationPreservesImportEvenWithOneChartLoaded() = kotlinx.coroutines.runBlocking {
        val cache = BarCache(null)
        cache.store("AAA", bars(), "Alpha")
        cache.store("BBB", bars(), "Beta")
        val vm = ScanViewModel(barCache = cache)
        val rows = listOf("AAA", "BBB").map {
            Screener.calcRow(it, it, bars(), params(ScanMode.REBOUND_START))!!
        }
        vm.loadResults(buildImportedResult(rows))
        val imported = vm.state.value
        val p = params(ScanMode.REBOUND_START)
        val settings = ScanSettings(4, true, ScanMode.REBOUND_START)
        vm.revalidate(p, settings, listOf("AAA", "BBB"), 10, 0, this, automatic = true)
        vm.loadBars("AAA", this)
        awaitBars(vm)
        vm.revalidate(p, settings, listOf("AAA", "BBB"), 10, 0, this, automatic = true)
        assertEquals(imported, vm.state.value)
        // A deliberate rule change must evaluate the entire imported universe.
        vm.revalidate(p.copy(maxPerf = -20.0), settings, listOf("AAA", "BBB"), 10, 0, this, automatic = true)
        while (vm.jobActive()) delay(10)
        assertEquals(2, (vm.state.value as ScanState.Done).result.rows.size)
    }

    @Test
    fun modeSwitchRescansWithNewCriteria() = kotlinx.coroutines.runBlocking {
        val dir = tempDir()
        fs.deleteRecursively(dir)
        val ticker = seedCache(dir)
        val vm = ScanViewModel(barCache = BarCache(FileBarStore(dir), 60 * 60 * 1000))
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
        val vm = ScanViewModel(barCache = BarCache(FileBarStore(dir), 60 * 60 * 1000))
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
        val vm = ScanViewModel(barCache = BarCache(FileBarStore(dir), 60 * 60 * 1000))
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
            Universe(emptyMap()),
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
        val vm = ScanViewModel(barCache = BarCache(FileBarStore(dir), 60 * 60 * 1000))
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

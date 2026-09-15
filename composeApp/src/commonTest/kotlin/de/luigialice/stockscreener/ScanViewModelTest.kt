package de.luigialice.stockscreener

import de.luigialice.stockscreener.data.BarCache
import de.luigialice.stockscreener.model.Bar
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScanViewModelTest {

    private val fs = FileSystem.SYSTEM

    private fun bars(): List<Bar> {
        // 320 Handelstage: 60 Tage Abwärtstrend, danach Erholung
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

    private suspend fun awaitDone(vm: ScanViewModel, timeoutMs: Long = 5000): ScanState.Done {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val s = vm.state.value
            if (s is ScanState.Done && !s.isScanning) return s
            delay(25)
        }
        error("Kein Done-Zustand: ${vm.state.value}")
    }

    @Test
    fun modeSwitchRescansWithNewCriteria() = kotlinx.coroutines.runBlocking {
        val dir = (System.getProperty("java.io.tmpdir") + "/svm-test-" + System.nanoTime()).toPath()
        fs.deleteRecursively(dir)
        val ticker = seedCache(dir)
        val vm = ScanViewModel(barCache = BarCache(dir, 60 * 60 * 1000))
        val scope = CoroutineScope(Job() + Dispatchers.Default)

        // Universum + Kurse seeden (ohne Netzwerk)
        val rowA = Screener.calcRow(
            ticker, "Test", bars(),
            ScanParams(ScanMode.REBOUND_START, 21, -12.0, 35.0, 48.0, 1.3)
        )!!
        vm.loadResults(buildImportedResult(listOf(rowA)))
        vm.loadBars(ticker, scope)
        delay(300) // loadBars abwarten

        // Modus A: Rebound
        vm.revalidate(
            ScanParams(ScanMode.REBOUND_START, 21, -12.0, 35.0, 48.0, 1.3),
            ScanSettings(1, false, ScanMode.REBOUND_START),
            listOf(ticker), 10, 0, scope
        )
        val doneA = awaitDone(vm)
        assertEquals(ScanMode.REBOUND_START, doneA.result.settings.mode)

        // Modus B: Laufende Erholung (5 Kriterien statt 8)
        vm.revalidate(
            ScanParams(ScanMode.ONGOING_RECOVERY, 21, -12.0, 35.0, 48.0, 1.3),
            ScanSettings(1, false, ScanMode.ONGOING_RECOVERY),
            listOf(ticker), 10, 0, scope
        )
        val deadline = System.currentTimeMillis() + 5000
        var doneB: ScanState.Done? = null
        while (System.currentTimeMillis() < deadline) {
            val s = vm.state.value
            if (s is ScanState.Done && !s.isScanning && s.result.settings.mode == ScanMode.ONGOING_RECOVERY) {
                doneB = s; break
            }
            delay(25)
        }
        val finalB = doneB ?: error("Re-Score auf ONGOING_RECOVERY kam nie an; letzter Stand: ${vm.state.value}")
        assertEquals(ScanMode.ONGOING_RECOVERY, finalB.result.settings.mode)
        assertEquals(5, finalB.result.rows.first().criteria.size)
        assertTrue(finalB.result.rows.first().criteria.keys.any { it.contains("SMA50") })
        cleanup(dir)
    }

    @Test
    fun rapidModeTogglingNeverLeavesScanningStuck() = kotlinx.coroutines.runBlocking {
        val dir = (System.getProperty("java.io.tmpdir") + "/svm-test-" + System.nanoTime()).toPath()
        fs.deleteRecursively(dir)
        val ticker = seedCache(dir)
        val vm = ScanViewModel(barCache = BarCache(dir, 60 * 60 * 1000))
        val scope = CoroutineScope(Job() + Dispatchers.Default)

        val rowA = Screener.calcRow(
            ticker, "Test", bars(),
            ScanParams(ScanMode.REBOUND_START, 21, -12.0, 35.0, 48.0, 1.3)
        )!!
        vm.loadResults(buildImportedResult(listOf(rowA)))
        vm.loadBars(ticker, scope)
        delay(300)

        // Hin und her schalten wie der User
        repeat(6) { i ->
            val mode = if (i % 2 == 0) ScanMode.ONGOING_RECOVERY else ScanMode.REBOUND_START
            vm.revalidate(
                ScanParams(mode, 21, -12.0, 35.0, 48.0, 1.3),
                ScanSettings(1, false, mode),
                listOf(ticker), 21, 0, scope
            )
            delay(40)
        }

        // Final: letzter Modus (REBOUND) muss vollständig angewendet sein, NICHT isScanning
        val deadline = System.currentTimeMillis() + 5000
        var final: ScanState.Done? = null
        while (System.currentTimeMillis() < deadline) {
            val s = vm.state.value
            if (s is ScanState.Done && !s.isScanning && s.result.settings.mode == ScanMode.REBOUND_START) {
                final = s; break
            }
            delay(25)
        }
        val f = final ?: error("Hängen geblieben in: ${vm.state.value}")
        assertEquals(ScanMode.REBOUND_START, f.result.settings.mode)
        assertEquals(1, f.scanned) // total = 1 Ticker, fertig
        cleanup(dir)
    }

    private fun cleanup(dir: Path) = fs.deleteRecursively(dir)
}

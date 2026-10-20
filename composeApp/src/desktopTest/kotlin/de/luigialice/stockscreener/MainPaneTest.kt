package de.luigialice.stockscreener

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import de.luigialice.stockscreener.data.BarCache
import de.luigialice.stockscreener.data.InMemoryBarStore
import de.luigialice.stockscreener.ui.ScanState
import de.luigialice.stockscreener.ui.ScanViewModel
import de.luigialice.stockscreener.ui.results.MainPane
import de.luigialice.stockscreener.ui.results.buildImportedResult
import de.luigialice.stockscreener.ui.theme.AppTheme
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class MainPaneTest {

    private fun tempCache(): BarCache = BarCache(InMemoryBarStore(), 60 * 60 * 1000)

    /** Wie in der App: MainPane sitzt immer in begrenztem Raum (BoxWithConstraints der Results-Spalte). */
    private fun paneModifier(): Modifier = Modifier.fillMaxWidth().height(700.dp)

    @Test
    fun idleShowsGuidance() = runSkikoComposeUiTest(size = Size(1600f, 900f)) {
        val vm = ScanViewModel(barCache = tempCache())
        setContent { AppTheme { MainPane(ScanState.Idle, vm, paneModifier()) } }
        onNodeWithText("Aktien-Universum wählen", substring = true).assertExists()
    }

    @Test
    fun runningShowsProgress() = runSkikoComposeUiTest(size = Size(1600f, 900f)) {
        val vm = ScanViewModel(barCache = tempCache())
        setContent { AppTheme { MainPane(ScanState.Running(3, 10, "AAPL"), vm, paneModifier()) } }
        onNodeWithText("Scanne … 3/10 (AAPL)").assertExists()
    }

    @Test
    fun failureShowsTheMessage() = runSkikoComposeUiTest(size = Size(1600f, 900f)) {
        val vm = ScanViewModel(barCache = tempCache())
        setContent { AppTheme { MainPane(ScanState.Failed("Boom"), vm, paneModifier()) } }
        onNodeWithText("Scan fehlgeschlagen: Boom").assertExists()
    }

    @Test
    fun doneWithoutHitsInvitesToRelaxTheFilter() = runSkikoComposeUiTest(size = Size(1600f, 900f)) {
        val vm = ScanViewModel(barCache = tempCache())
        val result = buildImportedResult(listOf(sampleRow("AAPL", score = 2)))
        setContent { AppTheme { MainPane(ScanState.Done(result), vm, paneModifier()) } }
        onNodeWithText("Treffer (0)").assertExists()
        onNodeWithText("Keine Treffer. Trefferfilter senken oder Regeln anpassen.").assertExists()
    }

    @Test
    fun doneWithHitsShowsTableMetricsAndLinks() = runSkikoComposeUiTest(size = Size(1600f, 900f)) {
        val cache = tempCache()
        cache.store("AAPL", sampleBars())
        cache.store("MSFT", sampleBars())
        val vm = ScanViewModel(barCache = cache)
        val result = buildImportedResult(listOf(sampleRow("AAPL", "Apple Inc", score = 6), sampleRow("MSFT", "Microsoft", score = 7)))
        setContent { AppTheme { MainPane(ScanState.Done(result), vm, paneModifier()) } }
        onNodeWithText("Geprüfte Aktien").assertExists()
        onNodeWithText("AAPL").assertExists()
        onNodeWithText("Microsoft").assertExists()
        onNodeWithText("Yahoo Summary").assertExists()
    }
}

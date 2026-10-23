package de.luigialice.stockscreener

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import de.luigialice.stockscreener.data.BarCache
import de.luigialice.stockscreener.data.InMemoryBarStore
import de.luigialice.stockscreener.model.ScanMode
import de.luigialice.stockscreener.model.ScanSettings
import de.luigialice.stockscreener.ui.ScanState
import de.luigialice.stockscreener.ui.ScanViewModel
import de.luigialice.stockscreener.ui.results.MainPane
import de.luigialice.stockscreener.ui.results.ResultsUiState
import de.luigialice.stockscreener.ui.results.buildImportedResult
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import de.luigialice.stockscreener.ui.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MainPaneTest {

    private fun tempCache(): BarCache = BarCache(InMemoryBarStore(), 60 * 60 * 1000)

    /** Sauberer Settings-Store, damit Tests nicht am lokalen Trefferfilter hängen. */
    private fun cleanUi(): ResultsUiState = ResultsUiState(InMemoryBarStore())

    /** Wie in der App: MainPane sitzt immer in begrenztem Raum (BoxWithConstraints der Results-Spalte). */
    private fun paneModifier(): Modifier = Modifier.fillMaxWidth().height(700.dp)

    @Test
    fun idleShowsGuidance() = runSkikoComposeUiTest(size = Size(1600f, 900f)) {
        val vm = ScanViewModel(barCache = tempCache())
        setContent { AppTheme { MainPane(ScanState.Idle, vm, cleanUi(), paneModifier()) } }
        onNodeWithText("Aktien-Universum wählen", substring = true).assertExists()
    }

    @Test
    fun runningShowsProgress() = runSkikoComposeUiTest(size = Size(1600f, 900f)) {
        val vm = ScanViewModel(barCache = tempCache())
        setContent { AppTheme { MainPane(ScanState.Running(3, 10, "AAPL"), vm, cleanUi(), paneModifier()) } }
        onNodeWithText("Scanne … 3/10 (AAPL)").assertExists()
    }

    @Test
    fun failureShowsTheMessage() = runSkikoComposeUiTest(size = Size(1600f, 900f)) {
        val vm = ScanViewModel(barCache = tempCache())
        setContent { AppTheme { MainPane(ScanState.Failed("Boom"), vm, cleanUi(), paneModifier()) } }
        onNodeWithText("Scan fehlgeschlagen: Boom").assertExists()
    }

    @Test
    fun doneWithoutHitsInvitesToRelaxTheFilter() = runSkikoComposeUiTest(size = Size(1600f, 900f)) {
        val vm = ScanViewModel(barCache = tempCache())
        // Mindest-Score des Scans (5) liegt über dem Row-Score (2) -> keine Treffer
        val result = buildImportedResult(listOf(sampleRow("AAPL", score = 2)))
            .copy(settings = ScanSettings(scoreMin = 5, onlyBelow12m = false, mode = ScanMode.REBOUND_START))
        setContent { AppTheme { MainPane(ScanState.Done(result), vm, cleanUi(), paneModifier()) } }
        onNodeWithText("Treffer (0)").assertExists()
        onNodeWithText("Keine Treffer. Trefferfilter senken, Regeln anpassen oder Muss-Kriterien lockern.").assertExists()
        // Auch ohne Treffer bleiben die Spaltenköpfe mit den Umschaltern da (Label „Ja")
        onAllNodesWithText("Ja").assertCountEquals(8)
    }

    @Test
    fun doneWithHitsShowsTableMetricsAndLinks() = runSkikoComposeUiTest(size = Size(1600f, 900f)) {
        val cache = tempCache()
        cache.store("AAPL", sampleBars())
        cache.store("MSFT", sampleBars())
        val vm = ScanViewModel(barCache = cache)
        val result = buildImportedResult(listOf(sampleRow("AAPL", "Apple Inc", score = 6), sampleRow("MSFT", "Microsoft", score = 7)))
        setContent { AppTheme { MainPane(ScanState.Done(result), vm, cleanUi(), paneModifier()) } }
        onNodeWithText("Geprüfte Aktien").assertExists()
        onNodeWithText("AAPL").assertExists()
        onNodeWithText("Microsoft").assertExists()
        onNodeWithText("Yahoo Summary").assertExists()
    }

    @Test
    fun perfColumnShowsTheWindowFromTheScan() = runSkikoComposeUiTest(size = Size(1600f, 900f)) {
        val vm = ScanViewModel(barCache = tempCache())
        val result = buildImportedResult(listOf(sampleRow("AAPL", "Apple Inc", score = 6)))
            .copy(settings = ScanSettings(scoreMin = 1, onlyBelow12m = false, mode = ScanMode.REBOUND_START, perfDays = 30))
        setContent { AppTheme { MainPane(ScanState.Done(result), vm, cleanUi(), paneModifier()) } }
        onNodeWithText("Perf 30 T. %").assertExists()
    }

    @Test
    fun perfColumnStaysGenericWithoutAWindow() = runSkikoComposeUiTest(size = Size(1600f, 900f)) {
        val vm = ScanViewModel(barCache = tempCache())
        // CSV-Import kennt das Fenster nicht
        val result = buildImportedResult(listOf(sampleRow("AAPL", "Apple Inc", score = 6)))
        setContent { AppTheme { MainPane(ScanState.Done(result), vm, cleanUi(), paneModifier()) } }
        onNodeWithText("Perf %").assertExists()
    }

    @Test
    fun trefferfilterLabelAndSliderShareOneLine() = runSkikoComposeUiTest(size = Size(1600f, 900f)) {
        val vm = ScanViewModel(barCache = tempCache())
        val result = buildImportedResult(listOf(sampleRow("AAPL", "Apple Inc", score = 6)))
            .copy(settings = ScanSettings(scoreMin = 1, onlyBelow12m = false, mode = ScanMode.REBOUND_START))
        setContent { AppTheme { MainPane(ScanState.Done(result), vm, cleanUi(), paneModifier()) } }
        // Label und Regler liegen in einer Zeile: ihre Höhenbänder überlappen
        val label = onNodeWithText("Trefferfilter", substring = true).getUnclippedBoundsInRoot()
        val slider = onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(1f, 1f..8f))).getUnclippedBoundsInRoot()
        assertTrue("Label $label und Regler $slider sollten eine Zeile teilen") {
            slider.top < label.bottom && label.top < slider.bottom
        }
        assertTrue("Regler gehört rechts vom Label ($label / $slider)") { slider.left >= label.left }
    }

    @Test
    fun mussMarkedCriteriaFilterTheHitList() = runSkikoComposeUiTest(size = Size(1600f, 900f)) {
        val vm = ScanViewModel(barCache = tempCache())
        val ui = cleanUi()
        ui.toggleMuss(ScanMode.REBOUND_START, "RSI im Band")
        ui.toggleMuss(ScanMode.REBOUND_START, "Volumen ok")
        fun criteria(rsiImBand: Boolean, volumenOk: Boolean) = mapOf(
            "Perf-Kriterium" to true, "RSI im Band" to rsiImBand, "Über SMA20" to true,
            "Unter SMA50" to true, "Volumen ok" to volumenOk, "RSI-Erholung" to true,
            "SMA20-Cross" to true, "Höheres Tief" to true,
        )
        // Ohne Muss-Markierung wären alle drei Treffer – OUT* fliegen wegen der Markierung raus
        val inside = sampleRow("HIT", "Im Band AG", score = 5, criteria = criteria(rsiImBand = true, volumenOk = true))
        val outsideRsi = sampleRow("OUTRSI", "Außerhalb AG", score = 7, criteria = criteria(rsiImBand = false, volumenOk = true))
        val outsideVol = sampleRow("OUTVOL", "Ohne Volumen AG", score = 6, criteria = criteria(rsiImBand = true, volumenOk = false))
        val result = buildImportedResult(listOf(inside, outsideRsi, outsideVol))
            .copy(settings = ScanSettings(scoreMin = 1, onlyBelow12m = false, mode = ScanMode.REBOUND_START))
        setContent { AppTheme { MainPane(ScanState.Done(result), vm, ui, paneModifier()) } }
        onNodeWithText("HIT").assertExists()
        onNodeWithText("OUTRSI").assertDoesNotExist()
        onNodeWithText("OUTVOL").assertDoesNotExist()
        onNodeWithText("Treffer (1)").assertExists()
    }

    @Test
    fun headerToggleMarksACriterionAsMuss() = runSkikoComposeUiTest(size = Size(1600f, 900f)) {
        val vm = ScanViewModel(barCache = tempCache())
        val ui = cleanUi()
        val result = buildImportedResult(listOf(sampleRow("HIT", "Treffer AG", score = 5)))
            .copy(settings = ScanSettings(scoreMin = 1, onlyBelow12m = false, mode = ScanMode.REBOUND_START))
        setContent { AppTheme { MainPane(ScanState.Done(result), vm, ui, paneModifier()) } }

        // Jedes Kriterium startet als Kann – die Checkbox trägt den Zustand, das Label ist „Ja"
        assertTrue(ui.mussByMode.isEmpty())

        onAllNodesWithText("Ja")[0].performClick()

        assertEquals(setOf("Perf-Kriterium"), ui.mussByMode[ScanMode.REBOUND_START])
    }
}

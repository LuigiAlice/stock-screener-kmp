package de.luigialice.stockscreener

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import de.luigialice.stockscreener.data.BarCache
import de.luigialice.stockscreener.data.InMemoryBarStore
import de.luigialice.stockscreener.ui.ScanState
import de.luigialice.stockscreener.ui.ScanViewModel
import de.luigialice.stockscreener.ui.settings.ReboundRulesState
import de.luigialice.stockscreener.ui.settings.SettingsPane
import de.luigialice.stockscreener.ui.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SettingsPaneTest {

    private fun tempCache(): BarCache = BarCache(InMemoryBarStore(), 60 * 60 * 1000)

    private fun paneModifier(): Modifier = Modifier.fillMaxWidth().height(1200.dp)

    @Test
    fun mindestScoreSliderIsGone() = runSkikoComposeUiTest(size = Size(900f, 1400f)) {
        val vm = ScanViewModel(barCache = tempCache())
        setContent {
            AppTheme {
                val scope = rememberCoroutineScope()
                SettingsPane(ScanState.Idle, vm, scope, paneModifier())
            }
        }
        onNodeWithText("Mindest-Score", substring = true).assertDoesNotExist()
    }

    @Test
    fun reboundRuleSlidersRemain() = runSkikoComposeUiTest(size = Size(900f, 1400f)) {
        val vm = ScanViewModel(barCache = tempCache())
        setContent {
            AppTheme {
                val scope = rememberCoroutineScope()
                SettingsPane(ScanState.Idle, vm, scope, paneModifier())
            }
        }
        onNodeWithText("Rückgang (Handelstage)", substring = true).assertExists()
        onNodeWithText("Max. Performance im Zeitraum", substring = true).assertExists()
        onNodeWithText("Min. relatives Volumen", substring = true).assertExists()
    }

    @Test
    fun scanAblaufIsHiddenByDefault() = runSkikoComposeUiTest(size = Size(900f, 1400f)) {
        val vm = ScanViewModel(barCache = tempCache())
        setContent {
            AppTheme {
                val scope = rememberCoroutineScope()
                SettingsPane(ScanState.Idle, vm, scope, paneModifier())
            }
        }
        onNodeWithText("Batch-Größe", substring = true).assertDoesNotExist()
        onNodeWithText("Pause zwischen Batches", substring = true).assertDoesNotExist()
        onNodeWithContentDescription("Einstellungen").assertExists()
    }

    @Test
    fun settingsDialogShowsScanAblauf() = runSkikoComposeUiTest(size = Size(900f, 1400f)) {
        val vm = ScanViewModel(barCache = tempCache())
        setContent {
            AppTheme {
                val scope = rememberCoroutineScope()
                SettingsPane(ScanState.Idle, vm, scope, paneModifier())
            }
        }
        onNodeWithContentDescription("Einstellungen").performClick()
        onNodeWithText("Scan-Ablauf", substring = true).assertExists()
        onNodeWithText("Batch-Größe", substring = true).assertExists()
        onNodeWithText("Pause zwischen Batches", substring = true).assertExists()
    }

    @Test
    fun resetRestoresTheRuleDefaults() = runSkikoComposeUiTest(size = Size(900f, 1400f)) {
        val vm = ScanViewModel(barCache = tempCache())
        val rules = ReboundRulesState()
        rules.days = 55f
        rules.maxPerf = -3f
        rules.minVolume = 3.5f
        rules.onlyBelow12m = false
        setContent {
            AppTheme {
                val scope = rememberCoroutineScope()
                SettingsPane(ScanState.Idle, vm, scope, rules, paneModifier())
            }
        }
        onNodeWithText("Rückgang (Handelstage): 55").assertExists()

        onNodeWithText("Regeln zurücksetzen").performClick()

        onNodeWithText("Rückgang (Handelstage): 30").assertExists()
        onNodeWithText("Max. Performance im Zeitraum (%): -8 %").assertExists()
        assertEquals(1.0f, rules.minVolume)
        assertEquals(true, rules.onlyBelow12m)
    }
}

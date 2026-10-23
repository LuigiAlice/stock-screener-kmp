package de.luigialice.stockscreener

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.geometry.Size
import de.luigialice.stockscreener.ui.results.ResultsTable
import de.luigialice.stockscreener.ui.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ResultsTableTest {

    private class RecordingUriHandler : UriHandler {
        val opened = mutableListOf<String>()
        override fun openUri(uri: String) {
            opened += uri
        }
    }

    private val rows = listOf(
        sampleRow("AAPL", "Apple Inc", score = 6),
        sampleRow("MSFT", "Microsoft", score = 7),
    )

    @Test
    fun rendersRowsCriteriaAndScore() = runSkikoComposeUiTest(size = Size(1600f, 900f)) {
        setContent {
            AppTheme {
                ResultsTable(rows, selectedIndex = 0, onSelect = {}, uriHandler = RecordingUriHandler(), tableModifier = Modifier, criteriaKeys = rows.first().criteria.keys.toList())
            }
        }
        onNodeWithText("AAPL").assertExists()
        onNodeWithText("Microsoft").assertExists()
        // Kriterien laufen als „Ja“/„Nein“ je Zeile — beide Zeilen enthalten beide Texte
        // Zusätzlich tragen die Umschalter der Kriteriums-Köpfe den Text „Ja"
        onAllNodesWithText("Nein").assertCountEquals(2)
        onAllNodesWithText("Ja").assertCountEquals(2 + rows.first().criteria.size)
        onNodeWithText("6/8").assertExists()
    }

    @Test
    fun clickOnRowSelectsIt() = runSkikoComposeUiTest(size = Size(1600f, 900f)) {
        var selected = -1
        setContent {
            AppTheme {
                ResultsTable(rows, selectedIndex = 0, onSelect = { selected = it }, uriHandler = RecordingUriHandler(), tableModifier = Modifier, criteriaKeys = rows.first().criteria.keys.toList())
            }
        }
        onNodeWithText("MSFT").performClick()
        waitForIdle()
        assertEquals(1, selected)
    }

    @Test
    fun summaryLinkOpensTheRowUrl() = runSkikoComposeUiTest(size = Size(2000f, 900f)) {
        // 2000f: die (breiteren) Kriteriums-Spalten brauchen Platz, Links ganz rechts
        val handler = RecordingUriHandler()
        setContent {
            AppTheme {
                ResultsTable(rows, selectedIndex = 0, onSelect = {}, uriHandler = handler, tableModifier = Modifier, criteriaKeys = rows.first().criteria.keys.toList())
            }
        }
        onAllNodesWithText("Summary")[0].performClick()
        waitForIdle()
        assertEquals(listOf("https://summary.test/AAPL"), handler.opened)
    }
}

package de.luigialice.stockscreener

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import de.luigialice.stockscreener.ui.chart.LineChart
import de.luigialice.stockscreener.ui.theme.AppTheme
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class LineChartTest {

    @Test
    fun chartIsLabeledWithItsTitle() = runComposeUiTest {
        setContent {
            AppTheme {
                LineChart(sampleBars(), Modifier.size(400.dp, 200.dp), title = "AAPL · Apple Inc · 01.01.2025 – 09.02.2025")
            }
        }
        onNodeWithContentDescription("AAPL · Apple Inc · 01.01.2025 – 09.02.2025").assertExists()
    }

    @Test
    fun emptyBarsFallBackToDefaultLabel() = runComposeUiTest {
        setContent {
            AppTheme {
                LineChart(emptyList(), Modifier.size(400.dp, 200.dp))
            }
        }
        onNodeWithContentDescription("Kurschart").assertExists()
    }
}

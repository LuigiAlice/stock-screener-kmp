package de.luigialice.stockscreener

import androidx.compose.ui.graphics.Color
import de.luigialice.stockscreener.ui.green
import de.luigialice.stockscreener.ui.red
import de.luigialice.stockscreener.ui.theme.AppColors
import de.luigialice.stockscreener.ui.theme.appColorScheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AppThemeTest {

    @Test
    fun paletteMatchesDesignContract() {
        assertEquals(Color(0xFF0B1720), AppColors.ink)
        assertEquals(Color(0xFF0A1822), AppColors.inkMid)
        assertEquals(Color(0xFF07121A), AppColors.inkDeep)
        assertEquals(Color(0xFF0F202B), AppColors.panel)
        assertEquals(Color(0xFF1C3340), AppColors.panelEdge)
        assertEquals(Color(0xFFD9A441), AppColors.brass)
        assertEquals(Color(0xFFE6EEF2), AppColors.text)
        assertEquals(Color(0xFF7E97A3), AppColors.textDim)
        assertEquals(Color(0xFFE0A458), AppColors.warn)
    }

    @Test
    fun schemeRolesRouteToThePalette() {
        assertEquals(AppColors.brass, appColorScheme.primary)
        assertEquals(AppColors.ink, appColorScheme.background)
        assertEquals(AppColors.panel, appColorScheme.surface)
        assertEquals(AppColors.text, appColorScheme.onSurface)
        assertEquals(AppColors.textDim, appColorScheme.onSurfaceVariant)
        assertEquals(AppColors.panelEdge, appColorScheme.surfaceContainerHighest)
        assertEquals(AppColors.panelEdge, appColorScheme.outline)
    }

    @Test
    fun brassAccentNeverCollidesWithMarketColors() {
        assertNotEquals(green, appColorScheme.primary)
        assertNotEquals(red, appColorScheme.primary)
    }
}

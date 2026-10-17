package de.luigialice.stockscreener.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Farbwelt „Tinte & Messing“: dunkle Petrol-Tinte als Bühne, Messing für alles
 * Bedienbare. Grün/Rot aus CommonUi bleiben ausschließlich Kursbewegungen und
 * erfüllten Kriterien vorbehalten.
 */
internal object AppColors {
    val ink = Color(0xFF0B1720)
    val inkDeep = Color(0xFF07121A)
    val panel = Color(0xFF0F202B)
    val panelEdge = Color(0xFF1C3340)
    val selection = Color(0xFF16303D)
    val brass = Color(0xFFD9A441)
    val text = Color(0xFFE6EEF2)
    val textDim = Color(0xFF7E97A3)
    val warn = Color(0xFFE0A458)
}

internal val appColorScheme = darkColorScheme(
    primary = AppColors.brass,
    onPrimary = Color(0xFF1C1404),
    background = AppColors.ink,
    onBackground = AppColors.text,
    surface = AppColors.panel,
    onSurface = AppColors.text,
    surfaceVariant = AppColors.panelEdge,
    onSurfaceVariant = AppColors.textDim,
    // Container-Rollen werden von darkColorScheme nicht aus surface abgeleitet
    surfaceContainerLowest = Color(0xFF0A1822),
    surfaceContainerLow = Color(0xFF0C1A24),
    surfaceContainer = AppColors.panel,
    surfaceContainerHigh = Color(0xFF142C38),
    surfaceContainerHighest = AppColors.panelEdge,
    secondaryContainer = AppColors.selection,
    onSecondaryContainer = AppColors.text,
    outline = AppColors.panelEdge,
    outlineVariant = AppColors.panelEdge.copy(alpha = 0.55f),
    error = Color(0xFFEF5350),
    onError = Color(0xFF1C0806),
)

private val appTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
)

@Composable
internal fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = appColorScheme, typography = appTypography) {
        // Ohne umgebendes Surface wäre die Standard-Textfarbe sonst Schwarz
        CompositionLocalProvider(LocalContentColor provides appColorScheme.onSurface) {
            content()
        }
    }
}

/**
 * Hintergrund der App: Tinte-Verlauf, ein warmes Messing-Glühen oben rechts und
 * ein feines Rasterpapier unter den Daten — Bühne für Kursanalyse, kein Motiv.
 */
@Composable
internal fun AppBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.drawWithCache {
            val step = 26.dp.toPx()
            val gridColor = AppColors.textDim.copy(alpha = 0.075f)
            val base = Brush.verticalGradient(
                0f to AppColors.ink,
                0.5f to Color(0xFF0A1822),
                1f to AppColors.inkDeep,
            )
            val glow = Brush.radialGradient(
                0f to AppColors.brass.copy(alpha = 0.13f),
                0.55f to AppColors.brass.copy(alpha = 0.035f),
                1f to Color.Transparent,
                center = Offset(size.width * 0.85f, size.height * 0.04f),
                radius = size.maxDimension * 0.55f,
            )
            val vignette = Brush.radialGradient(
                0.5f to Color.Transparent,
                1f to Color(0xFF040B10).copy(alpha = 0.5f),
                center = Offset(size.width * 0.5f, size.height * 0.42f),
                radius = size.maxDimension * 0.75f,
            )
            val grid = Path().apply {
                var x = 0f
                while (x < size.width) {
                    moveTo(x, 0f)
                    lineTo(x, size.height)
                    x += step
                }
                var y = 0f
                while (y < size.height) {
                    moveTo(0f, y)
                    lineTo(size.width, y)
                    y += step
                }
            }
            onDrawBehind {
                drawRect(base)
                drawRect(glow)
                drawPath(grid, gridColor, style = Stroke(width = 1f))
                drawRect(vignette)
            }
        }
    ) { content() }
}

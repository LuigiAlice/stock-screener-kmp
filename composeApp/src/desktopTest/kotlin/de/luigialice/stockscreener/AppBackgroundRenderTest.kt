package de.luigialice.stockscreener

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import de.luigialice.stockscreener.ui.ScreenerApp
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import org.jetbrains.skia.EncodedImageFormat
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Bildet den visuellen Vertrag des Hintergrunds ab: Panel, Tinte-Canvas und
 * Messing-Akzent müssen im gerenderten Bild sichtbar sein.
 */
class AppBackgroundRenderTest {

    private fun render(): BufferedImage {
        val scene = ImageComposeScene(width = 1440, height = 900, density = Density(1f)) { ScreenerApp() }
        val image = scene.render(0L)
        scene.close()
        val data = image.encodeToData(EncodedImageFormat.PNG) ?: error("PNG-Encoding fehlgeschlagen")
        return ImageIO.read(ByteArrayInputStream(data.bytes))
    }

    private fun containsColor(img: BufferedImage, xRange: IntRange, yRange: IntRange, match: (Int, Int, Int) -> Boolean): Boolean {
        for (x in xRange) for (y in yRange) {
            val rgb = img.getRGB(x, y)
            if (match((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)) return true
        }
        return false
    }

    private fun near(a: Int, b: Int) = abs(a - b) <= 6

    @Test
    fun backgroundPaintsPanelInkCanvasAndBrass() {
        val img = render()
        assertTrue(
            containsColor(img, 10..350, 100..800) { r, g, b -> near(r, 15) && near(g, 32) && near(b, 43) },
            "Panel #0F202B fehlt"
        )
        assertTrue(
            containsColor(img, 400..1400, 100..850) { r, g, b -> b > r && r < 40 && b < 60 },
            "Tinte-Canvas (Petrol) fehlt"
        )
        assertTrue(
            containsColor(img, 10..350, 100..850) { r, g, b -> near(r, 217) && near(g, 164) && near(b, 65) },
            "Messing-Akzent fehlt"
        )
    }
}

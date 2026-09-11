package de.luigialice.stockscreener

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import de.luigialice.stockscreener.data.BarCachePaths
import de.luigialice.stockscreener.ui.ScreenerApp
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BarCachePaths.baseDir = File(cacheDir, "bars").apply { mkdirs() }
        setContent { ScreenerApp() }
    }
}

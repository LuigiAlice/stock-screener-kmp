package de.luigialice.stockscreener.data

import java.io.File

object BarCachePaths {
    @Volatile
    var baseDir: File? = null
}

actual fun barCacheDirectory(): File? = BarCachePaths.baseDir

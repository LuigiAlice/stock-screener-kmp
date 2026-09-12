package de.luigialice.stockscreener.data

import okio.Path
import okio.Path.Companion.toPath

object BarCachePaths {
    @Volatile
    var baseDir: String? = null
}

actual fun currentTimeMs(): Long = System.currentTimeMillis()

actual fun barCacheDirectory(): Path? = BarCachePaths.baseDir?.toPath()

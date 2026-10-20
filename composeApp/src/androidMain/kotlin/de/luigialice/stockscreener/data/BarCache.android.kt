package de.luigialice.stockscreener.data

import okio.Path.Companion.toPath

object BarCachePaths {
    @Volatile
    var baseDir: String? = null
}

actual fun currentTimeMs(): Long = System.currentTimeMillis()

actual fun createBarStore(): BarStore? =
    BarCachePaths.baseDir?.let { FileBarStore(it.toPath()) }

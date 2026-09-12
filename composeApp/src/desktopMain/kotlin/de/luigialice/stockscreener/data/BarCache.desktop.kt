package de.luigialice.stockscreener.data

import okio.Path
import okio.Path.Companion.toPath

actual fun currentTimeMs(): Long = System.currentTimeMillis()

actual fun barCacheDirectory(): Path? =
    (System.getProperty("user.home") + "/.stockscreener/bars").toPath()

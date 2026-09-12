package de.luigialice.stockscreener.data

import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSDate
import platform.Foundation.NSHomeDirectory
import platform.Foundation.timeIntervalSince1970

actual fun currentTimeMs(): Long =
    (NSDate().timeIntervalSince1970 * 1000.0).toLong()

actual fun barCacheDirectory(): Path? =
    (NSHomeDirectory() + "/Documents/bars").toPath()

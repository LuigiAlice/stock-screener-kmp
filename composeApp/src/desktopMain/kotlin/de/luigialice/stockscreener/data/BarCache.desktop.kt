package de.luigialice.stockscreener.data

import java.io.File

actual fun barCacheDirectory(): File? =
    File(System.getProperty("user.home"), ".stockscreener/bars").apply { mkdirs() }

package de.luigialice.stockscreener.data

import okio.Path.Companion.toPath

actual fun currentTimeMs(): Long = System.currentTimeMillis()

actual fun createBarStore(): BarStore? =
    FileBarStore((System.getProperty("user.home") + "/.stockscreener/bars").toPath())

actual fun createSettingsStore(): BarStore? =
    FileBarStore((System.getProperty("user.home") + "/.stockscreener/settings").toPath())

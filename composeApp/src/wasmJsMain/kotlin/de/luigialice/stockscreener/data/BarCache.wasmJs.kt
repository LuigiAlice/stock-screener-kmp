package de.luigialice.stockscreener.data

import kotlinx.browser.localStorage
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Persistenz im Browser: localStorage (Quota-guarded, ~5 MB; bei Überlauf nur In-Memory). */
private class LocalStorageBarStore : BarStore {

    override fun load(key: String): String? =
        runCatching { localStorage.getItem("bars:$key") }.getOrNull()

    override fun save(key: String, value: String) {
        runCatching { localStorage.setItem("bars:$key", value) }
    }
}

@OptIn(ExperimentalTime::class)
actual fun currentTimeMs(): Long = Clock.System.now().toEpochMilliseconds()

actual fun createBarStore(): BarStore? = LocalStorageBarStore()

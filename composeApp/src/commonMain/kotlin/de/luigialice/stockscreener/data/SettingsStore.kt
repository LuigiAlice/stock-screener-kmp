package de.luigialice.stockscreener.data

/**
 * Dauerhafte UI-Einstellungen (z. B. Trefferfilter-Position je Scan-Modus).
 * Gleicher schlüsselbasierter String-Store wie der BarCache, anderer Ablageort;
 * null = nur für die Sitzung.
 */
expect fun createSettingsStore(): BarStore?

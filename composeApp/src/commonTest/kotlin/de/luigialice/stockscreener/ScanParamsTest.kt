package de.luigialice.stockscreener

import de.luigialice.stockscreener.model.ONGOING_RSI_BAND
import de.luigialice.stockscreener.model.ScanMode
import de.luigialice.stockscreener.model.scanParams
import kotlin.test.Test
import kotlin.test.assertEquals

class ScanParamsTest {

    @Test
    fun reboundUsesConfiguredBand() {
        val p = scanParams(ScanMode.REBOUND_START, days = 30, maxPerf = -20.0, rsiMin = 40.0, rsiMax = 55.0, minVolume = 2.0)
        assertEquals(30, p.days)
        assertEquals(-20.0, p.maxPerf, 1e-9)
        assertEquals(40.0, p.rsiMin, 1e-9)
        assertEquals(55.0, p.rsiMax, 1e-9)
        assertEquals(2.0, p.minVolume, 1e-9)
    }

    @Test
    fun ongoingUsesFixedRsiBandRegardlessOfInput() {
        val p = scanParams(ScanMode.ONGOING_RECOVERY, days = 30, maxPerf = -20.0, rsiMin = 40.0, rsiMax = 55.0, minVolume = 2.0)
        assertEquals(ONGOING_RSI_BAND.start, p.rsiMin, 1e-9)
        assertEquals(ONGOING_RSI_BAND.endInclusive, p.rsiMax, 1e-9)
    }
}

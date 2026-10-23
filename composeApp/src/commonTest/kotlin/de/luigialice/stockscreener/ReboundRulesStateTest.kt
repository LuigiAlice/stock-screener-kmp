package de.luigialice.stockscreener

import de.luigialice.stockscreener.model.ScanMode
import de.luigialice.stockscreener.model.ScanParams
import de.luigialice.stockscreener.ui.settings.ReboundRulesState
import kotlin.test.Test
import kotlin.test.assertEquals

class ReboundRulesStateTest {

    @Test
    fun startsAtTheCalibratedDefaults() {
        val rules = ReboundRulesState()
        val d = ScanParams(ScanMode.REBOUND_START)
        assertEquals(d.days.toFloat(), rules.days)
        assertEquals(d.maxPerf.toFloat(), rules.maxPerf)
        assertEquals(d.rsiMin.toFloat(), rules.rsiRange.start)
        assertEquals(d.rsiMax.toFloat(), rules.rsiRange.endInclusive)
        assertEquals(d.minVolume.toFloat(), rules.minVolume)
        assertEquals(true, rules.onlyBelow12m)
    }

    @Test
    fun resetRestoresEveryValue() {
        val rules = ReboundRulesState()
        rules.days = 55f
        rules.maxPerf = -3f
        rules.rsiRange = 12f..25f
        rules.minVolume = 3.5f
        rules.onlyBelow12m = false

        rules.reset()

        val d = ScanParams(ScanMode.REBOUND_START)
        assertEquals(d.days.toFloat(), rules.days)
        assertEquals(d.maxPerf.toFloat(), rules.maxPerf)
        assertEquals(d.rsiMin.toFloat(), rules.rsiRange.start)
        assertEquals(d.rsiMax.toFloat(), rules.rsiRange.endInclusive)
        assertEquals(d.minVolume.toFloat(), rules.minVolume)
        assertEquals(true, rules.onlyBelow12m)
    }

    @Test
    fun resetIsIdempotent() {
        val rules = ReboundRulesState()
        rules.reset()
        rules.reset()
        assertEquals(ReboundRulesState().days, rules.days)
        assertEquals(true, rules.onlyBelow12m)
    }
}

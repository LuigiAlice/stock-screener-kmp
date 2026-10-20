package de.luigialice.stockscreener

import de.luigialice.stockscreener.ui.fmt
import de.luigialice.stockscreener.ui.fmtDate
import de.luigialice.stockscreener.ui.fmtDateShort
import de.luigialice.stockscreener.ui.fmtDouble
import de.luigialice.stockscreener.ui.fmtIsoDate
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class CommonUiFormatTest {

    @Test
    fun fmtGroupsThousandsGermanStyle() {
        assertEquals("1.234,50", fmt(1234.5))
        assertEquals("1.000,00", fmt(1000.0))
        assertEquals("1.234.567", fmtDouble(1234567.0, 0, ',', '.'))
        assertEquals("0,00", fmt(0.0))
    }

    @Test
    fun fmtKeepsSignOfNegatives() {
        assertEquals("-1.234,56", fmt(-1234.56))
        assertEquals("-0,50", fmt(-0.5))
    }

    @Test
    fun fmtHonorsDigitCount() {
        assertEquals("12,5", fmt(12.5, 1))
        assertEquals("2", fmtDouble(2.0, 0, ',', '.'))
        assertEquals("2.000", fmtDouble(2000.0, 0, ',', '.'))
    }

    @Test
    fun fmtDoubleWithoutGroupSeparator() {
        assertEquals("1234.50", fmtDouble(1234.5, 2, '.', null))
    }

    @Test
    fun datesAreGermanFormatted() {
        val d = LocalDate(2026, 9, 5)
        assertEquals("05.09.2026", fmtDate(d))
        assertEquals("05.09.", fmtDateShort(d))
    }

    @Test
    fun isoDateBecomesGermanOrStaysAsIs() {
        assertEquals("09.09.2026", fmtIsoDate("2026-09-09"))
        assertEquals("", fmtIsoDate(null))
        assertEquals("kein Datum", fmtIsoDate("kein Datum"))
    }
}

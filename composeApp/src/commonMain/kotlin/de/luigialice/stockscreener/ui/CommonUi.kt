package de.luigialice.stockscreener.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlinx.datetime.LocalDate

internal val green = Color(0xFF22C55E)
internal val red = Color(0xFFEF4444)

/**
 * Reine Kotlin-Zahlenformatierung (multiplattform): Rundung auf `digits`,
 * optionales Gruppieren, frei wählbare Trennzeichen.
 * de-DE: fmtDouble(1234.5, 2, ',', '.') -> "1.234,50"
 * ROOT:  fmtDouble(1234.5, 2, '.', null) -> "1234.50"
 */
internal fun fmtDouble(v: Double, digits: Int, decimalSep: Char, groupSep: Char?): String {
    val factor = 10.0.pow(digits).toLong()
    val scaled = (v * factor).roundToLong()
    val negative = scaled < 0
    val abs = if (negative) -scaled else scaled
    val intPart = abs / factor
    val frac = abs % factor
    var intStr = intPart.toString()
    if (groupSep != null) {
        intStr = intStr.reversed().chunked(3).joinToString(groupSep.toString()) { it.reversed() }
    }
    val fracStr = if (digits > 0) decimalSep + frac.toString().padStart(digits, '0') else ""
    val sign = if (negative && (intPart != 0L || frac != 0L)) "-" else if (negative && intPart == 0L && frac == 0L) "" else ""
    return sign + intStr + fracStr
}

/** Zahlen deutsch: Tausender-Punkt, Dezimal-Komma (z. B. 1.234,56). */
internal fun fmt(v: Double, digits: Int = 2): String = fmtDouble(v, digits, ',', '.')

/** TT.MM.JJJJ */
internal fun fmtDate(date: LocalDate): String =
    date.dayOfMonth.toString().padStart(2, '0') + "." +
        date.monthNumber.toString().padStart(2, '0') + "." +
        date.year.toString().padStart(4, '0')

/** TT.MM. */
internal fun fmtDateShort(date: LocalDate): String =
    date.dayOfMonth.toString().padStart(2, '0') + "." +
        date.monthNumber.toString().padStart(2, '0') + "."

/** "2026-09-09" -> "09.09.2026" */
internal fun fmtIsoDate(iso: String?): String {
    val m = Regex("(\\d{4})-(\\d{2})-(\\d{2})").find(iso.orEmpty()) ?: return iso.orEmpty()
    return "${m.groupValues[3]}.${m.groupValues[2]}.${m.groupValues[1]}"
}

@Composable
internal fun SliderRow(label: String, valueText: String, value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    Text("$label: $valueText", fontSize = 13.sp)
    Slider(value, valueRange = min..max, onValueChange = onChange)
}

@Composable
internal fun Metric(label: String, value: String) {
    Column {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
internal fun LinkText(label: String, url: String, uriHandler: UriHandler) {
    Text(
        label,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable { uriHandler.openUri(url) }
    )
}

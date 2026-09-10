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
import kotlinx.datetime.LocalDate
import java.util.Locale

internal val germanLocale = Locale.GERMAN

internal val green = Color(0xFF22C55E)
internal val red = Color(0xFFEF4444)

/** Zahlen deutsch: Tausender-Punkt, Dezimal-Komma (z. B. 1.234,56). */
internal fun fmt(v: Double, digits: Int = 2): String = String.format(germanLocale, "%,.${digits}f", v)

/** TT.MM.JJJJ */
internal fun fmtDate(date: LocalDate): String =
    "%02d.%02d.%04d".format(date.dayOfMonth, date.monthNumber, date.year)

/** TT.MM. */
internal fun fmtDateShort(date: LocalDate): String =
    "%02d.%02d.".format(date.dayOfMonth, date.monthNumber)

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

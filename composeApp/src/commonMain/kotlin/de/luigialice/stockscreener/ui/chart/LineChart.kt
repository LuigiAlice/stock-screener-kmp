package de.luigialice.stockscreener.ui.chart

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.luigialice.stockscreener.model.Bar
import de.luigialice.stockscreener.ui.fmt
import de.luigialice.stockscreener.ui.fmtDate
import de.luigialice.stockscreener.ui.fmtDouble
import de.luigialice.stockscreener.ui.green

@Composable
fun LineChart(bars: List<Bar>, modifier: Modifier = Modifier, title: String? = null) {
    val textMeasurer = rememberTextMeasurer()
    val axisStyle = TextStyle(fontSize = 10.sp, color = Color.Gray)
    val titleStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    val lastPriceStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = green)
    val tooltipStyle = TextStyle(fontSize = 11.sp, color = Color.White)
    var hoverX by remember(bars) { mutableStateOf<Float?>(null) }
    Spacer(
        modifier
            .semantics { contentDescription = title ?: "Kurschart" }
            .pointerInput(bars) {
                awaitPointerEventScope {
                    var activeId: PointerId? = null
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Exit -> {
                                activeId = null
                                hoverX = null
                            }
                            PointerEventType.Release -> {
                                if (event.changes.any { it.id == activeId }) {
                                    activeId = null
                                    hoverX = null
                                }
                            }
                            else -> {
                                val target = activeId?.let { id -> event.changes.firstOrNull { it.id == id } }
                                    ?: event.changes.firstOrNull()?.takeIf { activeId == null }
                                if (target != null) {
                                    hoverX = target.position.x
                                    if (activeId == null && target.pressed) activeId = target.id
                                }
                            }
                        }
                    }
                }
            }
            .drawWithCache {
                // Statische Geometrie, Texte und Kurspfad werden nur pro Cache-Phase berechnet
                val scale = computeChartScale(bars)
                val geom = scale?.let {
                    val titleMeas = title?.let { t -> textMeasurer.measure(t, titleStyle) }
                    val plotTop = titleMeas?.let { m -> m.size.height + 4.dp.toPx() } ?: 0f
                    val plotBottom = size.height - 22.dp.toPx()
                    val padLeft = textMeasurer.measure("8888.88", axisStyle).size.width + 10.dp.toPx()
                    val plotH = plotBottom - plotTop
                    val plotW = size.width - padLeft

                    if (plotW > 0f && plotH > 0f) {
                        val yLabels = yTickValues(it).map { v ->
                            val label = if (it.range >= 40) fmtDouble(v, 0, ',', '.') else fmtDouble(v, 2, ',', '.')
                            textMeasurer.measure(label, axisStyle) to chartY(v, it, plotTop, plotH)
                        }
                        // Monatsraster: Gitterlinie an jedem Monatsanfang, Jahreszahlen an Jahreswechsel
                        val ticks = monthTicks(bars)
                        val monthNames = listOf("Jan", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez")
                        val showMonthLabels = ticks.size >= 2 && plotW / ticks.size >= 32f
                        val monthGrid = ticks.map { t -> chartX(t.index, bars.size, padLeft, plotW) }
                        val yearLabels = ticks.filter { it.isYearStart }.map { t ->
                            textMeasurer.measure(t.year.toString(), axisStyle) to chartX(t.index, bars.size, padLeft, plotW)
                        }
                        val monthLabels = if (showMonthLabels) ticks.filter { !it.isYearStart }.map { t ->
                            textMeasurer.measure(monthNames[t.month - 1], axisStyle) to chartX(t.index, bars.size, padLeft, plotW)
                        } else emptyList()
                        val path = Path()
                        bars.forEachIndexed { idx, bar ->
                            val p = Offset(chartX(idx, bars.size, padLeft, plotW), chartY(bar.close, it, plotTop, plotH))
                            if (idx == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                        }
                        val lastPoint = Offset(chartX(bars.size - 1, bars.size, padLeft, plotW), chartY(bars.last().close, it, plotTop, plotH))
                        val lastMeas = textMeasurer.measure(fmt(bars.last().close), lastPriceStyle)

                        ChartGeometry(padLeft, plotTop, plotBottom, plotW, plotH, it, yLabels, monthGrid, yearLabels, monthLabels, path, lastPoint, lastMeas, titleMeas)
                    } else null
                }

                onDrawBehind {
                    val g = geom ?: return@onDrawBehind

                    // Y-Achse: Gitterlinien + Preis-Beschriftung
                    g.yLabels.forEach { (m, yy) ->
                        drawLine(Color.Gray.copy(alpha = 0.25f), Offset(g.padLeft, yy), Offset(size.width, yy), strokeWidth = 1f)
                        drawText(m, topLeft = Offset(0f, (yy - m.size.height / 2f).coerceIn(g.plotTop, g.plotBottom - m.size.height)))
                    }
                    drawLine(Color.Gray.copy(alpha = 0.6f), Offset(g.padLeft, g.plotTop), Offset(g.padLeft, g.plotBottom), strokeWidth = 1f)

                    // Monatsraster + Jahres-/Monats-Beschriftung
                    g.monthGrid.forEach { xx ->
                        if (xx > g.padLeft + 1f) {
                            drawLine(Color.Gray.copy(alpha = 0.15f), Offset(xx, g.plotTop), Offset(xx, g.plotBottom), strokeWidth = 1f)
                        }
                    }
                    g.yearLabels.forEach { (m, xx) ->
                        drawText(m, topLeft = Offset((xx - m.size.width / 2f).coerceIn(0f, size.width - m.size.width), g.plotBottom + 6.dp.toPx()))
                    }
                    g.monthLabels.forEach { (m, xx) ->
                        drawText(m, topLeft = Offset((xx - m.size.width / 2f).coerceIn(0f, size.width - m.size.width), g.plotBottom + 6.dp.toPx()))
                    }
                    drawLine(Color.Gray.copy(alpha = 0.6f), Offset(g.padLeft, g.plotBottom), Offset(size.width, g.plotBottom), strokeWidth = 1f)

                    // Kurslinie
                    drawPath(g.path, green, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

                    // Letzter Kurs: Punkt + Beschriftung
                    drawCircle(green, radius = 4.dp.toPx(), center = g.lastPoint)
                    drawText(
                        g.lastMeas,
                        topLeft = Offset(
                            (g.lastPoint.x - g.lastMeas.size.width - 8.dp.toPx()).coerceAtLeast(g.padLeft),
                            (g.lastPoint.y - g.lastMeas.size.height - 8.dp.toPx()).coerceAtLeast(g.plotTop)
                        )
                    )

                    // Titel (Ticker + Zeitraum)
                    g.titleMeas?.let { drawText(it, topLeft = Offset(g.padLeft, 0f)) }

                    // Hover: Fadenkreuz + Tooltip (löst nur Draw-Invalidierung aus)
                    hoverX?.let { hx ->
                        if (hx < g.padLeft) return@let
                        val idx = hoverIndex(hx, g.padLeft, g.plotW, bars.size)
                        val bar = bars[idx]
                        val px = chartX(idx, bars.size, g.padLeft, g.plotW)
                        val py = chartY(bar.close, g.scale, g.plotTop, g.plotH)

                        drawLine(
                            Color.White.copy(alpha = 0.5f),
                            Offset(px, g.plotTop),
                            Offset(px, g.plotBottom),
                            strokeWidth = 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                        )
                        drawLine(
                            Color.White.copy(alpha = 0.3f),
                            Offset(g.padLeft, py),
                            Offset(size.width, py),
                            strokeWidth = 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                        )
                        drawCircle(Color.White, radius = 4.dp.toPx(), center = Offset(px, py))
                        drawCircle(green, radius = 2.5.dp.toPx(), center = Offset(px, py))

                        val lines = "${fmtDate(bar.date)}\nKurs: ${fmt(bar.close)}\nVolumen: ${volumeLabel(bar.volume)}"
                        val tm = textMeasurer.measure(lines, tooltipStyle)
                        val boxW = tm.size.width + 16.dp.toPx()
                        val boxH = tm.size.height + 12.dp.toPx()
                        val boxX = if (px + 12.dp.toPx() + boxW > size.width) px - 12.dp.toPx() - boxW else px + 12.dp.toPx()
                        val boxY = (py - boxH / 2f).coerceIn(g.plotTop, g.plotBottom - boxH)
                        drawRoundRect(
                            Color(0xEE1E1E1E),
                            topLeft = Offset(boxX, boxY),
                            size = Size(boxW, boxH),
                            cornerRadius = CornerRadius(6.dp.toPx())
                        )
                        drawRoundRect(
                            Color.Gray.copy(alpha = 0.6f),
                            topLeft = Offset(boxX, boxY),
                            size = Size(boxW, boxH),
                            cornerRadius = CornerRadius(6.dp.toPx()),
                            style = Stroke(width = 1.dp.toPx())
                        )
                        drawText(tm, topLeft = Offset(boxX + 8.dp.toPx(), boxY + 6.dp.toPx()))
                    }
                }
            }
    )
}

private class ChartGeometry(
    val padLeft: Float,
    val plotTop: Float,
    val plotBottom: Float,
    val plotW: Float,
    val plotH: Float,
    val scale: ChartScale,
    val yLabels: List<Pair<TextLayoutResult, Float>>,
    val monthGrid: List<Float>,
    val yearLabels: List<Pair<TextLayoutResult, Float>>,
    val monthLabels: List<Pair<TextLayoutResult, Float>>,
    val path: Path,
    val lastPoint: Offset,
    val lastMeas: TextLayoutResult,
    val titleMeas: TextLayoutResult?,
)

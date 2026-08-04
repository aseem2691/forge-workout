package com.forge.workout.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.forge.workout.data.Period
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/** Hairline grid, one shade off the surface — never dashed, never louder than the data. */
private val Grid = Color(0xFF1E1F24)
private const val AXIS_BAND_DP = 18f

private fun DrawScope.gridLines(plotHeight: Float, width: Float, rows: Int = 3) {
    repeat(rows + 1) { row ->
        val y = plotHeight * row / rows
        drawLine(Grid, Offset(0f, y), Offset(width, y), strokeWidth = 1f)
    }
}

private fun DrawScope.label(
    measurer: TextMeasurer,
    text: String,
    x: Float,
    y: Float,
    color: Color,
    sizeSp: Double = 9.0,
    weight: Int = 600,
    centre: Boolean = false,
    rightAlign: Boolean = false,
) {
    val style: TextStyle = arch(sizeSp, weight, color, line = 1.0)
    val layout = measurer.measure(AnnotatedString(text), style = style)
    val dx = when {
        centre -> x - layout.size.width / 2f
        rightAlign -> x - layout.size.width
        else -> x
    }
    drawText(layout, topLeft = Offset(dx.coerceAtLeast(0f), y))
}

/** A time-stamped measurement: what every trend chart here plots. */
data class TrendPoint(val atMs: Long, val value: Float)

/**
 * A single measurement over time — one series, so no legend: the card title names it.
 * Any [target] is drawn as a dashed threshold (a reference line, not a second series).
 */
@Composable
fun TrendChart(
    points: List<TrendPoint>,
    target: Float?,
    targetLabel: String?,
    emptyMessage: String,
    singleMessage: String,
    modifier: Modifier = Modifier,
    format: (Float) -> String = { format1(it) },
) {
    if (points.size < 2) {
        EmptyPlot(if (points.isEmpty()) emptyMessage else singleMessage, modifier)
        return
    }
    val measurer = rememberTextMeasurer()

    Canvas(modifier.fillMaxWidth().height(150.dp)) {
        val axisBand = AXIS_BAND_DP.dp.toPx()
        val plotHeight = size.height - axisBand
        val values = points.map { it.value }
        var low = values.min()
        var high = values.max()
        target?.let { low = minOf(low, it); high = maxOf(high, it) }
        // Pad so the line never rides the frame, and never divide by zero on a flat series.
        val span = max(high - low, 0.6f)
        val pad = span * 0.18f
        low -= pad
        high += pad
        val range = high - low

        gridLines(plotHeight, size.width)

        // Position by time, not by index — a fortnight's gap between weigh-ins has to
        // read as a gap rather than as one evenly spaced step.
        val firstMs = points.first().atMs
        val lastMs = points.last().atMs
        val spanMs = (lastMs - firstMs).toFloat()
        fun xAt(index: Int): Float = if (spanMs <= 0f) {
            size.width * index / (points.size - 1).toFloat()
        } else {
            size.width * ((points[index].atMs - firstMs) / spanMs)
        }
        fun yAt(value: Float) = plotHeight - ((value - low) / range) * plotHeight

        target?.let { threshold ->
            val y = yAt(threshold)
            drawLine(
                color = C.Blue.copy(alpha = 0.55f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
            )
            label(
                measurer,
                targetLabel ?: "TARGET ${format(threshold)}",
                size.width, y - 14.dp.toPx(), C.Blue, 8.5, 700, rightAlign = true,
            )
        }

        val path = Path().apply {
            points.forEachIndexed { index, point ->
                val x = xAt(index)
                val y = yAt(point.value)
                if (index == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        drawPath(path, C.Accent, style = Stroke(width = 2.dp.toPx()))

        // Markers only when they can breathe; otherwise the line carries it.
        if (points.size <= 14) {
            points.forEachIndexed { index, point ->
                drawCircle(C.Accent, radius = 4.dp.toPx(), center = Offset(xAt(index), yAt(point.value)))
            }
        }
        // Direct-label the endpoints only — never a number on every point.
        val first = points.first()
        val last = points.last()
        label(measurer, format(first.value), 0f, yAt(first.value) - 16.dp.toPx(), C.Muted, 9.0)
        label(measurer, format(last.value), size.width, yAt(last.value) - 16.dp.toPx(), C.Text, 10.5, 700, rightAlign = true)

        drawLine(Grid, Offset(0f, plotHeight), Offset(size.width, plotHeight), strokeWidth = 1f)
    }
}

/** Training volume per bucket — one series, one colour, bars anchored to the baseline. */
@Composable
fun VolumeChart(
    periods: List<Period>,
    modifier: Modifier = Modifier,
) {
    val peak = periods.maxOfOrNull { it.volumeKg } ?: 0
    if (periods.isEmpty() || peak <= 0) {
        EmptyPlot("No sessions logged in this range", modifier)
        return
    }
    val measurer = rememberTextMeasurer()

    Canvas(modifier.fillMaxWidth().height(150.dp)) {
        val axisBand = AXIS_BAND_DP.dp.toPx()
        val plotHeight = size.height - axisBand
        gridLines(plotHeight, size.width)

        val gap = 2.dp.toPx()
        val slot = size.width / periods.size
        val barWidth = (slot - gap).coerceAtLeast(1.5f)
        val corner = 4.dp.toPx()
        val peakIndex = periods.indexOfFirst { it.volumeKg == peak }

        periods.forEachIndexed { index, period ->
            if (period.volumeKg <= 0) return@forEachIndexed
            val height = (period.volumeKg / peak.toFloat()) * plotHeight * 0.88f
            val left = slot * index + gap / 2f
            val top = plotHeight - height
            drawRoundRect(
                // One series, one colour — shading by value would double-encode bar height
                // and makes tied values look different.
                color = C.Accent,
                topLeft = Offset(left, top),
                size = Size(barWidth, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner),
            )
        }

        // Label the peak only; the axis and the table carry the rest.
        periods.getOrNull(peakIndex)?.let { period ->
            val height = (period.volumeKg / peak.toFloat()) * plotHeight * 0.88f
            val centre = slot * peakIndex + slot / 2f
            label(
                measurer,
                tonnes(period.volumeKg),
                centre,
                (plotHeight - height - 15.dp.toPx()).coerceAtLeast(0f),
                C.Text,
                9.5,
                700,
                centre = true,
            )
        }

        drawLine(Grid, Offset(0f, plotHeight), Offset(size.width, plotHeight), strokeWidth = 1f)

        // Evenly spaced ticks including both ends, so the last label can never land
        // on top of the one before it.
        val ticks = 5
        val labelled = if (periods.size <= ticks) {
            periods.indices.toList()
        } else {
            (0 until ticks).map { it * (periods.size - 1) / (ticks - 1) }
        }
        labelled.forEach { index ->
            label(
                measurer,
                periods[index].label,
                slot * index + slot / 2f,
                plotHeight + 5.dp.toPx(),
                C.Faint,
                8.5,
                600,
                centre = true,
            )
        }
    }
}

@Composable
private fun EmptyPlot(message: String, modifier: Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(150.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Text(message, style = arch(11.0, 500, C.Faint, line = 1.4))
    }
}

fun tonnes(kg: Int): String =
    if (kg >= 1000) String.format(Locale.US, "%.1ft", kg / 1000f) else "$kg kg"

fun signed(value: Float): String =
    (if (value > 0) "+" else if (value < 0) "−" else "") + format1(abs(value))

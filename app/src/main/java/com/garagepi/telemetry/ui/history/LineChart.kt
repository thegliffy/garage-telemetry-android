package com.garagepi.telemetry.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

/** Cap on plotted points. Above this the series is bucketed — see [downsample]. */
private const val MAX_PLOT_POINTS = 600

/**
 * Time-series chart with a readable scale.
 *
 * The previous version drew a bare path with no axes, so there was no way to tell what
 * any value was. This labels the y range and elapsed time, marks zero for values that
 * swing both ways (pack power), and downsamples long drives so a 30k-point series still
 * renders smoothly.
 */
@Composable
fun LineChart(
    points: List<Pair<Long, Double>>,
    unit: String,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
) {
    val textMeasurer = rememberTextMeasurer()
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = TextStyle(fontSize = 10.sp, color = axisColor)

    val plotted = remember(points) { downsample(points, MAX_PLOT_POINTS) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(170.dp),
    ) {
        if (plotted.size < 2) return@Canvas

        val minY = plotted.minOf { it.second }
        val maxY = plotted.maxOf { it.second }
        // A flat series would otherwise divide by zero and draw nothing.
        val spanY = (maxY - minY).takeIf { it > 1e-9 } ?: 1.0
        val padded = spanY * 0.1
        val lowY = minY - padded
        val highY = maxY + padded

        val leftPad = 44.dp.toPx()
        val bottomPad = 14.dp.toPx()
        val plotW = size.width - leftPad
        val plotH = size.height - bottomPad

        fun yFor(v: Double) = (plotH - ((v - lowY) / (highY - lowY) * plotH)).toFloat()

        // Horizontal gridlines with their values, so the trace can actually be read.
        listOf(highY, (highY + lowY) / 2, lowY).forEach { value ->
            val y = yFor(value)
            drawLine(gridColor, Offset(leftPad, y), Offset(size.width, y), strokeWidth = 1f)
            drawText(
                textMeasurer = textMeasurer,
                text = formatValue(value),
                topLeft = Offset(0f, y - 6.dp.toPx()),
                style = labelStyle,
            )
        }

        // Zero line matters for signed series: it separates discharge from regen.
        if (lowY < 0 && highY > 0) {
            val zero = yFor(0.0)
            drawLine(axisColor, Offset(leftPad, zero), Offset(size.width, zero), strokeWidth = 2f)
        }

        val minX = plotted.first().first
        val spanX = (plotted.last().first - minX).coerceAtLeast(1L).toFloat()
        fun xFor(ts: Long) = leftPad + ((ts - minX).toFloat() / spanX) * plotW

        val path = Path().apply {
            moveTo(xFor(plotted.first().first), yFor(plotted.first().second))
            plotted.drop(1).forEach { lineTo(xFor(it.first), yFor(it.second)) }
        }
        drawPath(path, lineColor, style = Stroke(width = 2.5f))

        drawAxisLabels(textMeasurer, labelStyle, leftPad, plotH, bottomPad, spanX, unit)
    }
}

private fun DrawScope.drawAxisLabels(
    textMeasurer: TextMeasurer,
    style: TextStyle,
    leftPad: Float,
    plotH: Float,
    bottomPad: Float,
    spanX: Float,
    unit: String,
) {
    val minutes = (spanX / 60_000f).roundToInt()
    val elapsed = if (minutes >= 1) "$minutes min" else "${(spanX / 1000f).roundToInt()} s"
    drawText(
        textMeasurer = textMeasurer,
        text = "0",
        topLeft = Offset(leftPad, plotH + bottomPad - 12.dp.toPx()),
        style = style,
    )
    val label = textMeasurer.measure(elapsed, style)
    drawText(
        textMeasurer = textMeasurer,
        text = elapsed,
        topLeft = Offset(size.width - label.size.width, plotH + bottomPad - 12.dp.toPx()),
        style = style,
    )
    drawText(
        textMeasurer = textMeasurer,
        text = unit,
        topLeft = Offset(0f, plotH + bottomPad - 12.dp.toPx()),
        style = style,
    )
}

private fun formatValue(v: Double): String = when {
    abs(v) >= 100 -> v.roundToInt().toString()
    abs(v) >= 10 -> "%.1f".format(v)
    else -> "%.2f".format(v)
}

/**
 * Bucket-average a long series down to [max] points.
 *
 * At the new sample rate a one-hour drive is tens of thousands of readings; handing all
 * of them to a Path makes the history screen crawl. Averaging within buckets keeps the
 * shape of the trace, unlike dropping every Nth point which would alias spikes.
 */
private fun downsample(points: List<Pair<Long, Double>>, max: Int): List<Pair<Long, Double>> {
    if (points.size <= max) return points
    val bucketSize = points.size.toDouble() / max
    return (0 until max).map { i ->
        val from = (i * bucketSize).toInt()
        val to = ((i + 1) * bucketSize).toInt().coerceAtMost(points.size)
        val bucket = points.subList(from, to.coerceAtLeast(from + 1))
        bucket[bucket.size / 2].first to bucket.sumOf { it.second } / bucket.size
    }
}

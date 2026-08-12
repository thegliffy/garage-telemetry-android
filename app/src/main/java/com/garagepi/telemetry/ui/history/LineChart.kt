package com.garagepi.telemetry.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Minimal dependency-free line chart: (timestampMs, value) points over time. */
@Composable
fun LineChart(points: List<Pair<Long, Double>>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp),
    ) {
        if (points.size < 2) return@Canvas

        val minX = points.first().first.toFloat()
        val maxX = points.last().first.toFloat()
        val minY = points.minOf { it.second }.toFloat()
        val maxY = points.maxOf { it.second }.toFloat()
        val spanX = (maxX - minX).coerceAtLeast(1f)
        val spanY = (maxY - minY).coerceAtLeast(1f)

        fun toOffset(point: Pair<Long, Double>): Offset {
            val x = ((point.first.toFloat() - minX) / spanX) * size.width
            val y = size.height - ((point.second.toFloat() - minY) / spanY) * size.height
            return Offset(x, y)
        }

        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(toOffset(points.first()).x, toOffset(points.first()).y)
            points.drop(1).forEach { p ->
                val offset = toOffset(p)
                lineTo(offset.x, offset.y)
            }
        }
        drawPath(path = path, color = lineColor, style = Stroke(width = 4f))
    }
}

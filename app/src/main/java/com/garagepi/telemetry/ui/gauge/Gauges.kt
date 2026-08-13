package com.garagepi.telemetry.ui.gauge

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garagepi.telemetry.obd.TelemetryField
import com.garagepi.telemetry.obd.TelemetryFields
import com.garagepi.telemetry.ui.theme.ChargeGreen
import com.garagepi.telemetry.ui.theme.DischargeRed
import kotlin.math.abs

/**
 * Renders one tile's value in the requested style.
 *
 * [values] is the whole latest-values map rather than a single number because
 * [TileStyle.BATT_TEMP_PAIR] needs a second reading (pack coldest point) alongside the
 * tile's own field.
 */
@Composable
fun TileContent(
    field: TelemetryField,
    style: TileStyle,
    values: Map<String, Double>,
    compact: Boolean,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    val value = values[field.pid]
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = field.label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor.copy(alpha = 0.7f),
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        when (style) {
            TileStyle.NUMBER -> NumberReadout(field, value, compact, textColor)
            TileStyle.ARC -> ArcGauge(field, value, compact, textColor, trackColor, accentColor)
            TileStyle.POWER_ARC -> PowerArcGauge(field, value, compact, textColor, trackColor)
            TileStyle.THERMOMETER -> Thermometer(field, value, compact, textColor, trackColor, accentColor)
            TileStyle.BATT_TEMP_PAIR -> BatteryTempPair(field, values, compact, textColor, trackColor)
            TileStyle.TIRE_QUAD -> TireQuad(field, values, compact, textColor)
            TileStyle.MOTOR_PAIR -> MotorPair(field, values, compact, textColor, trackColor)
        }
    }
}

/**
 * All four corners at once, positioned as the car sits — front pair on top, rear below —
 * so an odd corner is spotted without reading labels.
 *
 * Works for pressure and temperature alike: the anchor field supplies the unit and
 * precision, and the sibling pids are derived from it.
 */
@Composable
private fun TireQuad(
    field: TelemetryField,
    values: Map<String, Double>,
    compact: Boolean,
    textColor: Color,
) {
    val temperature = field.pid == TelemetryFields.TIRE_FL_TEMP.pid
    val corners = if (temperature) {
        listOf(TelemetryFields.TIRE_FL_TEMP, TelemetryFields.TIRE_FR_TEMP)
            .zip(listOf(TelemetryFields.TIRE_RL_TEMP, TelemetryFields.TIRE_RR_TEMP))
    } else {
        listOf(TelemetryFields.TIRE_FL, TelemetryFields.TIRE_FR)
            .zip(listOf(TelemetryFields.TIRE_RL, TelemetryFields.TIRE_RR))
    }

    val style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 1.dp else 4.dp),
    ) {
        // corners is [(FL,RL), (FR,RR)] — transpose to draw front row then rear row.
        listOf(corners.map { it.first }, corners.map { it.second }).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                row.forEach { corner ->
                    Text(
                        // A sleeping sensor is omitted by decodeTpms rather than sent as
                        // 0, so a blank corner means "no reading", not a flat tire.
                        text = values[corner.pid]?.let { format(it, field.decimals) } ?: "--",
                        style = style,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor,
                        maxLines = 1,
                    )
                }
            }
        }
        Text(
            text = if (temperature) "°C  FL FR / RL RR" else "psi  FL FR / RL RR",
            style = MaterialTheme.typography.labelSmall,
            color = textColor.copy(alpha = 0.6f),
            maxLines = 1,
        )
    }
}

/** Front above rear. RPM goes negative in reverse, so each pane gets a bidirectional bar. */
@Composable
private fun MotorPair(
    field: TelemetryField,
    values: Map<String, Double>,
    compact: Boolean,
    textColor: Color,
    trackColor: Color,
) {
    val panes = listOf(
        "F" to values[TelemetryFields.MOTOR_RPM_FRONT.pid],
        "R" to values[TelemetryFields.MOTOR_RPM_REAR.pid],
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp),
    ) {
        panes.forEach { (tag, value) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = tag,
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.6f),
                )
                Text(
                    text = value?.let { format(it, field.decimals) } ?: "--",
                    style = if (compact) {
                        MaterialTheme.typography.bodyLarge
                    } else {
                        MaterialTheme.typography.titleLarge
                    },
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    maxLines = 1,
                )
            }
            Canvas(modifier = Modifier.fillMaxWidth().height(4.dp)) {
                val zero = fractionOf(0.0, field.min, field.max) ?: 0.5f
                drawRect(color = trackColor, size = Size(size.width, size.height))
                value?.let { v ->
                    fractionOf(v, field.min, field.max)?.let { f ->
                        val from = minOf(zero, f) * size.width
                        val to = maxOf(zero, f) * size.width
                        drawRect(
                            color = if (v < 0) ChargeGreen else DischargeRed,
                            topLeft = Offset(from, 0f),
                            size = Size((to - from).coerceAtLeast(2f), size.height),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberReadout(field: TelemetryField, value: Double?, compact: Boolean, textColor: Color) {
    // Bit flags read Yes/No; "1.0" tells a driver nothing about whether it is charging.
    if (field.isBoolean) {
        Text(
            text = value?.let { if (it != 0.0) "Yes" else "No" } ?: "--",
            style = if (compact) {
                MaterialTheme.typography.titleLarge
            } else {
                MaterialTheme.typography.headlineMedium
            },
            color = if (value != null && value != 0.0) ChargeGreen else textColor,
            maxLines = 1,
        )
        return
    }

    val charging = field.signedFlow && value != null && value < 0
    val discharging = field.signedFlow && value != null && value > 0
    val color = when {
        discharging -> DischargeRed
        charging -> ChargeGreen
        else -> textColor
    }
    val shown = value?.let { if (field.signedFlow) abs(it) else it }
    Text(
        text = shown?.let { format(it, field.decimals) } ?: "--",
        style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
        color = color,
        maxLines = 1,
    )
    Text(
        text = when {
            discharging && !compact -> "${field.unit} · discharging"
            charging && !compact -> "${field.unit} · charging"
            else -> field.unit
        },
        style = MaterialTheme.typography.labelSmall,
        color = color.copy(alpha = 0.8f),
        maxLines = 1,
    )
}

@Composable
private fun ArcGauge(
    field: TelemetryField,
    value: Double?,
    compact: Boolean,
    textColor: Color,
    trackColor: Color,
    accentColor: Color,
) {
    val fraction = value?.let { fractionOf(it, field.min, field.max) }
    Box(contentAlignment = Alignment.BottomCenter) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f), // a semicircle is twice as wide as it is tall
        ) {
            val stroke = if (compact) 6.dp.toPx() else 9.dp.toPx()
            drawGaugeArc(0f, 1f, trackColor, stroke)
            fraction?.let { drawGaugeArc(0f, it, accentColor, stroke) }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value?.let { format(it, field.decimals) } ?: "--",
                style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                maxLines = 1,
            )
            if (!compact) {
                Text(field.unit, style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.7f))
            }
        }
    }
}

/**
 * Bidirectional arc where zero sits at its true proportional position, not the midpoint.
 * Pack power runs −180 kW regen to +270 kW, so zero lands at 40% — centring it would
 * misreport every reading.
 */
@Composable
private fun PowerArcGauge(
    field: TelemetryField,
    value: Double?,
    compact: Boolean,
    textColor: Color,
    trackColor: Color,
) {
    val zero = fractionOf(0.0, field.min, field.max) ?: 0.5f
    val fraction = value?.let { fractionOf(it, field.min, field.max) }
    val charging = value != null && value < 0
    val valueColor = when {
        value == null -> textColor
        value > 0 -> DischargeRed
        value < 0 -> ChargeGreen
        else -> textColor
    }

    Box(contentAlignment = Alignment.BottomCenter) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f),
        ) {
            val stroke = if (compact) 6.dp.toPx() else 9.dp.toPx()
            drawGaugeArc(0f, 1f, trackColor, stroke)
            fraction?.let {
                // Fill from the zero point outwards, so the bar grows left for regen and
                // right for power rather than always from the left end.
                val from = minOf(zero, it)
                val to = maxOf(zero, it)
                drawGaugeArc(from, to, if (charging) ChargeGreen else DischargeRed, stroke)
            }
            // Zero tick, so the split point is visible even at rest.
            drawZeroTick(zero, textColor.copy(alpha = 0.6f), stroke)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value?.let { format(abs(it), field.decimals) } ?: "--",
                style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = valueColor,
                maxLines = 1,
            )
            if (!compact) {
                Text(
                    text = field.unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = valueColor.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun Thermometer(
    field: TelemetryField,
    value: Double?,
    compact: Boolean,
    textColor: Color,
    trackColor: Color,
    accentColor: Color,
) {
    val fraction = value?.let { fractionOf(it, field.min, field.max) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 34.dp else 48.dp),
        ) {
            drawThermometerTrack(trackColor)
            fraction?.let { drawThermometerFill(it, accentColor) }
        }
        Text(
            text = value?.let { "${format(it, field.decimals)}${field.unit}" } ?: "--",
            style = if (compact) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleLarge,
            color = textColor,
            maxLines = 1,
        )
    }
}

/**
 * Two live markers on one scale: the pack's hottest and coldest point right now, both from
 * the same 220101 frame. Not a historical range — the gap between them is the current
 * thermal spread, and a widening gap means uneven cooling.
 */
@Composable
private fun BatteryTempPair(
    field: TelemetryField,
    values: Map<String, Double>,
    compact: Boolean,
    textColor: Color,
    trackColor: Color,
) {
    val high = values[field.pid]
    val low = values[TelemetryFields.BATT_TEMP_MIN.pid]
    val highFraction = high?.let { fractionOf(it, field.min, field.max) }
    val lowFraction = low?.let { fractionOf(it, field.min, field.max) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 30.dp else 42.dp)
                .padding(horizontal = 4.dp),
        ) {
            val barHeight = 10.dp.toPx()
            val top = (size.height - barHeight) / 2f
            drawRect(color = trackColor, topLeft = Offset(0f, top), size = Size(size.width, barHeight))

            // Span between the two readings, so the spread is visible at a glance.
            if (highFraction != null && lowFraction != null) {
                val left = minOf(lowFraction, highFraction) * size.width
                val right = maxOf(lowFraction, highFraction) * size.width
                drawRect(
                    color = DischargeRed.copy(alpha = 0.35f),
                    topLeft = Offset(left, top),
                    size = Size((right - left).coerceAtLeast(2f), barHeight),
                )
            }
            lowFraction?.let { drawMarker(it, ChargeGreen, barHeight, top) }
            highFraction?.let { drawMarker(it, DischargeRed, barHeight, top) }
        }
        Text(
            text = if (high != null && low != null) {
                "${format(low, field.decimals)} – ${format(high, field.decimals)}${field.unit}"
            } else {
                "--"
            },
            style = if (compact) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleLarge,
            color = textColor,
            maxLines = 1,
        )
        if (!compact && high != null && low != null) {
            Text(
                text = "spread ${format(high - low, field.decimals)}${field.unit}",
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.7f),
            )
        }
    }
}

// --- drawing helpers ---------------------------------------------------------------

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGaugeArc(
    from: Float,
    to: Float,
    color: Color,
    stroke: Float,
) {
    val inset = stroke / 2f
    val diameter = size.width - stroke
    drawArc(
        color = color,
        startAngle = 180f + from * 180f,
        sweepAngle = (to - from) * 180f,
        useCenter = false,
        topLeft = Offset(inset, inset),
        size = Size(diameter, diameter),
        style = Stroke(width = stroke, cap = StrokeCap.Round),
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawZeroTick(
    fraction: Float,
    color: Color,
    stroke: Float,
) {
    val inset = stroke / 2f
    val diameter = size.width - stroke
    drawArc(
        color = color,
        startAngle = 180f + fraction * 180f,
        sweepAngle = 1.5f,
        useCenter = false,
        topLeft = Offset(inset, inset),
        size = Size(diameter, diameter),
        style = Stroke(width = stroke * 1.3f),
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawThermometerTrack(color: Color) {
    val barHeight = 10.dp.toPx()
    val top = (size.height - barHeight) / 2f
    drawRect(color = color, topLeft = Offset(0f, top), size = Size(size.width, barHeight))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawThermometerFill(
    fraction: Float,
    color: Color,
) {
    val barHeight = 10.dp.toPx()
    val top = (size.height - barHeight) / 2f
    drawRect(
        color = color,
        topLeft = Offset(0f, top),
        size = Size(size.width * fraction, barHeight),
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMarker(
    fraction: Float,
    color: Color,
    barHeight: Float,
    top: Float,
) {
    val x = (fraction * size.width).coerceIn(2f, size.width - 2f)
    drawRect(
        color = color,
        topLeft = Offset(x - 2f, top - 4f),
        size = Size(4f, barHeight + 8f),
    )
}

/**
 * Precision comes from the field, not from how large the number happens to be — a tenth of
 * a mph is noise while a hundredth of a cell volt matters. Rounds rather than truncates.
 */
private fun format(v: Double, decimals: Int): String = "%.${decimals.coerceIn(0, 3)}f".format(v)

package com.garagepi.telemetry.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.garagepi.telemetry.data.ReadingEntity
import com.garagepi.telemetry.data.TripSessionEntity
import com.garagepi.telemetry.obd.TelemetryField
import com.garagepi.telemetry.obd.TelemetryFields
import com.garagepi.telemetry.obd.Units
import com.garagepi.telemetry.sync.AppSettings
import com.garagepi.telemetry.ui.theme.BatteryTempLowBlue
import com.garagepi.telemetry.ui.theme.ChargeGreen
import com.garagepi.telemetry.ui.theme.DischargeRed
import com.garagepi.telemetry.ui.theme.MotorFrontBlue
import com.garagepi.telemetry.ui.theme.MotorRearYellow
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = viewModel()) {
    val trips by viewModel.trips.collectAsState()
    val selectedTripId by viewModel.selectedTripId.collectAsState()
    val readings by viewModel.selectedTripReadings.collectAsState()
    val summary by viewModel.selectedTripSummary.collectAsState()
    val context = LocalContext.current
    val selectedTrip = trips.firstOrNull { it.id == selectedTripId }

    if (selectedTripId == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "History", style = MaterialTheme.typography.headlineMedium)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(trips) { trip ->
                    OutlinedButton(
                        onClick = { viewModel.selectTrip(trip.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(formatSessionLabel(trip))
                    }
                }
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "History", style = MaterialTheme.typography.headlineMedium)
        OutlinedButton(onClick = { viewModel.clearSelection() }) { Text("Back to list") }

        summary?.let { SummaryCard(it, isCharge = selectedTrip?.isCharge == true) }

        HorizontalDivider()

        val fahrenheit = remember { AppSettings(context).temperatureInFahrenheit }
        SessionCharts(readings = readings, fahrenheit = fahrenheit)
    }
}

@Composable
private fun SessionCharts(readings: List<ReadingEntity>, fahrenheit: Boolean) {
    fun seriesFor(field: TelemetryField): List<Pair<Long, Double>> =
        ChartSeries.downsample(
            readings
                .filter { it.pid == field.pid }
                .map {
                    it.ts to if (fahrenheit && field.isTemperature) {
                        Units.celsiusToFahrenheit(it.value)
                    } else {
                        it.value
                    }
                },
        )

    TelemetryFields.HISTORY_SINGLE_CHARTS.forEach { raw ->
        val field = Units.forDisplay(raw, fahrenheit)
        val series = seriesFor(raw)
        if (series.isNotEmpty()) {
            Text(
                text = "${field.label} (${field.unit})",
                style = MaterialTheme.typography.titleLarge,
            )
            LineChart(points = series, unit = field.unit)
        }
    }

    val battMax = Units.forDisplay(TelemetryFields.BATT_TEMP, fahrenheit)
    val battMaxSeries = seriesFor(TelemetryFields.BATT_TEMP)
    val battMinSeries = seriesFor(TelemetryFields.BATT_TEMP_MIN)
    if (battMaxSeries.isNotEmpty() || battMinSeries.isNotEmpty()) {
        Text(
            text = "Battery temps  ·  red max  ·  blue min  (${battMax.unit})",
            style = MaterialTheme.typography.titleLarge,
        )
        LineChart(
            points = battMaxSeries,
            extraPoints = battMinSeries,
            unit = battMax.unit,
            lineColor = DischargeRed,
            extraLineColor = BatteryTempLowBlue,
        )
    }

    val motorFront = seriesFor(TelemetryFields.MOTOR_RPM_FRONT)
    val motorRear = seriesFor(TelemetryFields.MOTOR_RPM_REAR)
    if (motorFront.isNotEmpty() || motorRear.isNotEmpty()) {
        Text(
            text = "Motors  ·  blue front  ·  yellow rear  (rpm)",
            style = MaterialTheme.typography.titleLarge,
        )
        LineChart(
            points = motorFront,
            extraPoints = motorRear,
            unit = "rpm",
            lineColor = MotorFrontBlue,
            extraLineColor = MotorRearYellow,
        )
    }
}

@Composable
private fun SummaryCard(summary: TripSummary, isCharge: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                if (isCharge) "Charge summary" else "Drive summary",
                style = MaterialTheme.typography.titleLarge,
            )

            SummaryRow("Duration", formatDuration(summary.durationMs))
            if (isCharge) {
                SummaryRow("Energy added", "%.2f kWh".format(summary.energyRegenKwh), ChargeGreen)
                summary.socUsed?.let { used ->
                    SummaryRow("Battery gained", "%.1f %%".format(-used), ChargeGreen)
                }
            } else {
                SummaryRow(
                    "Distance",
                    summary.distanceMiles?.let { "%.1f mi".format(it) }
                        ?: "— no odometer readings yet",
                )
                SummaryRow("Energy used", "%.2f kWh".format(summary.energyUsedKwh), DischargeRed)
                if (summary.energyRegenKwh > 0.01) {
                    SummaryRow("Regenerated", "%.2f kWh".format(summary.energyRegenKwh), ChargeGreen)
                }
                SummaryRow("Net energy", "%.2f kWh".format(summary.netEnergyKwh))
                SummaryRow(
                    "Efficiency",
                    summary.efficiencyMilesPerKwh?.let { "%.2f mi/kWh".format(it) }
                        ?: "— needs distance",
                )
                summary.socUsed?.let { SummaryRow("Battery used", "%.1f %%".format(it)) }
            }
            SummaryRow("Samples", summary.sampleCount.toString())
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, valueColor: Color? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun formatDuration(ms: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m ${seconds}s"
}

private fun formatSessionLabel(trip: TripSessionEntity): String {
    val kind = if (trip.isCharge) "Charge" else "Drive"
    val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    val start = format.format(Date(trip.startedAt))
    val duration = trip.endedAt?.let { (it - trip.startedAt) / 60_000 }
    return if (duration != null) "$kind · $start · ${duration}min" else "$kind · $start · in progress"
}

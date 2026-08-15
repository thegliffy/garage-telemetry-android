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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.garagepi.telemetry.obd.TelemetryFields
import com.garagepi.telemetry.obd.Units
import com.garagepi.telemetry.sync.AppSettings
import com.garagepi.telemetry.ui.theme.ChargeGreen
import com.garagepi.telemetry.ui.theme.DischargeRed
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

    if (selectedTripId == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "Trip history", style = MaterialTheme.typography.headlineMedium)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(trips) { trip ->
                    OutlinedButton(
                        onClick = { viewModel.selectTrip(trip.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(formatTripLabel(trip.startedAt, trip.endedAt))
                    }
                }
            }
        }
        return
    }

    // Scrollable: previously a plain Column, so every chart below the fold — battery
    // temperature and the 12V aux among them — was rendered but unreachable.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Trip history", style = MaterialTheme.typography.headlineMedium)
        OutlinedButton(onClick = { viewModel.clearSelection() }) { Text("Back to trip list") }

        summary?.let { SummaryCard(it) }

        HorizontalDivider()

        val fahrenheit = remember { AppSettings(context).temperatureInFahrenheit }
        TelemetryFields.CHART_FIELDS.forEach { raw ->
            val field = Units.forDisplay(raw, fahrenheit)
            val series = ChartSeries.downsample(
                readings
                    .filter { it.pid == field.pid }
                    // Stored Celsius, shown in whatever the setting says — the axis labels come
                    // from the same converted values, so the scale follows automatically.
                    .map { it.ts to if (fahrenheit && raw.isTemperature) Units.celsiusToFahrenheit(it.value) else it.value },
            )
            if (series.isNotEmpty()) {
                Text(
                    text = "${field.label} (${field.unit})",
                    style = MaterialTheme.typography.titleLarge,
                )
                LineChart(points = series, unit = field.unit)
            }
        }
    }
}

@Composable
private fun SummaryCard(summary: TripSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Trip summary", style = MaterialTheme.typography.titleLarge)

            SummaryRow("Duration", formatDuration(summary.durationMs))
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
            SummaryRow("Samples", summary.sampleCount.toString())
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color? = null) {
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

private fun formatTripLabel(startedAt: Long, endedAt: Long?): String {
    val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    val start = format.format(Date(startedAt))
    val duration = endedAt?.let { (it - startedAt) / 60_000 }
    return if (duration != null) "$start · ${duration}min" else "$start · in progress"
}

package com.garagepi.telemetry.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.garagepi.telemetry.obd.TelemetryFields
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = viewModel()) {
    val trips by viewModel.trips.collectAsState()
    val selectedTripId by viewModel.selectedTripId.collectAsState()
    val readings by viewModel.selectedTripReadings.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Trip history", style = MaterialTheme.typography.headlineMedium)

        if (selectedTripId == null) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(trips) { trip ->
                    Button(onClick = { viewModel.selectTrip(trip.id) }) {
                        Text(formatTripLabel(trip.startedAt, trip.endedAt))
                    }
                }
            }
        } else {
            Button(onClick = { viewModel.clearSelection() }) { Text("Back to trip list") }
            TelemetryFields.DASHBOARD_FIELDS.forEach { field ->
                val series = readings.filter { it.pid == field.pid }.map { it.ts to it.value }
                if (series.isNotEmpty()) {
                    Text(text = "${field.label} (${field.unit})", style = MaterialTheme.typography.titleLarge)
                    LineChart(points = series)
                }
            }
        }
    }
}

private fun formatTripLabel(startedAt: Long, endedAt: Long?): String {
    val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    val start = format.format(Date(startedAt))
    val duration = endedAt?.let { (it - startedAt) / 60_000 }
    return if (duration != null) "$start · ${duration}min" else "$start · in progress"
}

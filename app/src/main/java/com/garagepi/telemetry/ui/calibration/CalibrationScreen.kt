package com.garagepi.telemetry.ui.calibration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.garagepi.telemetry.obd.CandidateSpec

@Composable
fun CalibrationScreen(viewModel: CalibrationViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Calibration", style = MaterialTheme.typography.headlineMedium)
        Text(
            "The Ioniq 5 doesn't publish where the odometer and speed live in its data, so " +
                "the app works it out from what you can see on the dash. Do this in the car " +
                "with the app connected.",
            style = MaterialTheme.typography.bodyLarge,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CalibrationTarget.entries.forEach { target ->
                FilterChip(
                    selected = uiState.target == target,
                    onClick = { viewModel.selectTarget(target) },
                    label = { Text(target.title) },
                )
            }
        }

        uiState.savedSpec?.let { spec ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Already calibrated", fontWeight = FontWeight.Medium)
                    Text(spec.describe(), style = MaterialTheme.typography.bodyLarge)
                    OutlinedButton(onClick = viewModel::clearSaved) { Text("Clear and redo") }
                }
            }
        }

        HorizontalDivider()

        Text(uiState.target.prompt, style = MaterialTheme.typography.bodyLarge)

        OutlinedTextField(
            value = uiState.enteredValue,
            onValueChange = viewModel::updateValue,
            label = { Text("${uiState.target.title} shown on the dash") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::takeSample) {
                Text(if (uiState.samplesTaken == 0) "Take sample" else "Take another sample")
            }
            if (uiState.samplesTaken > 0) {
                OutlinedButton(onClick = viewModel::reset) { Text("Start over") }
            }
        }

        uiState.message?.let { message ->
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                color = if (uiState.candidates.isEmpty() && uiState.samplesTaken == 0) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }

        uiState.candidates.forEach { spec ->
            CandidateRow(
                spec = spec,
                reads = uiState.previews[spec],
                onSave = { viewModel.save(spec) },
            )
        }
    }
}

@Composable
private fun CandidateRow(spec: CandidateSpec, reads: Double?, onSave: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(spec.describe(), fontWeight = FontWeight.Medium)
                reads?.let {
                    Text(
                        "currently reads ${"%.1f".format(it)}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            Button(onClick = onSave) { Text("Use this") }
        }
    }
}

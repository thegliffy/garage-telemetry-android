package com.garagepi.telemetry.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Sync settings", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "Points at garage-telemetry-api on your home network. " +
                "Trips upload automatically once these are set and you're back on wifi.",
            style = MaterialTheme.typography.bodyLarge,
        )

        OutlinedTextField(
            value = uiState.baseUrl,
            onValueChange = viewModel::updateBaseUrl,
            label = { Text("API base URL (e.g. http://192.168.1.50:8000)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = uiState.apiKey,
            onValueChange = viewModel::updateApiKey,
            label = { Text("Android API key") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(onClick = viewModel::save) { Text("Save") }
    }
}

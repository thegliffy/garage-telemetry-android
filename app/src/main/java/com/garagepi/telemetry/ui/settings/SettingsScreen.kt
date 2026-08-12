package com.garagepi.telemetry.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.garagepi.telemetry.data.RetentionPolicy

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Sync settings", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "Points at garage-telemetry-api on your home network. " +
                "Drives upload automatically once these are set and you're back on wifi.",
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

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::save) { Text("Save") }
            OutlinedButton(onClick = viewModel::testConnection, enabled = !uiState.testing) {
                Text(if (uiState.testing) "Testing…" else "Test connection")
            }
        }

        uiState.connectionTest?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = if (uiState.connectionTestOk) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }

        HorizontalDivider()

        Text(text = "Data retention", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "A session is one drive: from connecting to the adapter until the car " +
                "powers off. This controls how long finished sessions are kept on the phone.",
            style = MaterialTheme.typography.bodyLarge,
        )

        Column(Modifier.selectableGroup()) {
            RetentionPolicy.entries.forEach { policy ->
                RetentionOption(
                    policy = policy,
                    selected = uiState.retentionPolicy == policy,
                    onSelect = { viewModel.selectRetentionPolicy(policy) },
                )
            }
        }

        // Surface the consequence of each choice rather than letting it bite silently.
        when (uiState.retentionPolicy) {
            RetentionPolicy.ONE_MONTH, RetentionPolicy.ONE_YEAR -> Text(
                text = "Heads up: this is a strict age limit. A drive older than the window is " +
                    "deleted even if it never reached the server — for example if you were away " +
                    "from home when it aged out.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            RetentionPolicy.UNTIL_UPLOADED -> if (!uiState.syncConfigured) {
                Text(
                    text = "Sync isn't configured, so nothing will ever be deleted under this " +
                        "policy and storage will keep growing. Set the API URL and key above.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            RetentionPolicy.FOREVER -> Unit
        }

        Text(
            text = "Stored now: ${uiState.sessionCount} sessions, " +
                "${uiState.readingCount} readings, ${formatSize(uiState.dbSizeBytes)}",
            style = MaterialTheme.typography.bodyLarge,
        )

        OutlinedButton(
            onClick = viewModel::runCleanupNow,
            enabled = !uiState.cleanupRunning,
        ) {
            Text(if (uiState.cleanupRunning) "Cleaning up…" else "Run cleanup now")
        }
    }
}

@Composable
private fun RetentionOption(policy: RetentionPolicy, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(Modifier.padding(start = 8.dp)) {
            Text(text = policy.label, style = MaterialTheme.typography.bodyLarge)
            Text(text = policy.description, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.0f kB".format(bytes / 1_000.0)
    else -> "$bytes B"
}

package com.garagepi.telemetry.ui.settings

import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.garagepi.telemetry.data.LoggingGranularity
import com.garagepi.telemetry.data.RetentionPolicy
import com.garagepi.telemetry.ui.missingPermissions

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var adapterMenuOpen by remember { mutableStateOf(false) }
    var needsPermission by remember { mutableStateOf(missingPermissions(context).isNotEmpty()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { needsPermission = missingPermissions(context).isNotEmpty() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "OBD2 adapter", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "Pair the ELM327 in Android's Bluetooth settings first, then pick it here. " +
                "The Live tab connects to whichever adapter is selected.",
            style = MaterialTheme.typography.bodyLarge,
        )

        Text(
            text = uiState.selectedDeviceLabel?.let { "Selected: $it" } ?: "No adapter selected",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )

        if (needsPermission) {
            // Without this the picker would just say "no paired devices", which points at
            // the wrong problem entirely.
            Button(onClick = { permissionLauncher.launch(missingPermissions(context).toTypedArray()) }) {
                Text("Grant Bluetooth permission")
            }
        }

        Box {
            OutlinedButton(
                onClick = { adapterMenuOpen = true },
                enabled = !needsPermission,
            ) {
                Text(if (uiState.selectedDeviceAddress == null) "Choose adapter" else "Change adapter")
            }
            DropdownMenu(expanded = adapterMenuOpen, onDismissRequest = { adapterMenuOpen = false }) {
                val devices = viewModel.pairedDevices()
                if (devices.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("No paired devices — pair the ELM327 first") },
                        onClick = { adapterMenuOpen = false },
                    )
                }
                devices.forEach { device ->
                    DropdownMenuItem(
                        text = { Text(deviceLabel(device)) },
                        onClick = {
                            adapterMenuOpen = false
                            viewModel.selectDevice(device)
                        },
                    )
                }
            }
        }

        HorizontalDivider()

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
            OutlinedButton(
                onClick = viewModel::syncNow,
                // Pointless without an endpoint; a button that silently does nothing is
                // worse than one that is visibly unavailable.
                enabled = uiState.syncConfigured && !uiState.syncing,
            ) {
                Text(if (uiState.syncing) "Syncing…" else "Sync now")
            }
        }

        Text(
            text = if (uiState.pendingUploads == 0) {
                "Nothing waiting to upload"
            } else {
                "${uiState.pendingUploads} readings waiting to upload"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

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

        Text(text = "Display", style = MaterialTheme.typography.headlineMedium)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.setFahrenheit(!uiState.fahrenheit) }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Temperatures in Fahrenheit", style = MaterialTheme.typography.bodyLarge)
                Text(
                    // Worth stating: the stored series stays Celsius so it matches what
                    // garagepi writes to the same table.
                    "Display only — readings are stored and uploaded in Celsius.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = uiState.fahrenheit,
                onCheckedChange = viewModel::setFahrenheit,
            )
        }

        HorizontalDivider()

        Text(text = "Data granularity", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "How often samples are saved to the phone and uploaded. Live gauges still " +
                "update every poll; coarser settings only thin the log. Takes effect on the " +
                "next sample — no reconnect needed.",
            style = MaterialTheme.typography.bodyLarge,
        )

        Column(Modifier.selectableGroup()) {
            LoggingGranularity.entries.forEach { granularity ->
                GranularityOption(
                    granularity = granularity,
                    selected = uiState.loggingGranularity == granularity,
                    onSelect = { viewModel.selectLoggingGranularity(granularity) },
                )
            }
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
private fun GranularityOption(
    granularity: LoggingGranularity,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(Modifier.padding(start = 8.dp)) {
            Text(text = granularity.label, style = MaterialTheme.typography.bodyLarge)
            Text(text = granularity.description, style = MaterialTheme.typography.labelSmall)
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

/** Reading `device.name` needs BLUETOOTH_CONNECT; fall back to the address without it. */
@SuppressLint("MissingPermission")
private fun deviceLabel(device: android.bluetooth.BluetoothDevice): String =
    runCatching { device.name }.getOrNull() ?: device.address

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.0f kB".format(bytes / 1_000.0)
    else -> "$bytes B"
}

package com.garagepi.telemetry.ui.dashboard

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.garagepi.telemetry.obd.TelemetryField
import com.garagepi.telemetry.obd.TelemetryFields

@Composable
fun DashboardScreen(viewModel: DashboardViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var hasBluetoothPermission by remember { mutableStateOf(hasConnectPermission(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasBluetoothPermission = granted }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Garage Telemetry", style = MaterialTheme.typography.headlineMedium)

        ConnectionControls(
            state = uiState.connectionState,
            hasPermission = hasBluetoothPermission,
            onRequestPermission = { permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) },
            pairedDevices = { viewModel.pairedDevices() },
            onConnect = viewModel::connect,
            onDisconnect = viewModel::disconnect,
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(TelemetryFields.DASHBOARD_FIELDS) { field ->
                StatCard(field = field, value = uiState.latestValues[field.pid])
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun ConnectionControls(
    state: ConnectionState,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    pairedDevices: () -> List<BluetoothDevice>,
    onConnect: (BluetoothDevice) -> Unit,
    onDisconnect: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    when (state) {
        is ConnectionState.Disconnected, is ConnectionState.Error -> {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (state is ConnectionState.Error) {
                    Text(text = state.message, color = MaterialTheme.colorScheme.error)
                }
                if (!hasPermission) {
                    Button(onClick = onRequestPermission) { Text("Grant Bluetooth permission") }
                } else {
                    Button(onClick = { menuExpanded = true }) { Text("Connect to OBD2 adapter") }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        val devices = pairedDevices()
                        if (devices.isEmpty()) {
                            DropdownMenuItem(text = { Text("No paired devices — pair the ELM327 first") }, onClick = {})
                        }
                        devices.forEach { device ->
                            DropdownMenuItem(
                                text = { Text(device.name ?: device.address) },
                                onClick = {
                                    menuExpanded = false
                                    onConnect(device)
                                },
                            )
                        }
                    }
                }
            }
        }
        ConnectionState.Connecting -> Text("Connecting…")
        is ConnectionState.Connected -> {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Connected to ${state.deviceName}")
                OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
            }
        }
    }
}

@Composable
private fun StatCard(field: TelemetryField, value: Double?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(text = field.label, style = MaterialTheme.typography.labelSmall)
            Text(
                text = value?.let { "%.1f".format(it) } ?: "--",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(text = field.unit, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun hasConnectPermission(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
}

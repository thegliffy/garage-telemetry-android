package com.garagepi.telemetry.ui.dashboard

import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.garagepi.telemetry.R
import com.garagepi.telemetry.obd.TelemetryField
import com.garagepi.telemetry.obd.TelemetryFields
import com.garagepi.telemetry.service.ConnectionState
import com.garagepi.telemetry.sync.TileConfig
import com.garagepi.telemetry.ui.gauge.FieldStyles
import com.garagepi.telemetry.ui.gauge.TileContent
import com.garagepi.telemetry.ui.gauge.TileStyle
import com.garagepi.telemetry.ui.missingPermissions

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel(),
    onOpenCarDash: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val tiles by viewModel.tiles.collectAsState()
    val savedDevice by viewModel.savedDevice.collectAsState()
    val context = LocalContext.current

    var hasPermission by remember { mutableStateOf(missingPermissions(context).isEmpty()) }
    var editingTile by remember { mutableStateOf<Int?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { hasPermission = missingPermissions(context).isEmpty() }

    // The adapter is chosen in Settings now, so re-read on entry — otherwise this screen
    // keeps showing "no adapter" after one has just been picked.
    LaunchedEffect(Unit) { viewModel.refresh() }

    // Landscape gets the 5-wide layout; in portrait five columns would be ~70dp each,
    // too narrow for a value plus its label.
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val columns = if (landscape) 5 else 2

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
            )
            OutlinedButton(onClick = onOpenCarDash) { Text("Car mode") }
        }

        ConnectionControls(
            state = uiState.connectionState,
            savedDevice = savedDevice,
            hasPermission = hasPermission,
            onRequestPermission = { permissionLauncher.launch(missingPermissions(context).toTypedArray()) },
            onConnect = viewModel::connect,
            onDisconnect = viewModel::disconnect,
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(tiles) { index, tile ->
                val field = viewModel.fieldFor(tile.pid)
                StatCard(
                    field = field,
                    style = field?.let { FieldStyles.resolve(it, tile.style) },
                    values = uiState.latestValues,
                    compact = landscape,
                    onClick = { editingTile = index },
                )
            }
        }
    }

    editingTile?.let { index ->
        val tile = tiles.getOrNull(index) ?: TileConfig("")
        val field = viewModel.fieldFor(tile.pid)
        TilePickerDialog(
            currentPid = tile.pid,
            currentStyle = field?.let { FieldStyles.resolve(it, tile.style) },
            styleOptions = field?.let { FieldStyles.supported(it) }.orEmpty(),
            onPickField = { pid -> viewModel.setTileField(index, pid) },
            onPickStyle = { style -> viewModel.setTileStyle(index, style) },
            onDismiss = { editingTile = null },
        )
    }
}

@Composable
private fun ConnectionControls(
    state: ConnectionState,
    savedDevice: SavedDevice?,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    when (state) {
        is ConnectionState.Disconnected, is ConnectionState.Error -> {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (state is ConnectionState.Error) {
                    Text(text = state.message, color = MaterialTheme.colorScheme.error)
                }
                when {
                    !hasPermission ->
                        Button(onClick = onRequestPermission) { Text("Grant Bluetooth permission") }
                    // Say where to fix it rather than showing a button that cannot work.
                    savedDevice == null -> Text(
                        "No adapter selected — choose one in Settings.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    else -> Button(onClick = onConnect) { Text("Connect to ${savedDevice.label}") }
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
private fun StatCard(
    field: TelemetryField?,
    style: TileStyle?,
    values: Map<String, Double>,
    compact: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) 84.dp else 118.dp)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (compact) 6.dp else 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (field == null || style == null) {
                Text(
                    text = "Tap to set",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                TileContent(field = field, style = style, values = values, compact = compact)
            }
        }
    }
}

@Composable
private fun TilePickerDialog(
    currentPid: String,
    currentStyle: TileStyle?,
    styleOptions: List<TileStyle>,
    onPickField: (String) -> Unit,
    onPickStyle: (TileStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Show on this tile") },
        text = {
            // The field list alone is 40+ entries, so the whole dialog body scrolls.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // Style first: it only appears once a field is chosen, and only offers
                // styles that suit it — no thermometer for an odometer.
                if (styleOptions.size > 1) {
                    Text(
                        "Style",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        styleOptions.forEach { style ->
                            FilterChip(
                                selected = style == currentStyle,
                                onClick = { onPickStyle(style) },
                                label = { Text(style.label) },
                            )
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                }

                Text(
                    "Value",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                TelemetryFields.SELECTABLE.forEach { field ->
                    val selected = field.pid == currentPid
                    Text(
                        text = if (selected) "● ${field.label}" else "   ${field.label}",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPickField(field.pid) }
                            .padding(vertical = 10.dp),
                    )
                }
                Text(
                    text = if (currentPid.isEmpty()) "● Empty" else "   Empty",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPickField("") }
                        .padding(vertical = 10.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

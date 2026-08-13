package com.garagepi.telemetry.ui.dashboard

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.garagepi.telemetry.obd.TelemetryField
import com.garagepi.telemetry.obd.TelemetryFields
import com.garagepi.telemetry.service.LoggingState
import com.garagepi.telemetry.service.ObdLoggingService
import com.garagepi.telemetry.service.ObdLoggingState
import com.garagepi.telemetry.sync.AppSettings
import com.garagepi.telemetry.sync.TileConfig
import com.garagepi.telemetry.ui.gauge.TileStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Thin controller over [ObdLoggingService]. The connection and poll loop deliberately
 * live in the service, not here — a ViewModel dies with its Activity, which would cut a
 * drive short as soon as the screen locked.
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = AppSettings(application)
    private val bluetoothAdapter: BluetoothAdapter? =
        (application.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    val uiState: StateFlow<LoggingState> = ObdLoggingState.state

    private val _tiles = MutableStateFlow(settings.dashboardTiles)
    val tiles: StateFlow<List<TileConfig>> = _tiles.asStateFlow()

    private val _savedDevice = MutableStateFlow(readSavedDevice())
    /** Adapter chosen in Settings, or null if none has been picked yet. */
    val savedDevice: StateFlow<SavedDevice?> = _savedDevice.asStateFlow()

    /**
     * Re-reads settings that other screens can change. Called when the dashboard is shown,
     * because the adapter picker now lives in Settings and this screen has no other way to
     * learn that the choice changed.
     */
    fun refresh() {
        _savedDevice.value = readSavedDevice()
        _tiles.value = settings.dashboardTiles
    }

    fun connect() {
        _savedDevice.value?.let { ObdLoggingService.start(getApplication(), it.address) }
    }

    fun disconnect() = ObdLoggingService.stop(getApplication())

    /** `pid` empty clears the slot. Changing field resets style to that field's default. */
    fun setTileField(index: Int, pid: String) {
        update(index) { TileConfig(pid) }
    }

    fun setTileStyle(index: Int, style: TileStyle) {
        update(index) { it.copy(style = style) }
    }

    private fun update(index: Int, transform: (TileConfig) -> TileConfig) {
        val updated = _tiles.value.toMutableList()
        updated[index] = transform(updated[index])
        settings.dashboardTiles = updated
        _tiles.value = updated
    }

    fun fieldFor(pid: String): TelemetryField? = TelemetryFields.bySelectablePid(pid)

    @SuppressLint("MissingPermission")
    private fun readSavedDevice(): SavedDevice? {
        val address = settings.lastDeviceAddress ?: return null
        // Resolve the friendly name from the bonded list; fall back to the address if the
        // adapter is off or the device has since been unpaired.
        val name = runCatching {
            bluetoothAdapter?.bondedDevices?.firstOrNull { it.address == address }?.name
        }.getOrNull()
        return SavedDevice(address = address, label = name ?: address)
    }
}

data class SavedDevice(val address: String, val label: String)

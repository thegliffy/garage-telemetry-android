package com.garagepi.telemetry.ui.dashboard

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.garagepi.telemetry.service.LoggingState
import com.garagepi.telemetry.service.ObdLoggingService
import com.garagepi.telemetry.service.ObdLoggingState
import kotlinx.coroutines.flow.StateFlow

/**
 * Thin controller over [ObdLoggingService]. The connection and poll loop deliberately
 * live in the service, not here — a ViewModel dies with its Activity, which would cut a
 * drive short as soon as the screen locked.
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val bluetoothAdapter: BluetoothAdapter? =
        (application.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    val uiState: StateFlow<LoggingState> = ObdLoggingState.state

    @SuppressLint("MissingPermission")
    fun pairedDevices(): List<BluetoothDevice> = bluetoothAdapter?.bondedDevices?.toList().orEmpty()

    fun connect(device: BluetoothDevice) {
        ObdLoggingService.start(getApplication(), device.address)
    }

    fun disconnect() {
        ObdLoggingService.stop(getApplication())
    }
}

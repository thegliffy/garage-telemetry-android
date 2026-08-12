package com.garagepi.telemetry.ui.dashboard

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.garagepi.telemetry.bluetooth.Elm327Connection
import com.garagepi.telemetry.data.ReadingEntity
import com.garagepi.telemetry.data.TelemetryDatabase
import com.garagepi.telemetry.data.TripSessionEntity
import com.garagepi.telemetry.obd.ObdSession
import com.garagepi.telemetry.sync.AppSettings
import com.garagepi.telemetry.sync.SyncScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data class Connected(val deviceName: String) : ConnectionState
    data class Error(val message: String) : ConnectionState
}

data class DashboardUiState(
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val latestValues: Map<String, Double> = emptyMap(),
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val db = TelemetryDatabase.get(application)
    private val settings = AppSettings(application)
    private val bluetoothAdapter: BluetoothAdapter? =
        (application.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private var connection: Elm327Connection? = null
    private var obdSession: ObdSession? = null
    private var pollingJob: Job? = null
    private var currentTripId: Long? = null

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    @SuppressLint("MissingPermission")
    fun pairedDevices(): List<BluetoothDevice> = bluetoothAdapter?.bondedDevices?.toList().orEmpty()

    fun connect(device: BluetoothDevice) {
        val current = _uiState.value.connectionState
        if (current is ConnectionState.Connecting || current is ConnectionState.Connected) return

        _uiState.update { it.copy(connectionState = ConnectionState.Connecting) }
        viewModelScope.launch {
            var conn: Elm327Connection? = null
            try {
                conn = Elm327Connection.connect(device, bluetoothAdapter)
                val session = ObdSession(conn)
                session.initialize()
                connection = conn
                obdSession = session

                val tripId = db.tripSessionDao().insert(TripSessionEntity(startedAt = System.currentTimeMillis()))
                currentTripId = tripId
                settings.lastDeviceAddress = device.address

                _uiState.update {
                    it.copy(connectionState = ConnectionState.Connected(deviceLabel(device)))
                }
                startPolling(session, tripId)
            } catch (e: Exception) {
                if (obdSession == null) conn?.close() // init failed before we stored the session
                cleanupConnection()
                _uiState.update { it.copy(connectionState = ConnectionState.Error(e.message ?: "Connection failed")) }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun deviceLabel(device: BluetoothDevice): String = device.name ?: device.address

    private fun startPolling(session: ObdSession, tripId: Long) {
        pollingJob = viewModelScope.launch {
            while (isActive) {
                try {
                    val readings = session.pollOnce()
                    if (readings.isNotEmpty()) {
                        _uiState.update { state ->
                            state.copy(latestValues = state.latestValues + readings.associate { it.pid to it.value })
                        }
                        db.readingDao().insertAll(
                            readings.map { reading ->
                                ReadingEntity(
                                    tripSessionId = tripId,
                                    ts = reading.timestampMs,
                                    pid = reading.pid,
                                    value = reading.value,
                                )
                            },
                        )
                    }
                } catch (e: Exception) {
                    // Close the socket and end the trip here too — otherwise the adapter
                    // connection leaks and the trip never gets an endedAt, so SyncWorker
                    // re-queues it forever and the next connect orphans it entirely.
                    val failedTripId = currentTripId
                    cleanupConnection()
                    endTrip(failedTripId)
                    _uiState.update {
                        it.copy(
                            connectionState = ConnectionState.Error(e.message ?: "Read failed"),
                            latestValues = emptyMap(),
                        )
                    }
                    break
                }
                delay(1_000)
            }
        }
    }

    fun disconnect() {
        pollingJob?.cancel()
        val tripId = currentTripId
        cleanupConnection()
        _uiState.update { it.copy(connectionState = ConnectionState.Disconnected, latestValues = emptyMap()) }
        endTrip(tripId)
    }

    /** Marks the trip finished and nudges the sync job. Safe to call with a null/already-ended trip. */
    private fun endTrip(tripId: Long?) {
        if (tripId == null) return
        viewModelScope.launch {
            db.tripSessionDao().getById(tripId)?.let { trip ->
                if (trip.endedAt == null) {
                    db.tripSessionDao().update(trip.copy(endedAt = System.currentTimeMillis()))
                }
            }
            SyncScheduler.triggerNow(getApplication())
        }
    }

    private fun cleanupConnection() {
        obdSession?.close()
        obdSession = null
        connection = null
        pollingJob = null
        currentTripId = null
    }

    override fun onCleared() {
        super.onCleared()
        obdSession?.close()
    }
}

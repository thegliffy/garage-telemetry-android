package com.garagepi.telemetry.ui.settings

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.garagepi.telemetry.data.LoggingGranularity
import com.garagepi.telemetry.data.RetentionPolicy
import com.garagepi.telemetry.data.TelemetryDatabase
import com.garagepi.telemetry.sync.AppSettings
import com.garagepi.telemetry.sync.GarageApiClient
import com.garagepi.telemetry.sync.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class SettingsUiState(
    val baseUrl: String = "",
    val apiKey: String = "",
    val retentionPolicy: RetentionPolicy = RetentionPolicy.DEFAULT,
    val loggingGranularity: LoggingGranularity = LoggingGranularity.DEFAULT,
    val syncConfigured: Boolean = false,
    val sessionCount: Int = 0,
    val readingCount: Int = 0,
    val dbSizeBytes: Long = 0,
    val cleanupRunning: Boolean = false,
    val pendingUploads: Int = 0,
    val syncing: Boolean = false,
    val fahrenheit: Boolean = true,
    val connectionTest: String? = null,
    val connectionTestOk: Boolean = false,
    val testing: Boolean = false,
    val selectedDeviceAddress: String? = null,
    val selectedDeviceLabel: String? = null,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = AppSettings(application)
    private val db = TelemetryDatabase.get(application)
    private val bluetoothAdapter: BluetoothAdapter? =
        (application.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            baseUrl = settings.baseUrl,
            apiKey = settings.apiKey,
            retentionPolicy = settings.retentionPolicy,
            loggingGranularity = settings.loggingGranularity,
            syncConfigured = settings.syncConfigured,
            selectedDeviceAddress = settings.lastDeviceAddress,
            fahrenheit = settings.temperatureInFahrenheit,
        ),
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refreshStorageStats()
        refreshSelectedDevice()
    }

    /** Bonded devices, for the adapter picker. Empty if the permission is missing. */
    @SuppressLint("MissingPermission")
    fun pairedDevices(): List<BluetoothDevice> =
        runCatching { bluetoothAdapter?.bondedDevices?.toList() }.getOrNull().orEmpty()

    @SuppressLint("MissingPermission")
    fun selectDevice(device: BluetoothDevice) {
        settings.lastDeviceAddress = device.address
        _uiState.value = _uiState.value.copy(
            selectedDeviceAddress = device.address,
            selectedDeviceLabel = runCatching { device.name }.getOrNull() ?: device.address,
        )
    }

    @SuppressLint("MissingPermission")
    private fun refreshSelectedDevice() {
        val address = settings.lastDeviceAddress ?: return
        val label = runCatching {
            bluetoothAdapter?.bondedDevices?.firstOrNull { it.address == address }?.name
        }.getOrNull()
        _uiState.value = _uiState.value.copy(
            selectedDeviceAddress = address,
            selectedDeviceLabel = label ?: address,
        )
    }

    fun updateBaseUrl(value: String) {
        _uiState.value = _uiState.value.copy(baseUrl = value)
    }

    fun updateApiKey(value: String) {
        _uiState.value = _uiState.value.copy(apiKey = value)
    }

    fun save() {
        settings.baseUrl = _uiState.value.baseUrl.trim()
        settings.apiKey = _uiState.value.apiKey.trim()
        _uiState.value = _uiState.value.copy(syncConfigured = settings.syncConfigured)
    }

    /**
     * Saves, then checks the endpoint answers and accepts the key. Worth having in the app:
     * this gets configured in a driveway, where there is no terminal to curl from.
     */
    fun testConnection() {
        save()
        val state = _uiState.value
        if (state.baseUrl.isBlank() || state.apiKey.isBlank()) {
            _uiState.value = state.copy(
                connectionTest = "Enter the URL and key first.",
                connectionTestOk = false,
            )
            return
        }
        _uiState.value = state.copy(testing = true, connectionTest = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { GarageApiClient(state.baseUrl.trim(), state.apiKey.trim()).checkConnection() }
            }
            _uiState.value = _uiState.value.copy(
                testing = false,
                connectionTestOk = result.isSuccess,
                connectionTest = result.fold(
                    onSuccess = { "Connected — server replied $it" },
                    onFailure = { "Failed: ${it.message ?: it::class.simpleName}" },
                ),
            )
        }
    }

    /** Display only — stored and uploaded readings stay in Celsius. */
    fun setFahrenheit(enabled: Boolean) {
        settings.temperatureInFahrenheit = enabled
        _uiState.value = _uiState.value.copy(fahrenheit = enabled)
    }

    /** Retention is applied on selection — no Save needed, so it cannot be silently lost. */
    fun selectRetentionPolicy(policy: RetentionPolicy) {
        settings.retentionPolicy = policy
        _uiState.value = _uiState.value.copy(retentionPolicy = policy)
    }

    /** Applied immediately — the logging service re-reads this each poll cycle. */
    fun selectLoggingGranularity(granularity: LoggingGranularity) {
        settings.loggingGranularity = granularity
        _uiState.value = _uiState.value.copy(loggingGranularity = granularity)
    }

    /**
     * Queues an upload immediately rather than waiting for a drive to end or the 15 minute
     * retry. The work has a network constraint, so with the API unreachable this queues
     * rather than failing — the pending count simply will not drop, which is the honest
     * signal.
     */
    fun syncNow() {
        _uiState.value = _uiState.value.copy(syncing = true)
        SyncScheduler.triggerNow(getApplication())
        viewModelScope.launch {
            delay(2_000)
            refreshStorageStats()
            _uiState.value = _uiState.value.copy(syncing = false)
        }
    }

    fun runCleanupNow() {
        _uiState.value = _uiState.value.copy(cleanupRunning = true)
        SyncScheduler.triggerRetentionNow(getApplication())
        viewModelScope.launch {
            // WorkManager runs out of process; give it a beat before re-reading counts.
            delay(1_500)
            refreshStorageStats()
            _uiState.value = _uiState.value.copy(cleanupRunning = false)
        }
    }

    fun refreshStorageStats() {
        viewModelScope.launch {
            val sessions = db.tripSessionDao().countAll()
            val readings = db.readingDao().countAll()
            val pending = db.readingDao().countAllUnuploaded()
            val size = withContext(Dispatchers.IO) {
                File(getApplication<Application>().getDatabasePath("garage-telemetry.db").path).length()
            }
            _uiState.value = _uiState.value.copy(
                sessionCount = sessions,
                readingCount = readings,
                pendingUploads = pending,
                dbSizeBytes = size,
            )
        }
    }
}

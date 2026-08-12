package com.garagepi.telemetry.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
    val syncConfigured: Boolean = false,
    val sessionCount: Int = 0,
    val readingCount: Int = 0,
    val dbSizeBytes: Long = 0,
    val cleanupRunning: Boolean = false,
    val connectionTest: String? = null,
    val connectionTestOk: Boolean = false,
    val testing: Boolean = false,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = AppSettings(application)
    private val db = TelemetryDatabase.get(application)

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            baseUrl = settings.baseUrl,
            apiKey = settings.apiKey,
            retentionPolicy = settings.retentionPolicy,
            syncConfigured = settings.syncConfigured,
        ),
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refreshStorageStats()
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

    /** Retention is applied on selection — no Save needed, so it cannot be silently lost. */
    fun selectRetentionPolicy(policy: RetentionPolicy) {
        settings.retentionPolicy = policy
        _uiState.value = _uiState.value.copy(retentionPolicy = policy)
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
            val size = withContext(Dispatchers.IO) {
                File(getApplication<Application>().getDatabasePath("garage-telemetry.db").path).length()
            }
            _uiState.value = _uiState.value.copy(
                sessionCount = sessions,
                readingCount = readings,
                dbSizeBytes = size,
            )
        }
    }
}

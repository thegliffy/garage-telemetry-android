package com.garagepi.telemetry.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.garagepi.telemetry.sync.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SettingsUiState(val baseUrl: String = "", val apiKey: String = "")

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = AppSettings(application)

    private val _uiState = MutableStateFlow(SettingsUiState(baseUrl = settings.baseUrl, apiKey = settings.apiKey))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun updateBaseUrl(value: String) {
        _uiState.value = _uiState.value.copy(baseUrl = value)
    }

    fun updateApiKey(value: String) {
        _uiState.value = _uiState.value.copy(apiKey = value)
    }

    fun save() {
        settings.baseUrl = _uiState.value.baseUrl.trim()
        settings.apiKey = _uiState.value.apiKey.trim()
    }
}

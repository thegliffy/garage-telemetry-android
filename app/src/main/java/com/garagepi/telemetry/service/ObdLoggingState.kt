package com.garagepi.telemetry.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data class Connected(val deviceName: String) : ConnectionState
    data class Error(val message: String) : ConnectionState
}

data class LoggingState(
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    /** Latest value per shared reading id (see TelemetryFields). */
    val latestValues: Map<String, Double> = emptyMap(),
    /**
     * Newest raw payload for each not-yet-decoded PID, keyed by request hex. The
     * calibration screen scans these, which is what lets calibration happen in the car
     * instead of requiring the phone tethered to a desktop.
     */
    val calibrationFrames: Map<String, ByteArray> = emptyMap(),
)

/**
 * Process-wide logging state, owned by [ObdLoggingService] and observed by the UI.
 *
 * The service outlives any ViewModel — that is the whole point of moving logging out
 * of `viewModelScope` — so the state has to live somewhere both can reach. A singleton
 * is enough here and avoids binder/ServiceConnection plumbing for a single-activity app.
 */
object ObdLoggingState {
    private val _state = MutableStateFlow(LoggingState())
    val state: StateFlow<LoggingState> = _state.asStateFlow()

    fun update(transform: (LoggingState) -> LoggingState) = _state.update(transform)

    fun setConnection(connection: ConnectionState) = _state.update { it.copy(connectionState = connection) }

    fun mergeValues(values: Map<String, Double>) =
        _state.update { it.copy(latestValues = it.latestValues + values) }

    fun mergeCalibrationFrames(frames: Map<String, ByteArray>) {
        if (frames.isEmpty()) return
        _state.update { it.copy(calibrationFrames = it.calibrationFrames + frames) }
    }

    fun clearValues() = _state.update { it.copy(latestValues = emptyMap()) }
}

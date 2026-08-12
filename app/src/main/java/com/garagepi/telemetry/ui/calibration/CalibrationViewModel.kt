package com.garagepi.telemetry.ui.calibration

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.garagepi.telemetry.obd.CalibrationScan
import com.garagepi.telemetry.obd.CandidateSpec
import com.garagepi.telemetry.service.ObdLoggingState
import com.garagepi.telemetry.sync.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Which field is being calibrated; each has its own source frame and search shape. */
enum class CalibrationTarget(
    val requestHex: String,
    val title: String,
    val prompt: String,
    val tolerance: Double,
    val widths: List<Int>,
) {
    ODOMETER(
        requestHex = "22B002",
        title = "Odometer",
        prompt = "Type the odometer exactly as the dash shows it, then take a sample. " +
            "Drive a few miles and take a second sample to rule out coincidences.",
        tolerance = 1.0,
        widths = CalibrationScan.ODOMETER_WIDTHS,
    ),
    SPEED(
        requestHex = "22E004",
        title = "Speed",
        prompt = "Hold a steady speed, type what the dash reads, and take a sample. " +
            "Take a second sample at a clearly different speed.",
        tolerance = 2.0,
        widths = CalibrationScan.SPEED_WIDTHS,
    ),
}

data class CalibrationUiState(
    val target: CalibrationTarget = CalibrationTarget.ODOMETER,
    val enteredValue: String = "",
    val candidates: List<CandidateSpec> = emptyList(),
    val samplesTaken: Int = 0,
    val message: String? = null,
    val savedSpec: CandidateSpec? = null,
    /** Live preview of what each candidate currently reads, so a wrong one is obvious. */
    val previews: Map<CandidateSpec, Double> = emptyMap(),
)

class CalibrationViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = AppSettings(application)

    private val _uiState = MutableStateFlow(
        CalibrationUiState(savedSpec = settings.odometerSpec),
    )
    val uiState: StateFlow<CalibrationUiState> = _uiState.asStateFlow()

    fun selectTarget(target: CalibrationTarget) {
        _uiState.value = CalibrationUiState(
            target = target,
            savedSpec = savedSpecFor(target),
        )
    }

    fun updateValue(value: String) {
        _uiState.value = _uiState.value.copy(enteredValue = value)
    }

    /**
     * Scan the newest frame from the car for fields matching the entered value. The first
     * sample proposes candidates; later samples keep only those that still fit, which is
     * what separates the real field from a byte that coincidentally held the same number.
     */
    fun takeSample() {
        val state = _uiState.value
        val target = state.enteredValue.trim().toDoubleOrNull()
        if (target == null) {
            _uiState.value = state.copy(message = "Enter the value shown on the dash first.")
            return
        }

        val frame = ObdLoggingState.state.value.calibrationFrames[state.target.requestHex]
        if (frame == null) {
            _uiState.value = state.copy(
                message = "No frame from the car yet. Connect on the Live tab and wait a " +
                    "few seconds — the car has to be awake.",
            )
            return
        }

        val candidates = if (state.samplesTaken == 0) {
            CalibrationScan.scan(
                pid = state.target.requestHex,
                data = frame,
                target = target,
                tolerance = state.target.tolerance,
                widths = state.target.widths,
            )
        } else {
            CalibrationScan.narrow(state.candidates, frame, target, state.target.tolerance)
        }

        val samples = state.samplesTaken + 1
        val message = when {
            candidates.isEmpty() && state.samplesTaken == 0 ->
                "No field in this frame matches ${format(target)}. Double-check the value."
            candidates.isEmpty() ->
                "No candidate survived. The earlier sample was probably a coincidence — " +
                    "start over and sample again."
            candidates.size == 1 -> "Found it. Save to start recording this value."
            else -> "${candidates.size} possible fields after $samples sample(s). " +
                "Take another at a different value to narrow it down."
        }

        _uiState.value = state.copy(
            candidates = candidates,
            samplesTaken = if (candidates.isEmpty()) 0 else samples,
            message = message,
            previews = candidates.associateWith { it.extract(frame) ?: 0.0 },
        )
    }

    fun save(spec: CandidateSpec) {
        when (_uiState.value.target) {
            CalibrationTarget.ODOMETER -> settings.odometerSpec = spec
            CalibrationTarget.SPEED -> settings.speedSpec = spec
        }
        _uiState.value = _uiState.value.copy(
            savedSpec = spec,
            message = "Saved. Reconnect on the Live tab for it to take effect.",
        )
    }

    fun clearSaved() {
        when (_uiState.value.target) {
            CalibrationTarget.ODOMETER -> settings.odometerSpec = null
            CalibrationTarget.SPEED -> settings.speedSpec = null
        }
        _uiState.value = _uiState.value.copy(savedSpec = null, message = "Calibration cleared.")
    }

    fun reset() {
        _uiState.value = _uiState.value.copy(
            candidates = emptyList(),
            samplesTaken = 0,
            previews = emptyMap(),
            message = null,
        )
    }

    private fun savedSpecFor(target: CalibrationTarget) = when (target) {
        CalibrationTarget.ODOMETER -> settings.odometerSpec
        CalibrationTarget.SPEED -> settings.speedSpec
    }

    private fun format(v: Double) = if (v == v.toLong().toDouble()) "${v.toLong()}" else "$v"
}

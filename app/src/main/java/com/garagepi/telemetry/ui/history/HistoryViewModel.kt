package com.garagepi.telemetry.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.garagepi.telemetry.data.ReadingEntity
import com.garagepi.telemetry.data.TelemetryDatabase
import com.garagepi.telemetry.data.TripSessionEntity
import com.garagepi.telemetry.obd.TelemetryFields
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val db = TelemetryDatabase.get(application)

    val trips: StateFlow<List<TripSessionEntity>> = db.tripSessionDao()
        .observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedTripId = MutableStateFlow<Long?>(null)
    val selectedTripId: StateFlow<Long?> = _selectedTripId.asStateFlow()

    /**
     * Only PIDs needed for [TripSummary] and [TelemetryFields.CHART_FIELDS] — not the
     * full Mode 22 dump for the drive.
     */
    private val historyPids: List<String> = (
        TelemetryFields.CHART_FIELDS.map { it.pid } + listOf(
            TelemetryFields.HV_SOC.pid,
            TelemetryFields.PACK_POWER.pid,
            TelemetryFields.ODOMETER.pid,
        )
        ).distinct()

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedTripReadings: StateFlow<List<ReadingEntity>> = _selectedTripId
        .flatMapLatest { tripId ->
            if (tripId == null) {
                flowOf(emptyList())
            } else {
                db.readingDao().observeForTripPids(tripId, historyPids)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val selectedTripSampleCount: StateFlow<Int> = _selectedTripId
        .flatMapLatest { tripId ->
            if (tripId == null) flowOf(0) else db.readingDao().observeCountForTrip(tripId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * Derived totals for the selected drive. Recomputed off the reading stream rather
     * than stored, so it stays correct if readings arrive late from a running session.
     */
    val selectedTripSummary: StateFlow<TripSummary?> =
        combine(_selectedTripId, trips, selectedTripReadings, selectedTripSampleCount) {
                tripId, allTrips, readings, sampleCount ->
            val trip = allTrips.firstOrNull { it.id == tripId } ?: return@combine null
            if (readings.isEmpty()) null else TripSummary.from(trip, readings, sampleCount)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectTrip(tripId: Long) {
        _selectedTripId.value = tripId
    }

    fun clearSelection() {
        _selectedTripId.value = null
    }
}

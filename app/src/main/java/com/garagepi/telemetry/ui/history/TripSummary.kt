package com.garagepi.telemetry.ui.history

import com.garagepi.telemetry.data.ReadingEntity
import com.garagepi.telemetry.data.TripSessionEntity
import com.garagepi.telemetry.obd.TelemetryFields


/**
 * Derived per-drive totals. Distance comes from the cluster odometer (miles), so it is
 * an exact delta rather than an integration of speed.
 */
data class TripSummary(
    val durationMs: Long,
    val socStart: Double?,
    val socEnd: Double?,
    val energyUsedKwh: Double,
    val energyRegenKwh: Double,
    val distanceMiles: Double?,
    val sampleCount: Int,
) {
    val socUsed: Double? = if (socStart != null && socEnd != null) socStart - socEnd else null

    /** Net energy out of the pack: consumption less anything regen put back. */
    val netEnergyKwh: Double = energyUsedKwh - energyRegenKwh

    /** Miles per kWh. Null when distance is unknown or nothing was actually consumed. */
    val efficiencyMilesPerKwh: Double? =
        distanceMiles?.let { miles -> if (netEnergyKwh > 0.05) miles / netEnergyKwh else null }

    companion object {
        fun from(trip: TripSessionEntity, readings: List<ReadingEntity>): TripSummary {
            val end = trip.endedAt ?: readings.maxOfOrNull { it.ts } ?: trip.startedAt

            val soc = readings.filter { it.pid == TelemetryFields.HV_SOC.pid }.sortedBy { it.ts }
            val power = readings.filter { it.pid == TelemetryFields.PACK_POWER.pid }.sortedBy { it.ts }

            // Trapezoidal integration of kW over time. Discharge (positive) and regen
            // (negative) are accumulated separately so the summary can show both rather
            // than a single net figure that hides how much was recovered.
            var used = 0.0
            var regen = 0.0
            for (i in 1 until power.size) {
                val dtHours = (power[i].ts - power[i - 1].ts) / 3_600_000.0
                // Guard against a gap left by the app being killed mid-drive: integrating
                // across it would invent energy that was never measured.
                if (dtHours <= 0 || dtHours > 0.25) continue
                val avgKw = (power[i].value + power[i - 1].value) / 2.0
                if (avgKw >= 0) used += avgKw * dtHours else regen += -avgKw * dtHours
            }

            val odometer = readings.filter { it.pid == TelemetryFields.ODOMETER.pid }.sortedBy { it.ts }
            val distance = if (odometer.size >= 2) {
                // Reject a negative delta: the odometer only counts up, so anything else
                // means a bad frame slipped through rather than a real distance.
                (odometer.last().value - odometer.first().value).takeIf { it >= 0 }
            } else {
                null
            }

            return TripSummary(
                durationMs = end - trip.startedAt,
                socStart = soc.firstOrNull()?.value,
                socEnd = soc.lastOrNull()?.value,
                energyUsedKwh = used,
                energyRegenKwh = regen,
                distanceMiles = distance,
                sampleCount = readings.size,
            )
        }
    }
}

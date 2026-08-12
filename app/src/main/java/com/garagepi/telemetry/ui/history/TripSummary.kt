package com.garagepi.telemetry.ui.history

import com.garagepi.telemetry.data.ReadingEntity
import com.garagepi.telemetry.data.TripSessionEntity
import com.garagepi.telemetry.obd.TelemetryFields

private const val KM_PER_MILE = 1.609344

/**
 * Derived per-drive totals. Distance and efficiency stay null until the cluster odometer
 * offset is calibrated — see IoniqUds.decodeClusterCalibration.
 */
data class TripSummary(
    val durationMs: Long,
    val socStart: Double?,
    val socEnd: Double?,
    val energyUsedKwh: Double,
    val energyRegenKwh: Double,
    val distanceKm: Double?,
    val sampleCount: Int,
) {
    val socUsed: Double? = if (socStart != null && socEnd != null) socStart - socEnd else null

    /** Net energy out of the pack: consumption less anything regen put back. */
    val netEnergyKwh: Double = energyUsedKwh - energyRegenKwh

    val distanceMiles: Double? = distanceKm?.div(KM_PER_MILE)

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

            val odometer = readings.filter { it.pid == ODOMETER_PID }.sortedBy { it.ts }
            val distance = if (odometer.size >= 2) {
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
                distanceKm = distance,
                sampleCount = readings.size,
            )
        }

        /** Populated once the cluster odometer decode is calibrated. */
        const val ODOMETER_PID = "ODOMETER_KM"
    }
}

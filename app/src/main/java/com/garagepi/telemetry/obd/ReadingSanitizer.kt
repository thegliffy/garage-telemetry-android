package com.garagepi.telemetry.obd

import kotlin.math.abs

/** Ioniq 5 is limited near 115 mph; 150 is already a generous garbage ceiling. */
internal const val MAX_SPEED_MPH = 150.0

/** ~150 mph in km/h. Cluster speed is a single byte, so 255 is a classic bad frame. */
internal const val MAX_SPEED_CLUSTER_KMH = 240.0

/**
 * Largest speed change accepted between consecutive samples.
 *
 * ~0.5–2 s between VMCU polls; even a launch does not jump 40 mph in one frame.
 * Padding / offset glitches do (65 → 437).
 */
internal const val MAX_SPEED_JUMP_MPH = 40.0

/** Skip the jump check after a stall so the next real reading can re-anchor. */
private const val JUMP_MAX_GAP_MS = 5_000L

/**
 * Drops physically impossible decoded values before they reach the dashboard, Room, or
 * efficiency. Applied after every decode path, including in-app calibration overrides
 * which bypass [IoniqUds.decodeVmcuSpeed]'s own ceiling.
 */
class ReadingSanitizer {

    private var lastSpeedMph: Double? = null
    private var lastSpeedTs: Long = 0L

    fun reset() {
        lastSpeedMph = null
        lastSpeedTs = 0L
    }

    fun filter(readings: List<PidReading>): List<PidReading> {
        val kept = ArrayList<PidReading>(readings.size)
        for (reading in readings) {
            if (accept(reading)) {
                kept.add(reading)
                if (reading.pid == TelemetryFields.SPEED.pid) {
                    lastSpeedMph = reading.value
                    lastSpeedTs = reading.timestampMs
                }
            }
        }
        return kept
    }

    fun accept(reading: PidReading): Boolean {
        val value = reading.value
        if (!value.isFinite()) return false

        val range = HARD_LIMITS[reading.pid]
        if (range != null && value !in range) return false

        if (reading.pid == TelemetryFields.SPEED.pid) {
            val previous = lastSpeedMph
            if (previous != null) {
                val dt = reading.timestampMs - lastSpeedTs
                if (dt in 1..JUMP_MAX_GAP_MS && abs(value - previous) > MAX_SPEED_JUMP_MPH) {
                    return false
                }
            }
        }
        return true
    }

    companion object {
        /**
         * Physical ceilings, not gauge cosmetics ([TelemetryFields.SPEED].max is 100 so
         * the arc looks right — a real 110 mph highway run must still be kept).
         */
        val HARD_LIMITS: Map<String, ClosedFloatingPointRange<Double>> = mapOf(
            "SPEED_VMCU" to 0.0..MAX_SPEED_MPH,
            "SPEED_CLUSTER_KMH" to 0.0..MAX_SPEED_CLUSTER_KMH,
            "HV_SOC" to 0.0..100.0,
            "HV_SOC_DISPLAY" to 0.0..100.0,
            "HV_SOH" to 0.0..100.0,
            "AUX_SOC" to 0.0..100.0,
            "PACK_VOLTAGE_V" to 250.0..900.0,
            "PACK_CURRENT_A" to -400.0..400.0,
            "PACK_POWER_KW" to -300.0..400.0,
            "CELL_V_MIN" to 1.5..4.6,
            "CELL_V_MAX" to 1.5..4.6,
            "BATT_TEMP_MIN_C" to -40.0..90.0,
            "BATT_TEMP_MAX_C" to -40.0..90.0,
            "AUX_VOLTAGE_V" to 5.0..16.0,
            "TIRE_FL_PSI" to 0.0..80.0,
            "TIRE_FR_PSI" to 0.0..80.0,
            "TIRE_RL_PSI" to 0.0..80.0,
            "TIRE_RR_PSI" to 0.0..80.0,
            "MOTOR_RPM_FRONT" to -12_000.0..12_000.0,
            "MOTOR_RPM_REAR" to -12_000.0..12_000.0,
        )
    }
}

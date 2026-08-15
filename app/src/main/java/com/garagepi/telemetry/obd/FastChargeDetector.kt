package com.garagepi.telemetry.obd

/**
 * Enters DC-fast-charge UI when the CCS plug bit is set, or pack power is drawing at
 * DC rates (below ~−15 kW; L2 AC tops out near 11 kW).
 *
 * Streaks avoid a single noisy frame flashing the full-screen charge view.
 */
class FastChargeDetector(
    private val enterStreak: Int = 2,
    private val exitStreak: Int = 6,
) {
    var active: Boolean = false
        private set

    private var onCount = 0
    private var offCount = 0

    fun reset() {
        active = false
        onCount = 0
        offCount = 0
    }

    fun update(values: Map<String, Double>): Boolean {
        if (evidence(values)) {
            onCount++
            offCount = 0
            if (!active && onCount >= enterStreak) active = true
        } else {
            offCount++
            onCount = 0
            if (active && offCount >= exitStreak) active = false
        }
        return active
    }

    companion object {
        /** kW into the pack at or above this is DCFC, not Level 2. Sign is discharge-positive. */
        const val DC_POWER_KW = -15.0

        fun evidence(values: Map<String, Double>): Boolean {
            val ccs = (values[TelemetryFields.CCS_PLUG.pid] ?: 0.0) >= 0.5
            val power = values[TelemetryFields.PACK_POWER.pid]
            val dcPower = power != null && power <= DC_POWER_KW
            return ccs || dcPower
        }

        /**
         * No heater-on bit in the Esprit1st list — only heater element temperature
         * ([TelemetryFields.HEATER_TEMP]). Running PTC sits well above pack max;
         * idle heater temp tracks the pack.
         */
        fun heaterOn(heaterTempC: Double?, packMaxC: Double?): Boolean {
            if (heaterTempC == null) return false
            if (packMaxC == null) return heaterTempC >= 30.0
            return heaterTempC >= packMaxC + 8.0
        }
    }
}

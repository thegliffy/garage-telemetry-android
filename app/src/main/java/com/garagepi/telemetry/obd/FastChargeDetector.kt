package com.garagepi.telemetry.obd

/**
 * True while the car is plugged in (CCS or AC). Used to start a Charge history
 * session and to treat this as a charging stop.
 *
 * Pack power is **not** evidence: Ioniq regen routinely exceeds −15 kW and must stay
 * on the Drive record. Streaks ignore a single noisy plug bit.
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
        fun evidence(values: Map<String, Double>): Boolean {
            val ccs = (values[TelemetryFields.CCS_PLUG.pid] ?: 0.0) >= 0.5
            val ac = (values[TelemetryFields.AC_PLUG.pid] ?: 0.0) >= 0.5
            return ccs || ac
        }

        fun dcPlug(values: Map<String, Double>): Boolean =
            (values[TelemetryFields.CCS_PLUG.pid] ?: 0.0) >= 0.5

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

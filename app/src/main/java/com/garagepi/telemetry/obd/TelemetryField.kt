package com.garagepi.telemetry.obd

/**
 * One dashboard-displayable value. `pid` is the shared identifier used in
 * Room, the sync API's `readings.pid`, and garagepi's `pid_map.py` — for the
 * one Mode 01 PID we still use it's the standard hex code ("010D"); for the
 * Ioniq Mode 22 EV fields it's the same field name garagepi's
 * ioniq_mode22.py already emits ("HV_SOC_DISPLAY", "PACK_VOLTAGE_V", ...),
 * reused as-is rather than inventing a second convention for one car.
 */
/**
 * @param signedFlow true for values whose sign means direction of energy flow. These are
 *   shown as a magnitude and coloured instead — red for positive (discharge), green for
 *   negative (charge/regen) — so a driver reads the number, not a minus sign.
 */
data class TelemetryField(
    val pid: String,
    val label: String,
    val unit: String,
    val signedFlow: Boolean = false,
)

object TelemetryFields {
    val SPEED = TelemetryField("010D", "Speed", "km/h")

    // Headline SOC comes from 220101 (letter e/2), NOT 220105's display SOC: both this app
    // and garagepi emit HV_SOC, it read 55.0% against a dash showing 54%, and it avoids the
    // unresolved byte-offset question in 220105 (see IoniqUds.decode220105).
    val HV_SOC = TelemetryField("HV_SOC", "HV Battery", "%")
    val PACK_VOLTAGE = TelemetryField("PACK_VOLTAGE_V", "Pack Voltage", "V")
    val PACK_POWER = TelemetryField("PACK_POWER_KW", "Pack Power", "kW", signedFlow = true)
    val PACK_CURRENT = TelemetryField("PACK_CURRENT_A", "Pack Current", "A", signedFlow = true)
    val BATT_TEMP = TelemetryField("BATT_TEMP_MAX_C", "Battery Temp", "°C")
    val AUX_SOC = TelemetryField("AUX_SOC", "12V Aux Battery", "%")

    /** Populated only once calibrated in-app; unit is whatever the dash shows. */
    val ODOMETER = TelemetryField("ODOMETER", "Odometer", "")
    val BATT_TEMP_MIN = TelemetryField("BATT_TEMP_MIN_C", "Battery Temp (min)", "°C")
    val AUX_VOLTAGE = TelemetryField("AUX_VOLTAGE_V", "12V Aux Voltage", "V")
    val HV_SOH = TelemetryField("HV_SOH", "Battery Health", "%")

    // SPEED is intentionally absent until the VMCU offset is calibrated — the car does not
    // answer 010D, so including it would only render a permanently blank card.
    val DASHBOARD_FIELDS: List<TelemetryField> =
        listOf(HV_SOC, PACK_POWER, PACK_CURRENT, PACK_VOLTAGE, BATT_TEMP, AUX_SOC)

    /** Everything worth graphing over a drive — a superset of the dashboard tiles. */
    val CHART_FIELDS: List<TelemetryField> = listOf(
        HV_SOC,
        PACK_POWER,
        PACK_CURRENT,
        PACK_VOLTAGE,
        BATT_TEMP,
        BATT_TEMP_MIN,
        AUX_SOC,
        AUX_VOLTAGE,
        HV_SOH,
    )

    private val byPid = CHART_FIELDS.associateBy { it.pid }

    fun labelFor(pid: String): String = byPid[pid]?.label ?: pid
    fun unitFor(pid: String): String = byPid[pid]?.unit ?: ""
}

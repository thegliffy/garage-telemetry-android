package com.garagepi.telemetry.obd

/**
 * One dashboard-displayable value. `pid` is the shared identifier used in
 * Room, the sync API's `readings.pid`, and garagepi's `pid_map.py` — for the
 * one Mode 01 PID we still use it's the standard hex code ("010D"); for the
 * Ioniq Mode 22 EV fields it's the same field name garagepi's
 * ioniq_mode22.py already emits ("HV_SOC_DISPLAY", "PACK_VOLTAGE_V", ...),
 * reused as-is rather than inventing a second convention for one car.
 */
data class TelemetryField(val pid: String, val label: String, val unit: String)

object TelemetryFields {
    val SPEED = TelemetryField("010D", "Speed", "km/h")

    // Headline SOC comes from 220101 (letter e/2), NOT 220105's display SOC: both this app
    // and garagepi emit HV_SOC, it read 55.0% against a dash showing 54%, and it avoids the
    // unresolved byte-offset question in 220105 (see IoniqUds.decode220105).
    val HV_SOC = TelemetryField("HV_SOC", "HV Battery", "%")
    val PACK_VOLTAGE = TelemetryField("PACK_VOLTAGE_V", "Pack Voltage", "V")
    val PACK_POWER = TelemetryField("PACK_POWER_KW", "Pack Power", "kW")
    val BATT_TEMP = TelemetryField("BATT_TEMP_MAX_C", "Battery Temp", "°C")
    val AUX_SOC = TelemetryField("AUX_SOC", "12V Aux Battery", "%")

    val DASHBOARD_FIELDS: List<TelemetryField> = listOf(SPEED, HV_SOC, PACK_VOLTAGE, PACK_POWER, BATT_TEMP, AUX_SOC)

    private val byPid = DASHBOARD_FIELDS.associateBy { it.pid }

    fun labelFor(pid: String): String = byPid[pid]?.label ?: pid
    fun unitFor(pid: String): String = byPid[pid]?.unit ?: ""
}

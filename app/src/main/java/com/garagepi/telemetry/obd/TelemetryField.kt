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
 * @param min plausible low end of the signal, for gauges. Ranges come from the Esprit1st
 *   Ioniq 5 Torque CSV except where its range is impractical to draw.
 * @param max plausible high end. Leaving both at 0 means "no range", and the field can
 *   only be shown as a number.
 * @param decimals digits after the point. A property of the signal, not of how large the
 *   number happens to be: a tenth of a mph is noise, a hundredth of a cell volt is not.
 * @param isBoolean true for bit flags, rendered Yes/No rather than 1.0/0.0.
 */
data class TelemetryField(
    val pid: String,
    val label: String,
    val unit: String,
    val signedFlow: Boolean = false,
    val min: Double = 0.0,
    val max: Double = 0.0,
    val decimals: Int = 1,
    val isBoolean: Boolean = false,
) {
    val hasRange: Boolean get() = max > min
    val isTemperature: Boolean get() = unit == "°C"
}

object TelemetryFields {
    /**
     * From the VMCU (22E004), not the standard `010D` — this car answers NO DATA for that.
     * Deliberately its own id: the unit follows whatever the dash showed during
     * calibration, and publishing mph into the shared `010D` km/h series would corrupt
     * data garagepi also writes. Remap once the native unit is confirmed on the road.
     */
    val SPEED = TelemetryField("SPEED_VMCU", "Speed", "mph", min = 0.0, max = 100.0, decimals = 0)

    // Headline SOC comes from 220101 (letter e/2), NOT 220105's display SOC: both this app
    // and garagepi emit HV_SOC, it read 55.0% against a dash showing 54%, and it avoids the
    // unresolved byte-offset question in 220105 (see IoniqUds.decode220105).
    val HV_SOC = TelemetryField("HV_SOC", "HV Battery", "%", min = 0.0, max = 100.0)
    val PACK_VOLTAGE = TelemetryField("PACK_VOLTAGE_V", "Pack Voltage", "V", min = 400.0, max = 840.0)

    // Asymmetric on purpose: regen tops out far lower than drive power, so a symmetric
    // scale would waste most of the arc. Zero therefore sits at 40%, not the middle.
    val PACK_POWER = TelemetryField(
        "PACK_POWER_KW", "Pack Power", "kW", signedFlow = true, min = -180.0, max = 270.0,
    )
    val PACK_CURRENT = TelemetryField(
        "PACK_CURRENT_A", "Pack Current", "A", signedFlow = true, min = -200.0, max = 200.0,
    )
    val BATT_TEMP = TelemetryField(
        "BATT_TEMP_MAX_C", "Battery Temp", "°C", min = -40.0, max = 80.0, decimals = 0,
    )
    val AUX_SOC = TelemetryField("AUX_SOC", "12V Aux Battery", "%", min = 0.0, max = 100.0)

    /** Cluster odometer in miles; drives trip distance as an end-minus-start delta. */
    val ODOMETER = TelemetryField("ODOMETER", "Odometer", "mi", decimals = 0)
    val BATT_TEMP_MIN = TelemetryField(
        "BATT_TEMP_MIN_C", "Battery Temp (min)", "°C", min = -40.0, max = 80.0, decimals = 0,
    )
    val AUX_VOLTAGE = TelemetryField("AUX_VOLTAGE_V", "12V Aux Voltage", "V", min = 6.0, max = 15.0)
    val HV_SOH = TelemetryField("HV_SOH", "Battery Health", "%", min = 0.0, max = 100.0)

    /**
     * Derived, not read from the car — see [EfficiencyTracker]. Distance is integrated
     * from speed because the odometer's 1-mile resolution is useless over 10 seconds.
     */
    val EFF_SESSION = TelemetryField(
        "EFF_SESSION", "Efficiency (trip)", "mi/kWh", min = 0.0, max = 8.0, decimals = 2,
    )
    val EFF_NOW = TelemetryField(
        "EFF_10S", "Efficiency (now)", "mi/kWh", min = 0.0, max = 8.0, decimals = 2,
    )

    // Fields below come from the Esprit1st Ioniq 5 Torque Pro PID list, decoded out of
    // frames already being polled unless noted. See IoniqUds for the offsets.
    val HV_SOC_DISPLAY = TelemetryField(
        "HV_SOC_DISPLAY", "HV Battery (display)", "%", min = 0.0, max = 100.0,
    )
    val REMAINING_ENERGY = TelemetryField(
        "REMAINING_ENERGY_KWH", "Energy Remaining", "kWh", min = 0.0, max = 80.0,
    )
    val MAX_POWER = TelemetryField("MAX_POWER_KW", "Power Limit", "kW", min = 0.0, max = 300.0)
    val MAX_REGEN = TelemetryField("MAX_REGEN_KW", "Regen Limit", "kW", min = 0.0, max = 300.0)
    val CELL_V_MAX = TelemetryField(
        "CELL_V_MAX", "Cell Volt (max)", "V", min = 2.7, max = 4.3, decimals = 2,
    )
    val CELL_V_MIN = TelemetryField(
        "CELL_V_MIN", "Cell Volt (min)", "V", min = 2.7, max = 4.3, decimals = 2,
    )
    val HEATER_TEMP = TelemetryField(
        "HEATER_TEMP_C", "Batt Heater", "°C", min = -40.0, max = 80.0, decimals = 0,
    )

    /** Anchor for the two-pane motor tile; the renderer looks up the rear itself. */
    val MOTOR_RPM_FRONT = TelemetryField(
        "MOTOR_RPM_FRONT", "Motor RPM", "rpm", min = -10100.0, max = 10100.0, decimals = 0,
    )
    val MOTOR_RPM_REAR = TelemetryField(
        "MOTOR_RPM_REAR", "Motor RPM (rear)", "rpm", min = -10100.0, max = 10100.0, decimals = 0,
    )
    val ISOLATION = TelemetryField(
        "ISOLATION_KOHM", "Isolation", "kΩ", min = 0.0, max = 2000.0, decimals = 0,
    )
    val CEC = TelemetryField("CEC_KWH", "Lifetime Charged", "kWh", decimals = 0)
    val CED = TelemetryField("CED_KWH", "Lifetime Used", "kWh", decimals = 0)
    val OPTIME = TelemetryField("OPTIME_H", "Operating Time", "h", decimals = 0)
    val OUTDOOR_TEMP = TelemetryField(
        "OUTDOOR_TEMP_C", "Outside Temp", "°C", min = -40.0, max = 60.0, decimals = 0,
    )
    val INDOOR_TEMP = TelemetryField(
        "INDOOR_TEMP_C", "Cabin Temp", "°C", min = -40.0, max = 60.0, decimals = 0,
    )

    // Tire pressure uses 0-60 psi, not the CSV's 0-120: a car tire never sees 120, and the
    // wider scale would squash the band that matters into a quarter of the arc. Whole psi
    // only — the sensor reports in half-psi steps, so a decimal is false precision.
    // TIRE_FL and TIRE_FL_TEMP anchor the two four-corner tiles.
    val TIRE_FL = TelemetryField("TIRE_FL_PSI", "Tires", "psi", min = 0.0, max = 60.0, decimals = 0)
    val TIRE_FR = TelemetryField("TIRE_FR_PSI", "Tire FR", "psi", min = 0.0, max = 60.0, decimals = 0)
    val TIRE_RL = TelemetryField("TIRE_RL_PSI", "Tire RL", "psi", min = 0.0, max = 60.0, decimals = 0)
    val TIRE_RR = TelemetryField("TIRE_RR_PSI", "Tire RR", "psi", min = 0.0, max = 60.0, decimals = 0)
    val TIRE_FL_TEMP = TelemetryField(
        "TIRE_FL_C", "Tire Temps", "°C", min = -40.0, max = 80.0, decimals = 0,
    )
    val TIRE_FR_TEMP = TelemetryField(
        "TIRE_FR_C", "Tire FR Temp", "°C", min = -40.0, max = 80.0, decimals = 0,
    )
    val TIRE_RL_TEMP = TelemetryField(
        "TIRE_RL_C", "Tire RL Temp", "°C", min = -40.0, max = 80.0, decimals = 0,
    )
    val TIRE_RR_TEMP = TelemetryField(
        "TIRE_RR_C", "Tire RR Temp", "°C", min = -40.0, max = 80.0, decimals = 0,
    )
    val HV_CHARGING = TelemetryField("HV_CHARGING", "HV Charging", "", isBoolean = true)
    val AC_PLUG = TelemetryField("AC_PLUG", "AC Plug", "", isBoolean = true)
    val CCS_PLUG = TelemetryField("CCS_PLUG", "CCS Plug", "", isBoolean = true)

    /** Cluster's own speed in km/h — an independent check on the calibrated VMCU speed. */
    val SPEED_CLUSTER = TelemetryField("SPEED_CLUSTER_KMH", "Speed (cluster)", "km/h")

    val DASHBOARD_FIELDS: List<TelemetryField> =
        listOf(SPEED, HV_SOC, PACK_POWER, PACK_CURRENT, PACK_VOLTAGE, BATT_TEMP, AUX_VOLTAGE)

    /** The four that fit an Android Auto PaneTemplate, most useful while driving first. */
    val CAR_FIELDS: List<TelemetryField> = listOf(SPEED, HV_SOC, PACK_POWER, BATT_TEMP)

    /**
     * Fields offerable as a dashboard tile — everything that actually produces readings.
     *
     * `AUX_SOC` is deliberately absent: its offset was wrong and it is no longer decoded,
     * so offering it would give a tile that never fills. The individual tire corners and
     * the rear motor are absent too — they are still decoded, stored and charted, but
     * their anchors ([TIRE_FL], [TIRE_FL_TEMP], [MOTOR_RPM_FRONT]) show all of them in one
     * tile, and listing them separately would be eleven near-identical picker entries.
     */
    val SELECTABLE: List<TelemetryField> = listOf(
        // Most useful while driving, first — this order is also the default tile layout.
        SPEED,
        HV_SOC,
        PACK_POWER,
        EFF_NOW,
        EFF_SESSION,
        REMAINING_ENERGY,
        BATT_TEMP,
        OUTDOOR_TEMP,
        TIRE_FL,
        ODOMETER,
        // Everything else, still selectable.
        PACK_VOLTAGE,
        PACK_CURRENT,
        AUX_VOLTAGE,
        HV_SOC_DISPLAY,
        HV_SOH,
        HEATER_TEMP,
        INDOOR_TEMP,
        TIRE_FL_TEMP,
        MOTOR_RPM_FRONT,
        MAX_POWER,
        MAX_REGEN,
        CELL_V_MAX,
        CELL_V_MIN,
        ISOLATION,
        CEC,
        CED,
        OPTIME,
        HV_CHARGING,
        AC_PLUG,
        CCS_PLUG,
        SPEED_CLUSTER,
    )

    fun bySelectablePid(pid: String): TelemetryField? = SELECTABLE.firstOrNull { it.pid == pid }

    /** Everything worth graphing over a drive — a superset of the dashboard tiles. */
    val CHART_FIELDS: List<TelemetryField> = listOf(
        SPEED,
        EFF_NOW,
        HV_SOC,
        HV_SOC_DISPLAY,
        PACK_POWER,
        PACK_CURRENT,
        PACK_VOLTAGE,
        REMAINING_ENERGY,
        BATT_TEMP,
        BATT_TEMP_MIN,
        OUTDOOR_TEMP,
        AUX_VOLTAGE,
        MOTOR_RPM_REAR,
        MOTOR_RPM_FRONT,
        HV_SOH,
    )

    private val byPid = CHART_FIELDS.associateBy { it.pid }

    fun labelFor(pid: String): String = byPid[pid]?.label ?: pid
    fun unitFor(pid: String): String = byPid[pid]?.unit ?: ""
}

package com.garagepi.telemetry.sync

/**
 * Local Room / decoder names → Postgres `readings.pid`.
 *
 * Keep in sync with garagepi `pid_map.py`. The ingest API rejects pids longer than 16
 * characters; one oversize field in a batch 422s the whole upload. Names that garagepi
 * already shortens (PACK_V, REM_KWH, …) must use those codes so Grafana series line up.
 *
 * [SPEED_VMCU] is *not* mapped to `010D` — that series is km/h and garagepi may write it.
 */
object PidMap {
    const val MAX_PID_LENGTH = 16

    /**
     * Decoder / TelemetryField pid → API pid. Identity entries are listed so a missing
     * name is an obvious omission rather than a silent pass-through of a 19-char string.
     */
    val LOCAL_TO_API: Map<String, String> = mapOf(
        // garagepi pid_map.py
        "HV_SOC" to "HV_SOC",
        "HV_SOH" to "HV_SOH",
        "REMAINING_ENERGY_KWH" to "REM_KWH",
        "REMAINING_ENERGY_WH" to "REM_WH",
        "PACK_VOLTAGE_V" to "PACK_V",
        "PACK_CURRENT_A" to "PACK_A",
        "PACK_POWER_KW" to "PACK_KW",
        "AVAIL_CHARGE_KW" to "CHG_KW",
        "AVAIL_DISCHARGE_KW" to "DCHG_KW",
        "MAX_REGEN_KW" to "CHG_KW",
        "MAX_POWER_KW" to "DCHG_KW",
        "BATT_TEMP_MIN_C" to "BATT_TMIN",
        "BATT_TEMP_MAX_C" to "BATT_TMAX",
        "CELL_V_MIN" to "CELL_VMIN",
        "CELL_V_MAX" to "CELL_VMAX",
        "AUX_VOLTAGE_V" to "AUX_V",
        "AUX_SOC" to "AUX_SOC",
        "CEC_KWH" to "CEC_KWH",
        "CED_KWH" to "CED_KWH",
        "OPTIME_H" to "BATT_H",
        "BATT_WORK_TIME_H" to "BATT_H",
        "BATT_WORK_TIME_S" to "BATT_S",
        // Android-only (must stay off the shared 010D km/h series)
        "SPEED_VMCU" to "SPEED_VMCU",
        "SPEED_CLUSTER_KMH" to "SPD_CL_KMH",
        "ODOMETER" to "ODOMETER",
        "EFF_SESSION" to "EFF_SESSION",
        "EFF_10S" to "EFF_10S",
        "HV_SOC_DISPLAY" to "HV_SOC_DISP",
        "HEATER_TEMP_C" to "HEATER_TEMP_C",
        "MOTOR_RPM_FRONT" to "MOTOR_RPM_F",
        "MOTOR_RPM_REAR" to "MOTOR_RPM_R",
        "ISOLATION_KOHM" to "ISOLATION_KOHM",
        "INVERTER_CAP_V" to "INVERTER_CAP_V",
        "OUTDOOR_TEMP_C" to "OUTDOOR_TEMP_C",
        "INDOOR_TEMP_C" to "INDOOR_TEMP_C",
        "TIRE_FL_PSI" to "TIRE_FL_PSI",
        "TIRE_FR_PSI" to "TIRE_FR_PSI",
        "TIRE_RL_PSI" to "TIRE_RL_PSI",
        "TIRE_RR_PSI" to "TIRE_RR_PSI",
        "TIRE_FL_C" to "TIRE_FL_C",
        "TIRE_FR_C" to "TIRE_FR_C",
        "TIRE_RL_C" to "TIRE_RL_C",
        "TIRE_RR_C" to "TIRE_RR_C",
        "HV_CHARGING" to "HV_CHARGING",
        "AC_PLUG" to "AC_PLUG",
        "CCS_PLUG" to "CCS_PLUG",
    )

    /**
     * API pid to upload, or null to drop the reading (unknown oversize names must not
     * ride along in a batch and 422 the rest).
     */
    fun toApiPid(localPid: String): String? {
        LOCAL_TO_API[localPid]?.let { mapped ->
            return mapped.takeIf { it.length in 2..MAX_PID_LENGTH }
        }
        return localPid.takeIf { it.length in 2..MAX_PID_LENGTH }
    }
}

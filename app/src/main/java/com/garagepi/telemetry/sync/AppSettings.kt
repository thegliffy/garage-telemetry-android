package com.garagepi.telemetry.sync

import android.content.Context
import com.garagepi.telemetry.data.RetentionPolicy
import com.garagepi.telemetry.obd.CalibratedField
import com.garagepi.telemetry.obd.CandidateSpec
import com.garagepi.telemetry.obd.TelemetryFields

/** Small SharedPreferences wrapper — sync endpoint config and the last-used adapter. */
class AppSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("garage_telemetry_settings", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BASE_URL, value).apply()

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()

    var lastDeviceAddress: String?
        get() = prefs.getString(KEY_DEVICE_ADDRESS, null)
        set(value) = prefs.edit().putString(KEY_DEVICE_ADDRESS, value).apply()

    val syncConfigured: Boolean get() = baseUrl.isNotBlank() && apiKey.isNotBlank()

    var retentionPolicy: RetentionPolicy
        get() = RetentionPolicy.fromName(prefs.getString(KEY_RETENTION, null))
        set(value) = prefs.edit().putString(KEY_RETENTION, value.name).apply()

    /** Byte layout confirmed in the calibration screen, or null while uncalibrated. */
    var odometerSpec: CandidateSpec?
        get() = CandidateSpec.parse(prefs.getString(KEY_ODOMETER_SPEC, null))
        set(value) = prefs.edit().putString(KEY_ODOMETER_SPEC, value?.serialize()).apply()

    var speedSpec: CandidateSpec?
        get() = CandidateSpec.parse(prefs.getString(KEY_SPEED_SPEC, null))
        set(value) = prefs.edit().putString(KEY_SPEED_SPEC, value?.serialize()).apply()

    /** Unit the dash displays, so calibrated values keep the meaning you entered. */
    var imperialUnits: Boolean
        get() = prefs.getBoolean(KEY_IMPERIAL, true)
        set(value) = prefs.edit().putBoolean(KEY_IMPERIAL, value).apply()

    /** Calibrated fields keyed by request hex, for [com.garagepi.telemetry.obd.ObdSession]. */
    fun calibratedFields(): Map<String, CalibratedField> = buildMap {
        odometerSpec?.let { put(it.pid, CalibratedField(TelemetryFields.ODOMETER.pid, it)) }
        speedSpec?.let { put(it.pid, CalibratedField(TelemetryFields.SPEED.pid, it)) }
    }

    private companion object {
        const val KEY_BASE_URL = "base_url"
        const val KEY_API_KEY = "api_key"
        const val KEY_DEVICE_ADDRESS = "last_device_address"
        const val KEY_RETENTION = "retention_policy"
        const val KEY_ODOMETER_SPEC = "odometer_spec"
        const val KEY_SPEED_SPEC = "speed_spec"
        const val KEY_IMPERIAL = "imperial_units"
    }
}

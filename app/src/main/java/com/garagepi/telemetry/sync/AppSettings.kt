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

    /**
     * Which field each dashboard tile shows and how it is drawn, in order. Always
     * [TILE_COUNT] entries; a blank pid means an empty slot, so the grid need not be full.
     *
     * Stored as `pid:STYLE`. A bare `pid` — the format used before styles existed — parses
     * as "use the field's default style", so an existing layout survives the upgrade
     * instead of resetting.
     */
    var dashboardTiles: List<TileConfig>
        get() {
            val stored = prefs.getString(KEY_TILES, null)?.split(TILE_DELIMITER)
                ?: return defaultTiles()
            // Pad or trim rather than trusting stored length: the tile count may change
            // between versions, and a short list would otherwise crash the grid.
            return List(TILE_COUNT) { TileConfig.parse(stored.getOrElse(it) { "" }) }
        }
        set(value) {
            val normalized = List(TILE_COUNT) { value.getOrElse(it) { TileConfig("") } }
            prefs.edit()
                .putString(KEY_TILES, normalized.joinToString(TILE_DELIMITER) { it.serialize() })
                .apply()
        }

    private fun defaultTiles(): List<TileConfig> =
        List(TILE_COUNT) { TileConfig(TelemetryFields.SELECTABLE.getOrNull(it)?.pid ?: "") }

    /** Calibrated fields keyed by request hex, for [com.garagepi.telemetry.obd.ObdSession]. */
    fun calibratedFields(): Map<String, CalibratedField> = buildMap {
        odometerSpec?.let { put(it.pid, CalibratedField(TelemetryFields.ODOMETER.pid, it)) }
        speedSpec?.let { put(it.pid, CalibratedField(TelemetryFields.SPEED.pid, it)) }
    }

    companion object {
        /** Tiles on the main dashboard: 2x5 in portrait, 5x2 in landscape. */
        const val TILE_COUNT = 10
        private const val TILE_DELIMITER = ","

        const val KEY_BASE_URL = "base_url"
        const val KEY_API_KEY = "api_key"
        const val KEY_DEVICE_ADDRESS = "last_device_address"
        const val KEY_RETENTION = "retention_policy"
        const val KEY_TILES = "dashboard_tiles"
        const val KEY_ODOMETER_SPEC = "odometer_spec"
        const val KEY_SPEED_SPEC = "speed_spec"
        const val KEY_IMPERIAL = "imperial_units"
    }
}

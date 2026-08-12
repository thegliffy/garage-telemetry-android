package com.garagepi.telemetry.sync

import android.content.Context

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

    private companion object {
        const val KEY_BASE_URL = "base_url"
        const val KEY_API_KEY = "api_key"
        const val KEY_DEVICE_ADDRESS = "last_device_address"
    }
}

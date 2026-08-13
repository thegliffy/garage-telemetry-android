package com.garagepi.telemetry.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Runtime permissions needed before logging can start: `BLUETOOTH_CONNECT` on API 31+, and
 * `POST_NOTIFICATIONS` on API 33+ (the foreground service cannot show its required ongoing
 * notification without it, and the service will not start).
 *
 * Shared because the dashboard requests them and the settings adapter picker needs the same
 * check before it can list bonded devices.
 */
fun missingPermissions(context: Context): List<String> {
    val required = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Manifest.permission.BLUETOOTH_CONNECT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }
    return required.filter {
        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
    }
}

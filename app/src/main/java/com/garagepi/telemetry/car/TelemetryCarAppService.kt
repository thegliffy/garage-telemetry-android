package com.garagepi.telemetry.car

import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/**
 * Entry point for the Android Auto surface.
 *
 * This only *displays* telemetry — logging itself stays in [com.garagepi.telemetry
 * .service.ObdLoggingService], which runs whether or not the car is connected. The car
 * screen observes the same process-wide state the phone UI does.
 */
class TelemetryCarAppService : CarAppService() {

    /**
     * Debug builds accept any host so the Desktop Head Unit can connect; release builds
     * use the library's signed-host allowlist. Shipping ALLOW_ALL would let any app
     * pretending to be a car host drive this service.
     */
    override fun createHostValidator(): HostValidator =
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }

    override fun onCreateSession(): Session = object : Session() {
        override fun onCreateScreen(intent: Intent): Screen = TelemetryScreen(carContext)
    }
}

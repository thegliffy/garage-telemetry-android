package com.garagepi.telemetry

import android.app.Application
import com.garagepi.telemetry.sync.SyncScheduler

class GarageTelemetryApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SyncScheduler.schedulePeriodic(this)
    }
}

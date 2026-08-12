package com.garagepi.telemetry

import android.app.Application
import com.garagepi.telemetry.sync.SyncScheduler

class GarageTelemetryApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SyncScheduler.schedulePeriodic(this)
        SyncScheduler.scheduleRetention(this)
        // Close any session orphaned by the process being killed mid-drive, and apply
        // retention now rather than waiting up to a day for the periodic pass.
        SyncScheduler.triggerRetentionNow(this)
    }
}

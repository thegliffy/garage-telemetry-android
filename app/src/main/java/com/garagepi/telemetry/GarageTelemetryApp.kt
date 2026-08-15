package com.garagepi.telemetry

import android.app.Application
import com.garagepi.telemetry.sync.SyncScheduler

class GarageTelemetryApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SyncScheduler.schedulePeriodic(this)
        SyncScheduler.scheduleRetention(this)
        // Do not run RetentionWorker (and its VACUUM) on every cold start — that can
        // hitch UI/logging. Orphans are reaped by SyncWorker; Settings still has
        // "Run cleanup now". Daily periodic retention remains scheduled above.
    }
}

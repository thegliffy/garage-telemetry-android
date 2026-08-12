package com.garagepi.telemetry.data

import android.util.Log
import java.util.concurrent.TimeUnit

private const val TAG = "SessionReaper"

/** A session idle this long with no endedAt is assumed to have died with the process. */
private val STALE_AFTER_MS = TimeUnit.MINUTES.toMillis(10)

/**
 * Closes sessions that were never ended.
 *
 * Normally [com.garagepi.telemetry.service.ObdLoggingService] stamps `endedAt` when a
 * drive finishes. If Android kills the process mid-drive that never runs, and the
 * session stays open forever: `TripSessionDao.getPendingSync` keeps returning it,
 * `SyncWorker` never closes it upstream (that is gated on `endedAt != null`), and the
 * history screen shows it as still in progress.
 *
 * The session is dated from its **last reading** rather than from now, so an orphan
 * discovered days later is not recorded as a multi-day drive.
 */
object SessionReaper {

    suspend fun reap(db: TelemetryDatabase, now: Long = System.currentTimeMillis()): Int {
        val unfinished = db.tripSessionDao().getUnfinished()
        if (unfinished.isEmpty()) return 0

        var closed = 0
        for (trip in unfinished) {
            val lastReading = db.readingDao().maxTsForTrip(trip.id)
            // A session with no readings at all can only be dated from when it started.
            val lastActivity = lastReading ?: trip.startedAt
            if (now - lastActivity < STALE_AFTER_MS) continue

            db.tripSessionDao().update(trip.copy(endedAt = lastActivity))
            closed++
            Log.i(TAG, "closed orphaned session ${trip.id}, endedAt=$lastActivity")
        }
        return closed
    }
}

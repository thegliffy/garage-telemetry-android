package com.garagepi.telemetry.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.garagepi.telemetry.data.RetentionPolicy
import com.garagepi.telemetry.data.SessionReaper
import com.garagepi.telemetry.data.TelemetryDatabase

private const val TAG = "RetentionWorker"

/**
 * Applies the configured [RetentionPolicy]. Deleting a `trip_sessions` row also removes
 * its readings — `ReadingEntity` declares `ForeignKey(onDelete = CASCADE)` — so there is
 * no separate readings cleanup.
 */
class RetentionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = TelemetryDatabase.get(applicationContext)
        val policy = AppSettings(applicationContext).retentionPolicy

        // An open session is skipped by both delete queries (`endedAt IS NOT NULL`), so
        // reap first — otherwise an orphan from a killed process is never eligible.
        SessionReaper.reap(db)

        val deleted = when (policy) {
            RetentionPolicy.FOREVER -> 0
            RetentionPolicy.UNTIL_UPLOADED -> db.tripSessionDao().deleteFullyUploaded()
            RetentionPolicy.ONE_MONTH, RetentionPolicy.ONE_YEAR -> {
                val cutoff = policy.cutoffMillis() ?: return Result.success()
                db.tripSessionDao().deleteEndedBefore(cutoff)
            }
        }

        if (deleted > 0) {
            Log.i(TAG, "deleted $deleted session(s) under policy $policy")
            // Only VACUUM after a meaningful purge — a single-session delete still frees
            // pages, but exclusive VACUUM on tiny cleanups is not worth hitching logging.
            if (deleted >= VACUUM_AFTER_DELETED) {
                runCatching { db.openHelper.writableDatabase.execSQL("VACUUM") }
                    .onFailure { Log.w(TAG, "VACUUM failed: ${it.message}") }
            }
        }
        return Result.success()
    }

    companion object {
        /** Skip VACUUM for tiny deletions; daily/manual cleanup of several trips still shrinks. */
        const val VACUUM_AFTER_DELETED = 3
    }
}

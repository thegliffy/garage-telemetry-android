package com.garagepi.telemetry.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.garagepi.telemetry.data.ReadingEntity
import com.garagepi.telemetry.data.SessionReaper
import com.garagepi.telemetry.data.TelemetryDatabase
import com.garagepi.telemetry.data.TripSessionEntity
import java.time.Instant

private const val TAG = "SyncWorker"

/**
 * Uploads locally-recorded trips to garage-telemetry-api. Local Room data is
 * always the source of truth; this worker retries (via WorkManager backoff)
 * whenever the sync endpoint isn't reachable, e.g. away from home wifi.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = AppSettings(applicationContext)
        val db = TelemetryDatabase.get(applicationContext)

        // Independent of sync config: an orphaned session must get an endedAt or it is
        // returned by getPendingSync forever and never closed upstream.
        SessionReaper.reap(db)

        if (!settings.syncConfigured) {
            return Result.success()
        }

        val client = GarageApiClient(settings.baseUrl, settings.apiKey)

        var hadFailure = false
        for (trip in db.tripSessionDao().getPendingSync()) {
            try {
                syncTrip(trip, db, client)
            } catch (e: Exception) {
                // Include the throwable so Logcat keeps the HTTP body from
                // GarageApiClient ("HTTP 422 …: {…}") plus the stack.
                Log.w(TAG, "sync trip ${trip.id} failed: ${e.message}", e)
                hadFailure = true
            }
        }
        return if (hadFailure) Result.retry() else Result.success()
    }

    private suspend fun syncTrip(tripIn: TripSessionEntity, db: TelemetryDatabase, client: GarageApiClient) {
        var trip = tripIn

        if (trip.remoteSessionId == null) {
            // Another worker may have finished create+Room while we were queued.
            db.tripSessionDao().getById(trip.id)?.remoteSessionId?.let { existing ->
                trip = trip.copy(remoteSessionId = existing)
            }
        }
        if (trip.remoteSessionId == null) {
            val response = client.createSession(
                SessionCreateRequest(
                    source = "android",
                    kind = "trip",
                    meta = mapOf("local_trip_id" to trip.id.toString()),
                ),
            )
            // Persist before readings: a kill between HTTP 200 and this write used to
            // create a second Postgres session on retry. API also reuses local_trip_id.
            trip = trip.copy(remoteSessionId = response.id)
            db.tripSessionDao().update(trip)
        }
        val remoteSessionId = trip.remoteSessionId ?: return

        while (true) {
            val batch = db.readingDao().getUnuploaded(trip.id)
            if (batch.isEmpty()) break
            val payloads = batch.mapNotNull { it.toPayload() }
            val dropped = batch.size - payloads.size
            if (dropped > 0) {
                Log.w(TAG, "dropping $dropped reading(s) with unmapped/oversize pid in trip ${trip.id}")
            }
            // Always mark the local rows uploaded: skipped pids must not retry forever,
            // and an all-skipped batch must not POST an empty readings array (API min=1).
            if (payloads.isNotEmpty()) {
                client.uploadReadings(remoteSessionId, payloads)
            }
            db.readingDao().markUploaded(batch.map { it.id })
        }

        if (trip.endedAt != null && !trip.remoteClosed) {
            client.closeSession(remoteSessionId)
            db.tripSessionDao().update(trip.copy(remoteClosed = true))
        }
    }

    private fun ReadingEntity.toPayload(): ReadingPayload? {
        val apiPid = PidMap.toApiPid(pid) ?: return null
        return ReadingPayload(
            ts = Instant.ofEpochMilli(ts).toString(),
            pid = apiPid,
            value = value,
        )
    }
}

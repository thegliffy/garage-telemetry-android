package com.garagepi.telemetry.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.garagepi.telemetry.data.ReadingEntity
import com.garagepi.telemetry.data.SessionReaper
import com.garagepi.telemetry.data.TelemetryDatabase
import com.garagepi.telemetry.data.TripSessionEntity
import java.time.Instant

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
                hadFailure = true
            }
        }
        return if (hadFailure) Result.retry() else Result.success()
    }

    private suspend fun syncTrip(tripIn: TripSessionEntity, db: TelemetryDatabase, client: GarageApiClient) {
        var trip = tripIn

        if (trip.remoteSessionId == null) {
            val response = client.createSession(
                SessionCreateRequest(
                    source = "android",
                    kind = "trip",
                    meta = mapOf("local_trip_id" to trip.id.toString()),
                ),
            )
            trip = trip.copy(remoteSessionId = response.id)
            db.tripSessionDao().update(trip)
        }
        val remoteSessionId = trip.remoteSessionId ?: return

        while (true) {
            val batch = db.readingDao().getUnuploaded(trip.id)
            if (batch.isEmpty()) break
            client.uploadReadings(remoteSessionId, batch.map { it.toPayload() })
            db.readingDao().markUploaded(batch.map { it.id })
        }

        if (trip.endedAt != null && !trip.remoteClosed) {
            client.closeSession(remoteSessionId)
            db.tripSessionDao().update(trip.copy(remoteClosed = true))
        }
    }

    private fun ReadingEntity.toPayload() = ReadingPayload(
        ts = Instant.ofEpochMilli(ts).toString(),
        pid = pid,
        value = value,
    )
}

package com.garagepi.telemetry.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One drive, local-first. `remoteSessionId` is filled in once SyncWorker creates it upstream. */
@Entity(tableName = "trip_sessions")
data class TripSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long? = null,
    val remoteSessionId: String? = null,
    val remoteClosed: Boolean = false,
)

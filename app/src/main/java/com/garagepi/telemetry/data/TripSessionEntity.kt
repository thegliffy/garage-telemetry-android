package com.garagepi.telemetry.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** One drive or DC-charge session, local-first. `remoteSessionId` is filled in once SyncWorker creates it upstream. */
@Entity(tableName = "trip_sessions")
data class TripSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long? = null,
    val remoteSessionId: String? = null,
    val remoteClosed: Boolean = false,
    /** [DRIVE] or [CHARGE]. Existing rows migrate as [DRIVE]. */
    @ColumnInfo(defaultValue = "'drive'")
    val kind: String = DRIVE,
) {
    val isCharge: Boolean get() = kind == CHARGE

    companion object {
        const val DRIVE = "drive"
        const val CHARGE = "charge"
    }
}

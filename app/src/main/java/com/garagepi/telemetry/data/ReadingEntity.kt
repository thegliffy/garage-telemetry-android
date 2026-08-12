package com.garagepi.telemetry.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "readings",
    foreignKeys = [
        ForeignKey(
            entity = TripSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripSessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tripSessionId"), Index("tripSessionId", "uploaded")],
)
data class ReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripSessionId: Long,
    val ts: Long,
    val pid: String,
    val value: Double,
    val uploaded: Boolean = false,
)

package com.garagepi.telemetry.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TripSessionDao {
    @Insert
    suspend fun insert(trip: TripSessionEntity): Long

    @Update
    suspend fun update(trip: TripSessionEntity)

    @Query("SELECT * FROM trip_sessions WHERE id = :id")
    suspend fun getById(id: Long): TripSessionEntity?

    @Query("SELECT * FROM trip_sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<TripSessionEntity>>

    /** Trips still needing a remote session, an upload, or a remote close. */
    @Query(
        "SELECT * FROM trip_sessions WHERE remoteSessionId IS NULL " +
            "OR remoteClosed = 0 ORDER BY startedAt ASC",
    )
    suspend fun getPendingSync(): List<TripSessionEntity>

    /**
     * Sessions that were never closed — normally because the process died mid-drive.
     * The reaper stamps these with the timestamp of their last reading.
     */
    @Query("SELECT * FROM trip_sessions WHERE endedAt IS NULL")
    suspend fun getUnfinished(): List<TripSessionEntity>

    @Query("SELECT COUNT(*) FROM trip_sessions")
    suspend fun countAll(): Int

    /**
     * Age-based retention. Deliberately ignores upload state (see [RetentionPolicy]).
     * `endedAt IS NOT NULL` keeps an in-progress drive safe. Readings go with it via
     * the CASCADE foreign key on ReadingEntity.
     */
    @Query("DELETE FROM trip_sessions WHERE endedAt IS NOT NULL AND endedAt < :cutoff")
    suspend fun deleteEndedBefore(cutoff: Long): Int

    /** Retention for UNTIL_UPLOADED: drop sessions fully landed on the server. */
    @Query(
        "DELETE FROM trip_sessions WHERE endedAt IS NOT NULL AND remoteClosed = 1 " +
            "AND id NOT IN (SELECT DISTINCT tripSessionId FROM readings WHERE uploaded = 0)",
    )
    suspend fun deleteFullyUploaded(): Int
}

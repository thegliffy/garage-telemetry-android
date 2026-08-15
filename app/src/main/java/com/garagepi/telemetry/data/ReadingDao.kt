package com.garagepi.telemetry.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingDao {
    @Insert
    suspend fun insertAll(readings: List<ReadingEntity>)

    @Query("SELECT * FROM readings WHERE tripSessionId = :tripId ORDER BY ts ASC")
    fun observeForTrip(tripId: Long): Flow<List<ReadingEntity>>

    /**
     * History UI only needs chart + summary PIDs — loading every Mode 22 sample for a
     * long drive (~2 Hz × dozens of pids) is too large for the main-thread Flow.
     */
    @Query(
        "SELECT * FROM readings WHERE tripSessionId = :tripId AND pid IN (:pids) " +
            "ORDER BY ts ASC",
    )
    fun observeForTripPids(tripId: Long, pids: List<String>): Flow<List<ReadingEntity>>

    @Query("SELECT COUNT(*) FROM readings WHERE tripSessionId = :tripId")
    fun observeCountForTrip(tripId: Long): Flow<Int>

    @Query(
        "SELECT * FROM readings WHERE tripSessionId = :tripId AND uploaded = 0 " +
            "ORDER BY ts ASC LIMIT :limit",
    )
    suspend fun getUnuploaded(tripId: Long, limit: Int = 500): List<ReadingEntity>

    @Query("UPDATE readings SET uploaded = 1 WHERE id IN (:ids)")
    suspend fun markUploaded(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM readings WHERE tripSessionId = :tripId AND uploaded = 0")
    suspend fun countUnuploaded(tripId: Long): Int

    /**
     * Timestamp of the newest reading in a session, or null if it has none.
     * Used by the stale-session reaper to date an orphaned session from when it
     * actually stopped recording rather than from when the reaper happened to run.
     */
    @Query("SELECT MAX(ts) FROM readings WHERE tripSessionId = :tripId")
    suspend fun maxTsForTrip(tripId: Long): Long?

    @Query("SELECT COUNT(*) FROM readings")
    suspend fun countAll(): Int

    /** Across all sessions — what "Sync now" is about to send. */
    @Query("SELECT COUNT(*) FROM readings WHERE uploaded = 0")
    suspend fun countAllUnuploaded(): Int
}

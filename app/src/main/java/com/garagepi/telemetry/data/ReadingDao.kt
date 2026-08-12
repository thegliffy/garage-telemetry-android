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

    @Query(
        "SELECT * FROM readings WHERE tripSessionId = :tripId AND uploaded = 0 " +
            "ORDER BY ts ASC LIMIT :limit",
    )
    suspend fun getUnuploaded(tripId: Long, limit: Int = 500): List<ReadingEntity>

    @Query("UPDATE readings SET uploaded = 1 WHERE id IN (:ids)")
    suspend fun markUploaded(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM readings WHERE tripSessionId = :tripId AND uploaded = 0")
    suspend fun countUnuploaded(tripId: Long): Int
}

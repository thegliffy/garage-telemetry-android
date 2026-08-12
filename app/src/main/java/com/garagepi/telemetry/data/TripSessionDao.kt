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
}

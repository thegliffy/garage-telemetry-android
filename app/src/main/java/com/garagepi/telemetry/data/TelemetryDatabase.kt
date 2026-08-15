package com.garagepi.telemetry.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TripSessionEntity::class, ReadingEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class TelemetryDatabase : RoomDatabase() {
    abstract fun tripSessionDao(): TripSessionDao
    abstract fun readingDao(): ReadingDao

    companion object {
        @Volatile private var instance: TelemetryDatabase? = null

        fun get(context: Context): TelemetryDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TelemetryDatabase::class.java,
                    "garage-telemetry.db",
                ).build().also { instance = it }
            }
    }
}

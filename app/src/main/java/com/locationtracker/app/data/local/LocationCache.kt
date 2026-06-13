package com.locationtracker.app.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

// ─────────────────────────────────────────────────────────
// Location name cache (unchanged from version 1)
// ─────────────────────────────────────────────────────────

@Entity(tableName = "location_cache", primaryKeys = ["lat", "lng"])
data class LocationCacheEntity(
    val lat: Double,
    val lng: Double,
    val placeName: String
)

@Dao
interface LocationCacheDao {
    @Query("SELECT placeName FROM location_cache WHERE lat = :lat AND lng = :lng LIMIT 1")
    suspend fun getNameForCoordinates(lat: Double, lng: Double): String?

    @Query("INSERT OR REPLACE INTO location_cache (lat, lng, placeName) VALUES (:lat, :lng, :placeName)")
    suspend fun saveNameForCoordinates(lat: Double, lng: Double, placeName: String)
}

// ─────────────────────────────────────────────────────────
// Timeline entries — PLACE and TRAVEL
// ─────────────────────────────────────────────────────────

@Entity(tableName = "timeline_table")
data class TimelineEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // "PLACE" or "TRAVEL"
    val type: String,

    // PLACE fields
    val placeName: String = "",
    val placeAddress: String = "",
    val placeIcon: String = "\uD83D\uDCCD",

    // TRAVEL fields
    val activityType: String = "",
    val activityEmoji: String = "",
    val distanceMeters: Float = 0f,
    val durationMinutes: Int = 0,

    // Common
    val startTime: Long,
    val endTime: Long? = null,   // null = ongoing
    val startLat: Double = 0.0,
    val startLng: Double = 0.0,
    val endLat: Double = 0.0,
    val endLng: Double = 0.0,
    val date: String = ""        // "YYYY-MM-DD"
)

@Dao
interface TimelineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: TimelineEntry): Long

    @Update
    suspend fun updateEntry(entry: TimelineEntry)

    /** Reactive Flow query — emits whenever the table changes. */
    @Query("SELECT * FROM timeline_table WHERE date = :date ORDER BY startTime ASC")
    fun getEntriesForDate(date: String): Flow<List<TimelineEntry>>

    /** One-shot suspend query for use inside coroutines (TripTracker). */
    @Query("SELECT * FROM timeline_table WHERE date = :date ORDER BY startTime ASC")
    suspend fun getEntriesForDateOnce(date: String): List<TimelineEntry>

    @Query("SELECT * FROM timeline_table WHERE type = :type AND endTime IS NULL LIMIT 1")
    suspend fun getOngoingEntry(type: String): TimelineEntry?

    @Query("DELETE FROM timeline_table WHERE id = :id")
    suspend fun deleteById(id: Long)
}

// ─────────────────────────────────────────────────────────
// Database — version 2 adds timeline_table
// ─────────────────────────────────────────────────────────

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS timeline_table (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                type TEXT NOT NULL,
                placeName TEXT NOT NULL DEFAULT '',
                placeAddress TEXT NOT NULL DEFAULT '',
                placeIcon TEXT NOT NULL DEFAULT '📍',
                activityType TEXT NOT NULL DEFAULT '',
                activityEmoji TEXT NOT NULL DEFAULT '',
                distanceMeters REAL NOT NULL DEFAULT 0.0,
                durationMinutes INTEGER NOT NULL DEFAULT 0,
                startTime INTEGER NOT NULL,
                endTime INTEGER,
                startLat REAL NOT NULL DEFAULT 0.0,
                startLng REAL NOT NULL DEFAULT 0.0,
                endLat REAL NOT NULL DEFAULT 0.0,
                endLng REAL NOT NULL DEFAULT 0.0,
                date TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
    }
}

@Database(
    entities = [LocationCacheEntity::class, TimelineEntry::class],
    version = 2,
    exportSchema = false
)
abstract class LocationDatabase : RoomDatabase() {
    abstract fun locationCacheDao(): LocationCacheDao
    abstract fun timelineDao(): TimelineDao

    companion object {
        @Volatile private var INSTANCE: LocationDatabase? = null

        /** Process-wide singleton — safe to call from any thread / coroutine. */
        fun getInstance(context: Context): LocationDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LocationDatabase::class.java,
                    "location-db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

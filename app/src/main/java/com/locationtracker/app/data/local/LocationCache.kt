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
// Timeline entries — SIMPLE MODEL
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

    // TRAVEL fields
    val activityType: String = "",
    val activityEmoji: String = "",
    val activityLabel: String = "",
    val speedKmh: Float = 0f,
    val distanceMeters: Float = 0f,

    // Both types
    val startTime: Long,
    // null = still ongoing (place still open)
    val endTime: Long? = null,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val date: String = ""
)

@Dao
interface TimelineDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: TimelineEntry): Long

    @Update
    suspend fun update(entry: TimelineEntry)

    // Get last open entry (endTime is null)
    @Query("""
        SELECT * FROM timeline_table
        WHERE endTime IS NULL
        ORDER BY startTime DESC
        LIMIT 1
    """)
    suspend fun getOpenEntry(): TimelineEntry?

    // Get all entries for a date, newest first
    @Query("""
        SELECT * FROM timeline_table
        WHERE date = :date
        ORDER BY startTime ASC
    """)
    fun getEntriesForDate(date: String): Flow<List<TimelineEntry>>

    // Close any open entry right now
    @Query("""
        UPDATE timeline_table
        SET endTime = :endTime
        WHERE endTime IS NULL
    """)
    suspend fun closeOpenEntry(endTime: Long)

    // Wipe bad zero-distance travel entries
    @Query("""
        DELETE FROM timeline_table
        WHERE type = 'TRAVEL'
        AND distanceMeters < 50
    """)
    suspend fun deleteBadTravelEntries()
}

// ─────────────────────────────────────────────────────────
// Database
// ─────────────────────────────────────────────────────────

@Database(
    entities = [LocationCacheEntity::class, TimelineEntry::class],
    version = 5,
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
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

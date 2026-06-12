package com.locationtracker.app.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Query
import androidx.room.RoomDatabase

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

@Database(entities = [LocationCacheEntity::class], version = 1, exportSchema = false)
abstract class LocationDatabase : RoomDatabase() {
    abstract fun locationCacheDao(): LocationCacheDao
}

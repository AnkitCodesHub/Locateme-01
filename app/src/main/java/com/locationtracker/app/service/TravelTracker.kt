package com.locationtracker.app.service

import android.location.Location
import android.util.Log
import com.locationtracker.app.data.local.TimelineDao
import com.locationtracker.app.data.local.TimelineEntry

object TravelTracker {

    private var openTravelEntry: TimelineEntry? = null
    private var totalDistanceM: Float = 0f
    private var lastLocation: Location? = null

    suspend fun onMovingReading(
        location: Location,
        dao: TimelineDao
    ) {
        val speedKmh = location.speed * 3.6f

        // Calculate distance from last point
        val segmentDist = lastLocation?.let { prev ->
            val d = FloatArray(1)
            Location.distanceBetween(
                prev.latitude,
                prev.longitude,
                location.latitude,
                location.longitude,
                d
            )
            d[0]
        } ?: 0f

        totalDistanceM += segmentDist
        lastLocation = location

        val emoji = when {
            speedKmh < 7f  -> "🚶"
            speedKmh < 15f -> "🏃"
            speedKmh < 30f -> "🚴"
            else           -> "🚗"
        }
        val label = when {
            speedKmh < 7f  -> "Walking"
            speedKmh < 15f -> "Running"
            speedKmh < 30f -> "Cycling"
            else           -> "Driving"
        }
        val type = when {
            speedKmh < 7f  -> "WALKING"
            speedKmh < 15f -> "RUNNING"
            speedKmh < 30f -> "CYCLING"
            else           -> "DRIVING"
        }

        if (openTravelEntry == null) {
            // Start new travel entry
            val entry = TimelineEntry(
                type = "TRAVEL",
                activityType = type,
                activityEmoji = emoji,
                activityLabel = label,
                speedKmh = speedKmh,
                distanceMeters = totalDistanceM,
                startTime = System.currentTimeMillis(),
                endTime = null,
                latitude = location.latitude,
                longitude = location.longitude,
                date = PlaceDetector.todayKey()
            )
            val id = dao.insert(entry)
            openTravelEntry = entry.copy(id = id)
            Log.d("TravelTracker", "Travel started: $label")
        } else {
            // Update existing travel entry
            val updated = openTravelEntry!!.copy(
                speedKmh = speedKmh,
                distanceMeters = totalDistanceM,
                endTime = System.currentTimeMillis(),
                activityEmoji = emoji,
                activityLabel = label
            )
            dao.update(updated)
            openTravelEntry = updated
            Log.d("TravelTracker", "Travel updated: ${totalDistanceM}m @ ${speedKmh}km/h")
        }
    }

    suspend fun onUserStopped(dao: TimelineDao) {
        openTravelEntry?.let {
            dao.closeOpenEntry(System.currentTimeMillis())
            Log.d("TravelTracker", "Travel closed: ${totalDistanceM}m total")
        }
        openTravelEntry = null
        totalDistanceM = 0f
        lastLocation = null
    }
}

package com.locationtracker.app.service

import android.content.Context
import android.location.Location
import android.util.Log
import com.locationtracker.app.data.local.TimelineDao
import com.locationtracker.app.data.local.TimelineEntry
import com.locationtracker.app.utils.LocationUtils.getPlaceAddress
import com.locationtracker.app.utils.LocationUtils.getPlaceName
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PlaceDetector {

    // How long still before we call it a place
    private const val STILL_THRESHOLD_MS = 2 * 60 * 1000L // 2 minutes

    private var stillSince: Long = 0L
    private var openPlaceId: Long = -1L
    private var consecutiveStillCount = 0

    suspend fun onStillReading(
        context: Context,
        location: Location,
        dao: TimelineDao
    ) {
        consecutiveStillCount++
        
        if (stillSince == 0L) {
            stillSince = System.currentTimeMillis()
        }

        val stillDuration = System.currentTimeMillis() - stillSince

        // Need 2+ minutes of stillness
        // AND 2+ consecutive still readings
        if (stillDuration < STILL_THRESHOLD_MS || consecutiveStillCount < 2) {
            Log.d("PlaceDetector", "Still for ${stillDuration/1000}s waiting for threshold")
            return
        }

        // Already have an open place entry
        if (openPlaceId != -1L) {
            // Just update endTime to keep it live
            dao.closeOpenEntry(0L) // reset first
            return
        }

        // Close any open travel entry
        dao.closeOpenEntry(System.currentTimeMillis())

        // Reverse geocode
        val placeName = getPlaceName(context, location.latitude, location.longitude)
        val placeAddress = getPlaceAddress(context, location.latitude, location.longitude)

        // Create PLACE entry
        val entry = TimelineEntry(
            type = "PLACE",
            placeName = placeName,
            placeAddress = placeAddress,
            startTime = stillSince,
            endTime = null, // open = ongoing
            latitude = location.latitude,
            longitude = location.longitude,
            date = todayKey()
        )

        openPlaceId = dao.insert(entry)
        Log.d("PlaceDetector", "Place opened: $placeName")
    }

    suspend fun onMovementDetected(dao: TimelineDao) {
        // User started moving - close place
        if (openPlaceId != -1L) {
            dao.closeOpenEntry(System.currentTimeMillis())
            Log.d("PlaceDetector", "Place closed - user moving")
            openPlaceId = -1L
        }
        // Reset still tracking
        stillSince = 0L
        consecutiveStillCount = 0
    }

    fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
}

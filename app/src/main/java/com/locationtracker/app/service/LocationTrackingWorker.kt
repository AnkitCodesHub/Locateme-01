package com.locationtracker.app.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.locationtracker.app.data.model.TimelineActivity
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private const val TAG = "LocationTrackingWorker"

class LocationTrackingWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private val geocoder = Geocoder(context, Locale.getDefault())

    override suspend fun doWork(): Result {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Log.d(TAG, "No user signed in, skipping work")
            return Result.success()
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Location permissions not granted")
            return Result.failure()
        }

        return try {
            val location = fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
            if (location != null) {
                val locationName = getLocationName(location.latitude, location.longitude)
                updateTimeline(currentUser.uid, locationName)
            } else {
                Log.w(TAG, "Location returned null")
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error tracking location", e)
            Result.failure()
        }
    }

    @Suppress("DEPRECATION")
    private fun getLocationName(lat: Double, lng: Double): String {
        var actualLocationName = "Unknown Place"
        return try {
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                actualLocationName = address.featureName ?: address.thoroughfare ?: address.subLocality ?: "Unknown Place"
            }
            actualLocationName
        } catch (e: Exception) {
            Log.e(TAG, "Geocoder failed", e)
            actualLocationName
        }
    }

    private suspend fun updateTimeline(userId: String, locationName: String) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        
        val now = Date()
        val dateString = dateFormat.format(now)
        val timeString = timeFormat.format(now)
        val timestamp = now.time

        val timelineRef = Firebase.database.reference
            .child("history_timelines")
            .child(userId)
            .child(dateString)

        val snapshot = timelineRef.orderByChild("timestamp").limitToLast(1).get().await()

        if (snapshot.exists() && snapshot.children.iterator().hasNext()) {
            val lastEntrySnap = snapshot.children.iterator().next()
            val lastActivity = lastEntrySnap.getValue(TimelineActivity::class.java)

            if (lastActivity != null && lastActivity.locationName == locationName) {
                // Same location, update endTime
                lastEntrySnap.ref.child("endTime").setValue(timeString).await()
                Log.d(TAG, "Updated endTime for location: \$locationName")
                return
            }
        }

        // Different location or no entry exists, create new block
        val newActivityId = UUID.randomUUID().toString()
        val newActivity = TimelineActivity(
            id = newActivityId,
            locationName = locationName,
            startTime = timeString,
            endTime = timeString,
            timestamp = timestamp
        )

        timelineRef.child(newActivityId).setValue(newActivity).await()
        Log.d(TAG, "Created new activity block for location: \$locationName")
    }
}

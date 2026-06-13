package com.locationtracker.app.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import com.locationtracker.app.BuildConfig
import com.locationtracker.app.data.local.LocationDatabase
import androidx.room.Room
import android.os.Looper
import android.os.SystemClock
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

class LocationTimelineWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d("TimelineWorker", "Worker started at ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}")

        FirebaseAuth.getInstance().currentUser ?: run {
            Log.e("TimelineWorker", "No user logged in")
            return Result.failure()
        }

        val sensorActivityName = applicationContext
            .getSharedPreferences("activity_prefs", Context.MODE_PRIVATE)
            .getString("last_activity", null)
        val sensorActivity = sensorActivityName
            ?.let { runCatching { ActivityType.valueOf(it) }.getOrNull() }

        val latInput = inputData.getDouble("lat", 0.0)
        val lngInput = inputData.getDouble("lng", 0.0)
        val speedInput = inputData.getFloat("speed", 0f)

        // Periodic job + sensor says stationary: skip GPS entirely
        if (latInput == 0.0 && lngInput == 0.0 && sensorActivity == ActivityType.STATIONARY) {
            Log.d("TimelineWorker", "User is stationary per sensor - skipping GPS")
            return Result.success()
        }

        var speedMps = speedInput
        val location: android.location.Location

        if (latInput != 0.0 || lngInput != 0.0) {
            // Coords came from LocationTrackingService via inputData
            location = android.location.Location("input").also {
                it.latitude = latInput
                it.longitude = lngInput
                it.speed = speedInput
            }
        } else {
            // Periodic fallback: fetch GPS ourselves
            val fetched = try {
                withTimeout(12_000L) {
                    withContext(Dispatchers.IO) { getCurrentLocation(context) }
                }
            } catch (e: Exception) {
                Log.e("TimelineWorker", "Location fetch failed: ${e.message}")
                null
            } ?: run {
                Log.e("TimelineWorker", "Could not get location - retrying")
                return Result.retry()
            }
            location = fetched
            speedMps = if (fetched.hasSpeed()) fetched.speed else 0f
        }

        val speedActivity = ActivityDetector.detectFromSpeed(speedMps)
        val finalActivity = when {
            speedMps < 0.3f        -> ActivityType.STATIONARY
            sensorActivity != null -> sensorActivity
            else                   -> speedActivity
        }

        Log.d("TimelineWorker", "Feeding TripTracker: $finalActivity @ ${speedMps * 3.6f}km/h")
        TripTracker.onLocationUpdate(applicationContext, location, finalActivity)

        return Result.success()
    }
    
    private suspend fun updateTimelineEndTimeOnly(userId: String, currentDate: String, currentTime: String) {
        val ref = Firebase.database.reference.child("user_timelines").child(userId).child(currentDate)
        val snapshot = ref.get().await()
        val lastEntry = snapshot.children.maxByOrNull { child -> child.child("timestamp").getValue(Long::class.java) ?: 0L }
        lastEntry?.ref?.child("endTime")?.setValue(currentTime)?.await()
    }
    
    private suspend fun updateFriendsTimelineEndTimeOnly(userId: String, currentDate: String, currentTime: String) {
        try {
            val friendsSnapshot = Firebase.database.reference.child("friends").child(userId).get().await()
            friendsSnapshot.children.forEach { friendNode ->
                val friendId = friendNode.key ?: return@forEach
                val friendRef = Firebase.database.reference.child("friend_timelines").child(friendId).child(userId).child(currentDate)
                val friendSnapshot = friendRef.get().await()
                val lastEntry = friendSnapshot.children.maxByOrNull { child -> child.child("timestamp").getValue(Long::class.java) ?: 0L }
                lastEntry?.ref?.child("endTime")?.setValue(currentTime)?.await()
            }
        } catch (e: Exception) {
            Log.e("TimelineWorker", "Friends static update failed: ${e.message}")
        }
    }

    private suspend fun updateTimeline(
        userId: String,
        currentDate: String,
        currentTime: String,
        locationName: String,
        userName: String,
        activityType: String,
        activityEmoji: String,
        activityLabel: String,
        speedKmh: Float
    ) {
        val ref = Firebase.database.reference
            .child("user_timelines")
            .child(userId)
            .child(currentDate)
        
        val snapshot = ref.get().await()
        
        // Find the MOST RECENT entry
        val lastEntry = snapshot.children.maxByOrNull { child ->
            child.child("timestamp").getValue(Long::class.java) ?: 0L
        }
        
        val lastLocationName = lastEntry
            ?.child("locationName")
            ?.getValue(String::class.java)
            ?.trim()
        
        Log.d("TimelineWorker", "Last location: $lastLocationName")
        Log.d("TimelineWorker", "Current location: $locationName")
        
        if (lastLocationName != null && lastLocationName.equals(locationName.trim(), ignoreCase = true)) {
            // SAME LOCATION - only update endTime
            // DO NOT create new card
            lastEntry.ref.child("endTime").setValue(currentTime).await()
            Log.d("TimelineWorker", "Same location - updated endTime to $currentTime")
        } else {
            // NEW LOCATION - create new card
            val newEntry = hashMapOf(
                "locationName" to locationName.trim(),
                "startTime" to currentTime,
                "endTime" to currentTime,
                "timestamp" to System.currentTimeMillis(),
                "date" to currentDate,
                "userId" to userId,
                "userName" to userName,
                "activityType" to activityType,
                "activityEmoji" to activityEmoji,
                "activityLabel" to activityLabel,
                "speedKmh" to speedKmh
            )
            ref.push().setValue(newEntry).await()
            Log.d("TimelineWorker", "New location - created card: $locationName at $currentTime")
        }
    }
    
    private suspend fun updateFriendsTimeline(
        userId: String,
        userName: String,
        currentDate: String,
        currentTime: String,
        locationName: String,
        activityType: String,
        activityEmoji: String,
        activityLabel: String,
        speedKmh: Float
    ) {
        try {
            val friendsSnapshot = Firebase.database.reference
                .child("friends")
                .child(userId)
                .get().await()
            
            friendsSnapshot.children.forEach { friendNode ->
                val friendId = friendNode.key ?: return@forEach
                
                val friendRef = Firebase.database.reference
                    .child("friend_timelines")
                    .child(friendId)
                    .child(userId)
                    .child(currentDate)
                
                val friendSnapshot = friendRef.get().await()
                
                // Find most recent entry
                val lastEntry = friendSnapshot.children.maxByOrNull { child ->
                    child.child("timestamp").getValue(Long::class.java) ?: 0L
                }
                
                val lastLocation = lastEntry
                    ?.child("locationName")
                    ?.getValue(String::class.java)
                    ?.trim()
                
                if (lastLocation != null && lastLocation.equals(locationName.trim(), ignoreCase = true)) {
                    // Same location update endTime only
                    lastEntry.ref.child("endTime").setValue(currentTime).await()
                    Log.d("TimelineWorker", "Friend $friendId - updated endTime")
                } else {
                    // New location - new card
                    val entry = hashMapOf(
                        "locationName" to locationName.trim(),
                        "startTime" to currentTime,
                        "endTime" to currentTime,
                        "timestamp" to System.currentTimeMillis(),
                        "date" to currentDate,
                        "userId" to userId,
                        "userName" to userName,
                        "activityType" to activityType,
                        "activityEmoji" to activityEmoji,
                        "activityLabel" to activityLabel,
                        "speedKmh" to speedKmh
                    )
                    friendRef.push().setValue(entry).await()
                    Log.d("TimelineWorker", "Friend $friendId - new card: $locationName")
                }
            }
        } catch (e: Exception) {
            Log.e("TimelineWorker", "Friends update failed: ${e.message}")
        }
    }
    
    private suspend fun getExactPlaceName(
        lat: Double,
        lng: Double,
        apiKey: String
    ): String {
        return try {
            val url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=$lat,$lng&rankby=distance&key=$apiKey&language=en"
            
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            
            val response = withContext(Dispatchers.IO) {
                client.newCall(Request.Builder().url(url).build()).execute()
            }
            
            val body = response.body?.string() ?: ""
            val json = JSONObject(body)
            val status = json.optString("status", "")
            
            if (status == "OK") {
                val results = json.getJSONArray("results")
                if (results.length() > 0) {
                    val place = results.getJSONObject(0)
                    val name = place.optString("name", "")
                    val vicinity = place.optString("vicinity", "")
                    
                    val area = vicinity.split(",").firstOrNull()?.trim() ?: ""
                    
                    if (area.isNotEmpty() && area != name) {
                        "$name, $area"
                    } else {
                        name
                    }
                } else {
                    getFallbackPlaceName(lat, lng, apiKey)
                }
            } else {
                Log.e("PlacesAPI", "Status: $status")
                getFallbackPlaceName(lat, lng, apiKey)
            }
        } catch (e: Exception) {
            Log.e("PlacesAPI", "Error: ${e.message}")
            getFallbackPlaceName(lat, lng, apiKey)
        }
    }
    
    private suspend fun getFallbackPlaceName(
        lat: Double,
        lng: Double,
        apiKey: String
    ): String {
        return try {
            val url = "https://maps.googleapis.com/maps/api/geocode/json?latlng=$lat,$lng&key=$apiKey&language=en"
            
            val client = OkHttpClient()
            val response = withContext(Dispatchers.IO) {
                client.newCall(Request.Builder().url(url).build()).execute()
            }
            
            val body = response.body?.string() ?: ""
            val json = JSONObject(body)
            val results = json.optJSONArray("results")
            
            if (results != null && results.length() > 0) {
                val components = results.getJSONObject(0).getJSONArray("address_components")
                
                var poi = ""
                var premise = ""
                var route = ""
                var sublocality = ""
                var locality = ""
                
                for (i in 0 until components.length()) {
                    val comp = components.getJSONObject(i)
                    val types = comp.getJSONArray("types").toString()
                    val name = comp.getString("long_name")
                    
                    when {
                        types.contains("point_of_interest") || types.contains("establishment") -> poi = name
                        types.contains("premise") -> premise = name
                        types.contains("route") -> route = name
                        types.contains("sublocality_level_1") -> sublocality = name
                        types.contains("locality") -> locality = name
                    }
                }
                
                when {
                    poi.isNotEmpty() -> poi
                    premise.isNotEmpty() -> premise
                    route.isNotEmpty() && sublocality.isNotEmpty() -> "$route, $sublocality"
                    route.isNotEmpty() -> route
                    sublocality.isNotEmpty() -> sublocality
                    locality.isNotEmpty() -> locality
                    else -> "Unknown Location"
                }
            } else {
                "Unknown Location"
            }
        } catch (e: Exception) {
            "Unknown Location"
        }
    }
    
    private suspend fun getCurrentLocation(context: Context): android.location.Location? {
        return try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return null
            }

            // First try lastLocation (no GPS needed)
            val lastLocation = fusedClient.lastLocation.await()
            
            if (lastLocation != null && isLocationFresh(lastLocation)) {
                Log.d("TimelineWorker", "Using cached location")
                return lastLocation
            }
            
            // If lastLocation is stale or null
            // request ONE fresh reading
            // then immediately stop
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                10_000L  // 10 second interval
            )
                .setMaxUpdates(1) // ONE reading only
                .setWaitForAccurateLocation(false)
                .setMinUpdateIntervalMillis(0L)
                .setMaxUpdateDelayMillis(10_000L)
                .build()
            
            // Use suspendCancellableCoroutine to get single reading
            val location = suspendCancellableCoroutine<android.location.Location?> { cont ->
                
                val callback = object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        // Got location - stop GPS
                        fusedClient.removeLocationUpdates(this)
                        val loc = result.locations.firstOrNull()
                        Log.d("TimelineWorker", "Fresh GPS reading: ${loc?.latitude}, ${loc?.longitude}")
                        // Resume coroutine with result
                        if (cont.isActive) {
                            cont.resume(loc)
                        }
                    }
                }
                
                fusedClient.requestLocationUpdates(
                    locationRequest,
                    callback,
                    Looper.getMainLooper()
                )
                
                // Timeout after 10 seconds
                // stop GPS if no reading
                cont.invokeOnCancellation {
                    fusedClient.removeLocationUpdates(callback)
                    Log.d("TimelineWorker", "GPS stopped - timeout")
                }
            }
            
            location
            
        } catch (e: SecurityException) {
            Log.e("TimelineWorker", "No location permission: ${e.message}")
            null
        } catch (e: Exception) {
            Log.e("TimelineWorker", "Location error: ${e.message}")
            null
        }
    }

    // Check if last known location is fresh
    // (less than 11 minutes old)
    private fun isLocationFresh(location: android.location.Location): Boolean {
        val ageInMinutes = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / (1_000_000L * 60L)
        val isFresh = ageInMinutes < 11
        Log.d("TimelineWorker", "Location age: ${ageInMinutes}min fresh=$isFresh")
        return isFresh
    }

    private fun Double.roundTo4Decimals(): Double {
        return Math.round(this * 10000.0) / 10000.0
    }
}

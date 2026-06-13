package com.locationtracker.app.service

import android.content.Context
import android.location.Location
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.locationtracker.app.BuildConfig
import com.locationtracker.app.data.local.LocationDatabase
import com.locationtracker.app.data.local.TimelineEntryEntity
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val TAG = "TripTracker"

/**
 * Singleton state machine that mirrors Google Maps Timeline logic.
 *
 * Alternates between two states:
 *   TRAVEL — user is moving (produces a TRAVEL entry)
 *   PLACE  — user is stopped 2+ minutes (produces a PLACE entry)
 *
 * Called from both LocationTrackingService (real-time, 50m threshold)
 * and LocationTimelineWorker (periodic 15-min fallback).
 */
object TripTracker {

    // ── In-memory state ──────────────────────────────────
    private var currentTravelId: Long? = null
    private var currentPlaceId: Long? = null
    private var stationaryStartTime: Long = 0L
    private var totalDistanceMeters: Float = 0f
    private var lastLocation: Location? = null

    // 2 minutes of being still before a PLACE entry is created
    private const val STATIONARY_THRESHOLD_MS = 2 * 60 * 1000L

    // ── Public entry point ───────────────────────────────

    fun onLocationUpdate(
        context: Context,
        location: Location,
        activityType: ActivityType
    ) {
        val speedKmh = if (location.hasSpeed()) location.speed * 3.6f else 0f
        val isMoving = speedKmh >= 1.0f

        Log.d(TAG, "Location update: ${location.latitude}, ${location.longitude} speed=${speedKmh}km/h moving=$isMoving")

        val db = Room.databaseBuilder(context, LocationDatabase::class.java, "location-db")
            .addMigrations(com.locationtracker.app.data.local.MIGRATION_1_2)
            .build()
        val dao = db.timelineDao()

        CoroutineScope(Dispatchers.IO).launch {
            if (isMoving) {
                handleMovement(context, location, activityType, dao)
            } else {
                handleStationary(context, location, dao)
            }
            lastLocation = location
        }
    }

    // ── Movement handler ─────────────────────────────────

    private suspend fun handleMovement(
        context: Context,
        location: Location,
        activityType: ActivityType,
        dao: com.locationtracker.app.data.local.TimelineDao
    ) {
        // Close any open PLACE entry
        currentPlaceId?.let { placeId ->
            val place = dao.getOngoingEntry("PLACE") ?: return@let
            val ended = place.copy(endTime = System.currentTimeMillis())
            dao.update(ended)
            writeEntryToFirebase(context, ended)
            Log.d(TAG, "Closed PLACE entry: ${place.placeName}")
            currentPlaceId = null
        }

        // Accumulate distance from last known point
        lastLocation?.let { last ->
            val dist = FloatArray(1)
            Location.distanceBetween(
                last.latitude, last.longitude,
                location.latitude, location.longitude,
                dist
            )
            totalDistanceMeters += dist[0]
        }

        val now = System.currentTimeMillis()
        val emoji = activityToEmoji(activityType)

        if (currentTravelId == null) {
            // Start new TRAVEL entry
            val entry = TimelineEntryEntity(
                type = "TRAVEL",
                activityType = activityType.name,
                activityEmoji = emoji,
                distanceMeters = totalDistanceMeters,
                startTime = now,
                endTime = now,
                startLat = location.latitude,
                startLng = location.longitude,
                endLat = location.latitude,
                endLng = location.longitude,
                date = todayDate()
            )
            val id = dao.insert(entry)
            currentTravelId = id
            Log.d(TAG, "Started TRAVEL entry id=$id")
        } else {
            // Update ongoing TRAVEL entry
            val existing = dao.getOngoingEntry("TRAVEL") ?: return
            val durationMin = ((now - existing.startTime) / 60_000L).toInt()
            val updated = existing.copy(
                distanceMeters = totalDistanceMeters,
                durationMinutes = durationMin,
                endTime = now,
                endLat = location.latitude,
                endLng = location.longitude,
                activityEmoji = emoji,
                activityType = activityType.name
            )
            dao.update(updated)
            writeEntryToFirebase(context, updated)
        }

        stationaryStartTime = 0L
    }

    // ── Stationary handler ───────────────────────────────

    private suspend fun handleStationary(
        context: Context,
        location: Location,
        dao: com.locationtracker.app.data.local.TimelineDao
    ) {
        val now = System.currentTimeMillis()

        if (stationaryStartTime == 0L) {
            stationaryStartTime = now
            Log.d(TAG, "Started stationary timer")
            return
        }

        val stationaryMs = now - stationaryStartTime

        if (stationaryMs >= STATIONARY_THRESHOLD_MS && currentPlaceId == null) {
            // Close the travel entry first
            currentTravelId?.let { _ ->
                val travel = dao.getOngoingEntry("TRAVEL") ?: return@let
                val durationMin = ((now - travel.startTime) / 60_000L).toInt()
                val closedTravel = travel.copy(
                    endTime = now,
                    distanceMeters = totalDistanceMeters,
                    durationMinutes = durationMin,
                    endLat = location.latitude,
                    endLng = location.longitude
                )
                dao.update(closedTravel)
                writeEntryToFirebase(context, closedTravel)
                Log.d(TAG, "Closed TRAVEL entry — dist=${totalDistanceMeters}m dur=${durationMin}min")
                currentTravelId = null
                totalDistanceMeters = 0f
            }

            // Create a new PLACE entry with reverse geocoded name
            val apiKey = BuildConfig.MAPS_API_KEY
            val placeName = getPlaceName(location.latitude, location.longitude, apiKey)
            val placeAddress = getPlaceAddress(location.latitude, location.longitude, apiKey)
            val placeIcon = iconForPlaceName(placeName)

            val placeEntry = TimelineEntryEntity(
                type = "PLACE",
                placeName = placeName,
                placeAddress = placeAddress,
                placeIcon = placeIcon,
                startTime = now,
                endTime = null,
                startLat = location.latitude,
                startLng = location.longitude,
                date = todayDate()
            )
            val id = dao.insert(placeEntry)
            currentPlaceId = id
            Log.d(TAG, "Started PLACE entry: $placeName id=$id")
            writeEntryToFirebase(context, placeEntry.copy(id = id))
        }
    }

    // ── Firebase sync ────────────────────────────────────

    private fun writeEntryToFirebase(context: Context, entry: TimelineEntryEntity) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val userId = currentUser.uid
        val userName = currentUser.displayName ?: "User"
        val date = entry.date.ifEmpty { todayDate() }

        val ref = Firebase.database.reference
            .child("user_timelines")
            .child(userId)
            .child(date)
            .child("entry_${entry.id}")

        val data = buildMap<String, Any?> {
            put("type", entry.type)
            put("startTime", formatTime(entry.startTime))
            put("endTime", entry.endTime?.let { formatTime(it) } ?: "")
            put("timestamp", entry.startTime)
            put("date", date)
            put("userId", userId)
            put("userName", userName)

            if (entry.type == "PLACE") {
                put("locationName", entry.placeName)
                put("placeAddress", entry.placeAddress)
                put("placeIcon", entry.placeIcon)
            } else {
                put("activityType", entry.activityType)
                put("activityEmoji", entry.activityEmoji)
                put("activityLabel", labelForActivity(entry.activityType))
                put("distanceMeters", entry.distanceMeters)
                put("durationMinutes", entry.durationMinutes)
                put("speedKmh", if (entry.durationMinutes > 0)
                    (entry.distanceMeters / 1000f) / (entry.durationMinutes / 60f)
                else 0f)
            }
        }

        ref.setValue(data)
            .addOnSuccessListener { Log.d(TAG, "Firebase write OK for ${entry.type} entry") }
            .addOnFailureListener { Log.e(TAG, "Firebase write failed: ${it.message}") }

        // Also propagate to friends who share
        syncToFriends(userId, userName, date, "entry_${entry.id}", data)
    }

    private fun syncToFriends(
        userId: String,
        userName: String,
        date: String,
        entryKey: String,
        data: Map<String, Any?>
    ) {
        Firebase.database.reference.child("friends").child(userId).get()
            .addOnSuccessListener { snap ->
                snap.children.forEach { friendNode ->
                    val friendId = friendNode.key ?: return@forEach
                    Firebase.database.reference
                        .child("friend_timelines")
                        .child(friendId)
                        .child(userId)
                        .child(date)
                        .child(entryKey)
                        .setValue(data)
                }
            }
    }

    // ── Geocoding ────────────────────────────────────────

    private suspend fun getPlaceName(lat: Double, lng: Double, apiKey: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json" +
                    "?location=$lat,$lng&rankby=distance&key=$apiKey&language=en"
                val client = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build()
                val response = client.newCall(Request.Builder().url(url).build()).execute()
                val body = response.body?.string() ?: return@withContext "Unknown Location"
                val json = JSONObject(body)
                if (json.optString("status") == "OK") {
                    val results = json.getJSONArray("results")
                    if (results.length() > 0) {
                        return@withContext results.getJSONObject(0).optString("name", "Unknown Location")
                    }
                }
                geocodeFallback(lat, lng, apiKey)
            } catch (e: Exception) {
                Log.e(TAG, "Place name error: ${e.message}")
                "Unknown Location"
            }
        }
    }

    private suspend fun getPlaceAddress(lat: Double, lng: Double, apiKey: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://maps.googleapis.com/maps/api/geocode/json" +
                    "?latlng=$lat,$lng&key=$apiKey&language=en"
                val client = OkHttpClient()
                val response = client.newCall(Request.Builder().url(url).build()).execute()
                val body = response.body?.string() ?: return@withContext ""
                val json = JSONObject(body)
                val results = json.optJSONArray("results") ?: return@withContext ""
                if (results.length() > 0) {
                    return@withContext results.getJSONObject(0).optString("formatted_address", "")
                        .split(",").take(2).joinToString(",")
                }
                ""
            } catch (e: Exception) { "" }
        }
    }

    private fun geocodeFallback(lat: Double, lng: Double, apiKey: String): String {
        return try {
            val url = "https://maps.googleapis.com/maps/api/geocode/json?latlng=$lat,$lng&key=$apiKey&language=en"
            val client = OkHttpClient()
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            val body = response.body?.string() ?: return "Unknown Location"
            val json = JSONObject(body)
            val results = json.optJSONArray("results") ?: return "Unknown Location"
            if (results.length() > 0) {
                val components = results.getJSONObject(0).getJSONArray("address_components")
                for (i in 0 until components.length()) {
                    val comp = components.getJSONObject(i)
                    val types = comp.getJSONArray("types").toString()
                    if (types.contains("sublocality") || types.contains("locality")) {
                        return comp.getString("long_name")
                    }
                }
            }
            "Unknown Location"
        } catch (e: Exception) { "Unknown Location" }
    }

    // ── Helpers ──────────────────────────────────────────

    private fun activityToEmoji(type: ActivityType): String = when (type) {
        ActivityType.DRIVING    -> "\uD83D\uDE97"  // 🚗
        ActivityType.CYCLING    -> "\uD83D\uDEB4"  // 🚴
        ActivityType.RUNNING    -> "\uD83C\uDFC3"  // 🏃
        ActivityType.WALKING    -> "\uD83D\uDEB6"  // 🚶
        ActivityType.STATIONARY -> "\uD83D\uDCCD"  // 📍
    }

    private fun labelForActivity(type: String): String = when (type) {
        "DRIVING"    -> "Driving"
        "CYCLING"    -> "Cycling"
        "RUNNING"    -> "Running"
        "WALKING"    -> "Walking"
        "STATIONARY" -> "Stationary"
        else         -> "Moving"
    }

    private fun iconForPlaceName(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.contains("school") || lower.contains("college") || lower.contains("vidyalay") -> "\uD83C\uDF93" // 🎓
            lower.contains("hospital") || lower.contains("clinic")                              -> "\uD83C\uDFE5" // 🏥
            lower.contains("police")                                                             -> "\uD83D\uDE94" // 🚔
            lower.contains("restaurant") || lower.contains("hotel") || lower.contains("dhaba")  -> "\uD83C\uDF7D" // 🍽
            lower.contains("mall") || lower.contains("shop") || lower.contains("market")        -> "\uD83D\uDECD" // 🛍
            lower.contains("home") || lower.contains("house")                                   -> "\uD83C\uDFE0" // 🏠
            lower.contains("office") || lower.contains("work")                                  -> "\uD83C\uDFE2" // 🏢
            lower.contains("park") || lower.contains("garden")                                  -> "\uD83C\uDF33" // 🌳
            lower.contains("temple") || lower.contains("church") || lower.contains("masjid")    -> "\uD83D\uDED5" // 🛕
            lower.contains("bank")                                                               -> "\uD83C\uDFE6" // 🏦
            lower.contains("station") || lower.contains("railway")                              -> "\uD83D\uDE89" // 🚉
            lower.contains("petrol") || lower.contains("fuel") || lower.contains("pump")        -> "\u26FD"       // ⛽
            else                                                                                 -> "\uD83D\uDCCD" // 📍
        }
    }

    private fun todayDate(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    private fun formatTime(ms: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
}

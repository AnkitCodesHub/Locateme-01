package com.locationtracker.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.locationtracker.app.R
import com.locationtracker.app.data.repository.LocationSharingRepository
import com.locationtracker.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.sqrt

private const val TAG = "LocationShare"
private const val NOTIFICATION_ID = 1001
private const val CHANNEL_ID = "location_sharing_channel"

/**
 * A foreground service that uses a sensor-gated approach for battery-efficient location tracking.
 *
 * Architecture:
 *   - Accelerometer/gyroscope run continuously at ~1mW
 *   - GPS fires ONLY when sensors confirm movement (single high-accuracy fix)
 *   - GPS stays off while stationary, saving ~150mW constant drain
 *
 * Intent extras:
 *   ACTION_START_SHARING  — starts the service and begins sharing with [EXTRA_FRIEND_IDS]
 *   ACTION_STOP_SHARING   — stops sharing with a specific friend [EXTRA_FRIEND_ID]
 *   ACTION_STOP_ALL       — stops all sharing and kills the service
 *   ACTION_UPDATE_FRIENDS — replaces the current sharing list with [EXTRA_FRIEND_IDS]
 */
class LocationTrackingService : Service() {

    companion object {
        const val ACTION_START_SHARING = "com.locationtracker.app.START_SHARING"
        const val ACTION_STOP_SHARING = "com.locationtracker.app.STOP_SHARING"
        const val ACTION_STOP_ALL = "com.locationtracker.app.STOP_ALL"
        const val ACTION_UPDATE_FRIENDS = "com.locationtracker.app.UPDATE_FRIENDS"

        const val EXTRA_FRIEND_ID = "extra_friend_id"
        const val EXTRA_FRIEND_IDS = "extra_friend_ids"

        /** Convenience builder — starts sharing with a list of friend IDs. */
        fun startIntent(context: Context, friendIds: List<String>): Intent =
            Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_START_SHARING
                putStringArrayListExtra(EXTRA_FRIEND_IDS, ArrayList(friendIds))
            }

        /** Convenience builder — stops sharing with a single friend. */
        fun stopFriendIntent(context: Context, friendId: String): Intent =
            Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_STOP_SHARING
                putExtra(EXTRA_FRIEND_ID, friendId)
            }

        /** Convenience builder — stops all sharing and shuts down the service. */
        fun stopAllIntent(context: Context): Intent =
            Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_STOP_ALL
            }
    }

    // --- Dependencies ---
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val repository = LocationSharingRepository()
    private val auth = FirebaseAuth.getInstance()

    /** The set of friend IDs the current user is actively sharing with. */
    private val activeFriendIds = mutableSetOf<String>()

    // --- Coroutine scope ---
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // --- Stationary-detection state machine ---
    private var stationaryPoint: android.location.Location? = null
    private var stationaryArrivalTime: Long = 0L
    private var stationaryLocationName: String = ""
    private var stationaryArrivalTimeStr: String = ""

    // --- Sensor-gated motion detection ---
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var isMoving = false
    private var stationaryReadingCount = 0
    private var movingReadingCount = 0
    private val MOVEMENT_THRESHOLD = 0.8f        // m/s² net acceleration to count as moving
    private val STATIONARY_THRESHOLD = 0.15f      // m/s² net acceleration to count as still
    private val READINGS_TO_CONFIRM_MOVING = 3   // consecutive readings before requesting GPS
    private val READINGS_TO_CONFIRM_STATIONARY = 10  // consecutive readings before killing GPS
    private var pendingGpsFix = false

    // --- Sensor event listener ---
    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            Log.d("SensorDebug", "RAW SENSOR FIRED")
            if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val magnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            val netAcceleration = Math.abs(magnitude - 9.81f)

            Log.d("SensorDebug", "netAccel=$netAcceleration movingCount=$movingReadingCount stationaryCount=$stationaryReadingCount isMoving=$isMoving")

            if (netAcceleration > MOVEMENT_THRESHOLD) {
                // Potential movement reading
                movingReadingCount++
                stationaryReadingCount = 0

                if (!isMoving && movingReadingCount >= READINGS_TO_CONFIRM_MOVING) {
                    isMoving = true
                    Log.d(TAG, "Movement confirmed — requesting GPS fix (net accel=${netAcceleration}m/s²)")
                    requestSingleGpsFix()
                    Log.d("SensorDebug", "GPS FIX REQUESTED")
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        android.widget.Toast.makeText(
                            applicationContext,
                            "GPS requested!",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }

            } else if (netAcceleration < STATIONARY_THRESHOLD) {
                // Potential stationary reading
                stationaryReadingCount++
                movingReadingCount = 0

                if (isMoving && stationaryReadingCount >= READINGS_TO_CONFIRM_STATIONARY) {
                    isMoving = false
                    Log.d(TAG, "Stationary confirmed — GPS stays off (net accel=${netAcceleration}m/s²)")
                }
            } else {
                if (netAcceleration > 0.4f) movingReadingCount++
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            // No-op
        }
    }

    // --- Service lifecycle ---

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()

        // Set up sensor manager and register accelerometer (+ gyroscope if available)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        Log.d("SensorDebug", "Accelerometer available: ${accelerometer != null}")
        Log.d("SensorDebug", "Gyroscope available: ${gyroscope != null}")

        val accelRegistered = sensorManager.registerListener(
            sensorListener,
            accelerometer,
            SensorManager.SENSOR_DELAY_NORMAL
        )
        Log.d("SensorDebug", "Listener registered: $accelRegistered")

        // Gyroscope registered for future use (motion classification); motion gate uses accelerometer
        if (gyroscope != null) {
            val gyroRegistered = sensorManager.registerListener(
                sensorListener,
                gyroscope,
                SensorManager.SENSOR_DELAY_NORMAL
            )
            Log.d("SensorDebug", "Gyroscope listener registered: $gyroRegistered")
        } else {
            Log.d("SensorDebug", "No gyroscope — using accelerometer only")
        }

        Log.d(TAG, "LocationTrackingService created — sensor-gated mode active")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SHARING -> {
                val friendIds = intent.getStringArrayListExtra(EXTRA_FRIEND_IDS) ?: emptyList()
                activeFriendIds.addAll(friendIds)
                Log.d(TAG, "ACTION_START_SHARING — friends: $activeFriendIds")
                startForeground(NOTIFICATION_ID, buildNotification())
                // No continuous GPS — sensors are already running from onCreate()
            }

            ACTION_STOP_SHARING -> {
                val friendId = intent.getStringExtra(EXTRA_FRIEND_ID) ?: return START_NOT_STICKY
                val currentUser = auth.currentUser
                if (currentUser != null) {
                    repository.stopSharing(currentUser.uid, friendId)
                }
                activeFriendIds.remove(friendId)
                Log.d(TAG, "ACTION_STOP_SHARING — removed friend=$friendId, remaining: $activeFriendIds")

                if (activeFriendIds.isEmpty()) {
                    stopSelf()
                }
            }

            ACTION_STOP_ALL -> {
                Log.d(TAG, "ACTION_STOP_ALL — stopping all sharing")
                stopAllAndShutdown()
            }

            ACTION_UPDATE_FRIENDS -> {
                val newIds = intent.getStringArrayListExtra(EXTRA_FRIEND_IDS)?.toSet() ?: emptySet()
                val removed = activeFriendIds - newIds
                val added = newIds - activeFriendIds
                val currentUser = auth.currentUser

                // Stop sharing with removed friends
                if (currentUser != null) {
                    removed.forEach { repository.stopSharing(currentUser.uid, it) }
                }

                activeFriendIds.clear()
                activeFriendIds.addAll(newIds)
                Log.d(TAG, "ACTION_UPDATE_FRIENDS — added: $added, removed: $removed, active: $activeFriendIds")

                if (activeFriendIds.isEmpty()) stopSelf()
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "LocationTrackingService destroyed — stopping all sharing")
        sensorManager.unregisterListener(sensorListener)
        stopAllAndShutdown()
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // --- Internal helpers ---

    /**
     * Requests a single high-accuracy GPS fix via getCurrentLocation().
     * Guards against duplicate concurrent requests with [pendingGpsFix].
     * After the fix arrives, delegates to [processLocationFix].
     */
    @SuppressLint("MissingPermission")
    private fun requestSingleGpsFix() {
        if (pendingGpsFix) return
        pendingGpsFix = true

        val cancellationTokenSource = CancellationTokenSource()
        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            cancellationTokenSource.token
        ).addOnSuccessListener { location ->
            pendingGpsFix = false
            if (location != null) {
                Log.d(TAG, "GPS fix received: lat=${location.latitude} lng=${location.longitude}")
                processLocationFix(location)
            } else {
                Log.w(TAG, "GPS fix returned null")
            }
        }.addOnFailureListener { e ->
            pendingGpsFix = false
            Log.e(TAG, "GPS fix failed: ${e.message}")
        }
    }

    /**
     * Core location processing — contains the 50m stationary-detection check,
     * timeline entry writing to Firebase, and live location push for friends' map.
     *
     * Called only when sensors confirm movement and a GPS fix has been obtained.
     */
    private fun processLocationFix(location: android.location.Location) {
        scope.launch {
            try {
                val currentUser = auth.currentUser
                if (currentUser != null && activeFriendIds.isNotEmpty()) {
                    val timeStr = java.text.SimpleDateFormat(
                        "hh:mm a", java.util.Locale.getDefault()
                    ).format(java.util.Date(System.currentTimeMillis()))

                    val dateKey = java.text.SimpleDateFormat(
                        "yyyy-MM-dd", java.util.Locale.getDefault()
                    ).format(java.util.Date(System.currentTimeMillis()))

                    val currentPoint = stationaryPoint

                    if (currentPoint == null) {
                        // First ever location fix — record as arrival point
                        stationaryPoint = location
                        stationaryArrivalTime = System.currentTimeMillis()
                        stationaryArrivalTimeStr = timeStr
                        stationaryLocationName = reverseGeocode(location)
                        Log.d(TAG, "First fix — arrival recorded at: $stationaryLocationName")

                    } else {
                        val distanceMoved = currentPoint.distanceTo(location)
                        Log.d(TAG, "Distance from stationary point: ${distanceMoved}m")

                        if (distanceMoved >= 50f) {
                            // User moved 50m+ — close current timeline entry and write to Firebase
                            val departureTimeStr = timeStr
                            val entryLocationName = stationaryLocationName

                            Log.d(TAG, "Moved ${distanceMoved}m — writing timeline entry: $entryLocationName")

                            for (friendId in activeFriendIds) {
                                val entryRef = com.google.firebase.database.FirebaseDatabase
                                    .getInstance().reference
                                    .child("friend_timelines")
                                    .child(friendId)
                                    .child(currentUser.uid)
                                    .child(dateKey)
                                    .push()

                                entryRef.setValue(
                                    mapOf(
                                        "locationName" to entryLocationName,
                                        "startTime" to stationaryArrivalTimeStr,
                                        "endTime" to departureTimeStr,
                                        "timestamp" to stationaryArrivalTime,
                                        "latitude" to currentPoint.latitude,
                                        "longitude" to currentPoint.longitude
                                    )
                                )
                            }

                            // Start fresh at new location
                            stationaryPoint = location
                            stationaryArrivalTime = System.currentTimeMillis()
                            stationaryArrivalTimeStr = timeStr
                            stationaryLocationName = reverseGeocode(location)
                            Log.d(TAG, "New stationary point: $stationaryLocationName")

                        } else {
                            // Less than 50m — still at same place, discard fix
                            Log.d(TAG, "Only ${distanceMoved}m moved — discarding fix, still at $stationaryLocationName")
                        }
                    }

                    // Always push live location to friends' map regardless of distance
                    repository.updateLocation(
                        currentUserId = currentUser.uid,
                        displayName = currentUser.displayName ?: currentUser.email ?: "Unknown",
                        sharingWithIds = activeFriendIds.toList(),
                        location = location,
                        profilePictureUrl = currentUser.photoUrl?.toString() ?: ""
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing location fix: ${e.message}")
            }
        }
    }

    /**
     * Reverse geocodes a location to a human-readable name.
     * Returns "Unknown location" on any failure.
     */
    private fun reverseGeocode(location: android.location.Location): String {
        return try {
            val addresses = android.location.Geocoder(
                applicationContext,
                java.util.Locale.getDefault()
            ).getFromLocation(location.latitude, location.longitude, 1)
            addresses?.firstOrNull()?.let {
                it.featureName ?: it.thoroughfare ?: it.subLocality ?: "Unknown location"
            } ?: "Unknown location"
        } catch (e: Exception) {
            "Unknown location"
        }
    }

    private fun stopAllAndShutdown() {
        val currentUser = auth.currentUser
        if (currentUser != null && activeFriendIds.isNotEmpty()) {
            val currentPoint = stationaryPoint
            if (currentPoint != null) {
                val departureTimeStr = java.text.SimpleDateFormat(
                    "hh:mm a", java.util.Locale.getDefault()
                ).format(java.util.Date(System.currentTimeMillis()))

                val dateKey = java.text.SimpleDateFormat(
                    "yyyy-MM-dd", java.util.Locale.getDefault()
                ).format(java.util.Date(System.currentTimeMillis()))

                for (friendId in activeFriendIds) {
                    val entryRef = com.google.firebase.database.FirebaseDatabase
                        .getInstance().reference
                        .child("friend_timelines")
                        .child(friendId)
                        .child(currentUser.uid)
                        .child(dateKey)
                        .push()

                    entryRef.setValue(
                        mapOf(
                            "locationName" to stationaryLocationName,
                            "startTime" to stationaryArrivalTimeStr,
                            "endTime" to departureTimeStr,
                            "timestamp" to stationaryArrivalTime,
                            "latitude" to currentPoint.latitude,
                            "longitude" to currentPoint.longitude
                        )
                    )
                }
                stationaryPoint = null
                Log.d(TAG, "Final timeline entry written on shutdown")
            }

            repository.stopAllSharing(currentUser.uid, activeFriendIds.toList())
        }
        activeFriendIds.clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}

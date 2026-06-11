package com.locationtracker.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.locationtracker.app.R
import com.locationtracker.app.data.repository.LocationSharingRepository
import com.locationtracker.app.ui.MainActivity

private const val TAG = "LocationShare"
private const val NOTIFICATION_ID = 1001
private const val CHANNEL_ID = "location_sharing_channel"
private const val LOCATION_INTERVAL_MS = 10_000L  // 10 seconds
private const val LOCATION_FASTEST_INTERVAL_MS = 5_000L

/**
 * A foreground service that continuously fetches high-accuracy GPS updates via
 * [FusedLocationProviderClient] and writes them to Firebase Realtime Database for
 * every friend the user is currently sharing with.
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

    // --- Location callback ---
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            val currentUser = auth.currentUser ?: return
            val friendIds = activeFriendIds.toList()

            if (friendIds.isEmpty()) {
                Log.d(TAG, "Location update received but no active friends to share with.")
                return
            }

            Log.d(TAG, "Location update → lat=${location.latitude} lng=${location.longitude}, " +
                    "sharing with ${friendIds.size} friend(s): $friendIds")

            repository.updateLocation(
                currentUserId = currentUser.uid,
                displayName = currentUser.displayName ?: currentUser.email ?: "Unknown",
                sharingWithIds = friendIds,
                location = location,
                profilePictureUrl = currentUser.photoUrl?.toString() ?: ""
            )
        }
    }

    // --- Service lifecycle ---

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
        Log.d(TAG, "LocationTrackingService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SHARING -> {
                val friendIds = intent.getStringArrayListExtra(EXTRA_FRIEND_IDS) ?: emptyList()
                activeFriendIds.addAll(friendIds)
                Log.d(TAG, "ACTION_START_SHARING — friends: $activeFriendIds")
                startForeground(NOTIFICATION_ID, buildNotification())
                startLocationUpdates()
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
        stopAllAndShutdown()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // --- Internal helpers ---

    private fun stopAllAndShutdown() {
        val currentUser = auth.currentUser
        if (currentUser != null && activeFriendIds.isNotEmpty()) {
            repository.stopAllSharing(currentUser.uid, activeFriendIds.toList())
        }
        activeFriendIds.clear()
        stopLocationUpdates()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL_MS)
            .setMinUpdateIntervalMillis(LOCATION_FASTEST_INTERVAL_MS)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            Log.d(TAG, "Location updates started — interval=${LOCATION_INTERVAL_MS}ms")
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission denied in service", e)
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        Log.d(TAG, "Location updates stopped")
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

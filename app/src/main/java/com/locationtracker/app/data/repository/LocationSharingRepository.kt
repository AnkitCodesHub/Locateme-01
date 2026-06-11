package com.locationtracker.app.data.repository

import android.location.Location
import android.util.Log
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.locationtracker.app.data.model.UserLocation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private const val TAG = "LocationShare"
private const val NODE_ACTIVE_SHARES = "active_shares"

/**
 * Manages all Firebase Realtime Database read/write operations for location sharing.
 *
 * Firebase schema:
 *   active_shares/
 *     {targetUserId}/          ← The user who is allowed to see the location
 *       {sharingUserId}/       ← The user who is currently sharing
 *         latitude:    Double
 *         longitude:   Double
 *         displayName: String
 *         isSharing:   Boolean
 *         timestamp:   Long
 */
class LocationSharingRepository(
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
) {

    /**
     * Starts sharing the current user's location with [targetFriendId].
     * Writes the initial location payload and registers an onDisconnect handler
     * so the entry is automatically removed if the app crashes or loses connectivity.
     */
    fun startSharing(currentUser: FirebaseUser, targetFriendId: String, location: Location) {
        val ref = database.reference
            .child(NODE_ACTIVE_SHARES)
            .child(targetFriendId)
            .child(currentUser.uid)

        val payload = UserLocation(
            userId = currentUser.uid,
            latitude = location.latitude,
            longitude = location.longitude,
            displayName = currentUser.displayName ?: currentUser.email ?: "Unknown",
            isSharing = true,
            timestamp = System.currentTimeMillis(),
            profilePictureUrl = currentUser.photoUrl?.toString() ?: ""
        ).toMap()

        // Register onDisconnect BEFORE writing so it's always set
        ref.onDisconnect().removeValue()

        ref.setValue(payload)
            .addOnSuccessListener {
                Log.d(TAG, "Started sharing with friend=$targetFriendId " +
                        "lat=${location.latitude} lng=${location.longitude}")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to start sharing with friend=$targetFriendId", e)
            }
    }

    /**
     * Updates lat/lng for all friends the current user is actively sharing with.
     * Called on every FusedLocationProviderClient callback (every ~10 seconds).
     */
    fun updateLocation(
        currentUserId: String,
        displayName: String,
        sharingWithIds: List<String>,
        location: Location,
        profilePictureUrl: String = ""
    ) {
        if (sharingWithIds.isEmpty()) return

        val updates = HashMap<String, Any>()
        val now = System.currentTimeMillis()

        for (friendId in sharingWithIds) {
            val basePath = "$NODE_ACTIVE_SHARES/$friendId/$currentUserId"
            updates["$basePath/latitude"] = location.latitude
            updates["$basePath/longitude"] = location.longitude
            updates["$basePath/displayName"] = displayName
            updates["$basePath/isSharing"] = true
            updates["$basePath/timestamp"] = now
            if (profilePictureUrl.isNotEmpty()) {
                updates["$basePath/profilePictureUrl"] = profilePictureUrl
            }
        }

        database.reference.updateChildren(updates)
            .addOnSuccessListener {
                Log.d(TAG, "Updated location for ${sharingWithIds.size} friend(s) " +
                        "lat=${location.latitude} lng=${location.longitude}")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to update location batch", e)
            }
    }

    /**
     * Stops sharing the current user's location with a specific friend and removes the Firebase node.
     */
    fun stopSharing(currentUserId: String, targetFriendId: String) {
        val ref = database.reference
            .child(NODE_ACTIVE_SHARES)
            .child(targetFriendId)
            .child(currentUserId)

        ref.removeValue()
            .addOnSuccessListener {
                Log.d(TAG, "Stopped sharing with friend=$targetFriendId")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to stop sharing with friend=$targetFriendId", e)
            }
    }

    /**
     * Stops sharing with ALL friends at once (e.g., when the service is destroyed).
     */
    fun stopAllSharing(currentUserId: String, sharingWithIds: List<String>) {
        if (sharingWithIds.isEmpty()) return
        val updates = HashMap<String, Any?>()
        for (friendId in sharingWithIds) {
            updates["$NODE_ACTIVE_SHARES/$friendId/$currentUserId"] = null
        }
        database.reference.updateChildren(updates)
        Log.d(TAG, "Stopped all sharing for user=$currentUserId with ${sharingWithIds.size} friend(s)")
    }

    /**
     * Observes the active location share of a specific friend.
     * Listens to active_shares/{currentUserId}/{friendId}.
     */
    fun observeFriendLocation(currentUserId: String, friendId: String): Flow<UserLocation?> = callbackFlow {
        val ref = database.reference
            .child(NODE_ACTIVE_SHARES)
            .child(currentUserId)
            .child(friendId)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    trySend(null)
                    return
                }
                try {
                    val uid = snapshot.child("userId").getValue(String::class.java) ?: friendId
                    val loc = UserLocation(
                        userId = uid,
                        latitude = snapshot.child("latitude").getValue(Double::class.java) ?: 0.0,
                        longitude = snapshot.child("longitude").getValue(Double::class.java) ?: 0.0,
                        displayName = snapshot.child("displayName").getValue(String::class.java) ?: "",
                        isSharing = snapshot.child("isSharing").getValue(Boolean::class.java) ?: false,
                        timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L,
                        profilePictureUrl = snapshot.child("profilePictureUrl").getValue(String::class.java) ?: ""
                    )
                    trySend(loc)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing location for friend=$friendId", e)
                    trySend(null)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "observeFriendLocation cancelled for $friendId: ${error.message}")
                close(error.toException())
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /**
     * Observes the sharing status of a specific friend toward [currentUserId].
     * Returns a Flow<Boolean> reflecting the isSharing field.
     */
    fun observeFriendSharingStatus(currentUserId: String, friendId: String): Flow<Boolean> =
        callbackFlow {
            val ref = database.reference
                .child(NODE_ACTIVE_SHARES)
                .child(currentUserId)
                .child(friendId)
                .child("isSharing")

            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    trySend(snapshot.getValue(Boolean::class.java) ?: false)
                }

                override fun onCancelled(error: DatabaseError) {
                    close(error.toException())
                }
            }

            ref.addValueEventListener(listener)
            awaitClose { ref.removeEventListener(listener) }
        }
}

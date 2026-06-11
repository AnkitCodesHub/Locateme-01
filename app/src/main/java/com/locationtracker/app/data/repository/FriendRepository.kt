package com.locationtracker.app.data.repository

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.locationtracker.app.data.model.Friend
import com.locationtracker.app.data.model.FriendRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import com.locationtracker.app.data.model.TimelineActivity

private const val TAG = "LocateMeDebug"

/**
 * Manages the friend request and confirmed-friend relationship system.
 *
 * ─── Firebase schema ─────────────────────────────────────────────────────────
 *
 * users/
 *   {userId}/
 *     uid:         String
 *     displayName: String
 *     email:       String
 *
 * friend_requests/
 *   {receiverUserId}/
 *     {senderUserId}: "pending"
 *
 * friends/
 *   {userId1}/
 *     {userId2}: true
 *   {userId2}/
 *     {userId1}: true
 *
 * ─────────────────────────────────────────────────────────────────────────────
 */
class FriendRepository(
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
) {

    private fun usersRef() = database.reference.child("users")
    private fun requestsRef() = database.reference.child("friend_requests")
    private fun friendsRef() = database.reference.child("friends")

    /**
     * Observes the confirmed friends list for [userId].
     * Listens to `friends/{userId}` and resolves each friend's profile from `users/{friendId}`.
     */
    fun getFriends(userId: String): Flow<List<Friend>> = callbackFlow {
        val ref = friendsRef().child(userId)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d(TAG, "getFriends: parsed ${snapshot.childrenCount} friend keys for user $userId")
                val friendIds = snapshot.children
                    .filter { it.getValue(Boolean::class.java) == true }
                    .mapNotNull { it.key }
                
                // Emitting raw objects to be enriched by ViewModel or here.
                // For simplicity, we just pass down basic Friend objects here
                val friends = friendIds.map { Friend(uid = it) }
                trySend(friends)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "getFriends cancelled: ${error.message}")
                close(error.toException())
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /**
     * Sends a friend request from [currentUserId] to [friendEmail].
     */
    suspend fun sendFriendRequest(
        currentUserId: String,
        currentUserDisplayName: String,
        currentUserEmail: String,
        friendEmail: String
    ): Result<String> {
        if (friendEmail.equals(currentUserEmail, ignoreCase = true)) {
            return Result.failure(Exception("You can't add yourself as a friend"))
        }

        return try {
            val usersSnapshot = database.reference
                .child("users")
                .orderByChild("email")
                .equalTo(friendEmail.trim().lowercase())
                .get()
                .await()

            if (!usersSnapshot.exists() || !usersSnapshot.hasChildren()) {
                return Result.failure(Exception("No user found with email: $friendEmail"))
            }

            val targetSnap = usersSnapshot.children.first()
            val targetId = targetSnap.key ?: return Result.failure(Exception("Invalid target ID"))
            val targetName = targetSnap.child("displayName").getValue(String::class.java) ?: friendEmail

            val alreadyFriend = friendsRef().child(currentUserId).child(targetId).get().await()
            if (alreadyFriend.exists()) {
                return Result.failure(Exception("$targetName is already your friend"))
            }

            val alreadySent = requestsRef().child(targetId).child(currentUserId).get().await()
            if (alreadySent.exists()) {
                return Result.failure(Exception("Friend request already sent to $targetName"))
            }

            Log.d(TAG, "Sending request from $currentUserId to $targetId")
            requestsRef().child(targetId).child(currentUserId).setValue(FriendRequest.STATUS_PENDING).await()
            
            Result.success(targetName)
        } catch (e: Exception) {
            Log.e(TAG, "sendFriendRequest failed", e)
            Result.failure(e)
        }
    }

    /**
     * Observes incoming friend requests for [userId].
     */
    fun observeIncomingRequests(userId: String): Flow<List<FriendRequest>> = callbackFlow {
        val ref = requestsRef().child(userId)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d(TAG, "observeIncomingRequests: found ${snapshot.childrenCount} pending requests for $userId")
                val requests = snapshot.children.mapNotNull { child ->
                    val senderId = child.key ?: return@mapNotNull null
                    val status = child.getValue(String::class.java) ?: return@mapNotNull null
                    if (status != FriendRequest.STATUS_PENDING) return@mapNotNull null
                    FriendRequest(senderId = senderId, status = status)
                }
                trySend(requests)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "observeIncomingRequests cancelled", error.toException())
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /**
     * Accepts an incoming friend request from [senderId].
     */
    suspend fun acceptFriendRequest(currentUserId: String, senderId: String): Result<Unit> {
        return try {
            val updates = hashMapOf<String, Any?>(
                "friend_requests/$currentUserId/$senderId" to null,
                "friends/$currentUserId/$senderId" to true,
                "friends/$senderId/$currentUserId" to true
            )
            Log.d(TAG, "Accepting friend request: writing mutual friends for $currentUserId and $senderId")
            database.reference.updateChildren(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "acceptFriendRequest failed", e)
            Result.failure(e)
        }
    }

    /**
     * Rejects an incoming friend request from [senderId].
     */
    suspend fun rejectFriendRequest(currentUserId: String, senderId: String): Result<Unit> {
        return try {
            Log.d(TAG, "Rejecting friend request from $senderId to $currentUserId")
            requestsRef().child(currentUserId).child(senderId).removeValue().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "rejectFriendRequest failed", e)
            Result.failure(e)
        }
    }

    /**
     * Removes a confirmed friendship from both sides.
     */
    suspend fun removeFriend(currentUserId: String, friendId: String): Result<Unit> {
        return try {
            val updates = hashMapOf<String, Any?>(
                "friends/$currentUserId/$friendId" to null,
                "friends/$friendId/$currentUserId" to null
            )
            database.reference.updateChildren(updates).await()
            Log.d(TAG, "Removed friend: $currentUserId unfriended $friendId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "removeFriend failed", e)
            Result.failure(e)
        }
    }

    /**
     * Fetches a single user's profile by UID.
     */
    suspend fun getUserProfile(uid: String): Friend? {
        return try {
            val snap = usersRef().child(uid).get().await()
            if (!snap.exists()) return null
            Friend(
                uid = snap.child("uid").getValue(String::class.java) ?: uid,
                displayName = snap.child("displayName").getValue(String::class.java) ?: "",
                email = snap.child("email").getValue(String::class.java) ?: "",
                photoUrl = snap.child("photoUrl").getValue(String::class.java)
            )
        } catch (e: Exception) {
            Log.e(TAG, "getUserProfile failed for uid=$uid", e)
            null
        }
    }

    /**
     * Writes the current user's profile data to Firebase.
     */
    suspend fun writeUserProfile(userId: String, displayName: String, email: String) {
        val profileData = mapOf(
            "uid" to userId,
            "displayName" to displayName,
            "email" to email
        )
        database.reference.child("users").child(userId).updateChildren(profileData).await()
        Log.d(TAG, "Wrote profile for userId=$userId email=$email")
    }

    suspend fun getFriendById(currentUserId: String, friendId: String): Friend? {
        // First check if they are friends
        val isFriendSnap = friendsRef().child(currentUserId).child(friendId).get().await()
        if (!isFriendSnap.exists()) return null

        // Then get profile
        return getUserProfile(friendId)
    }

    /**
     * Observes the history timeline for a friend on a given date.
     */
    fun getFriendTimeline(friendId: String, dateString: String): Flow<List<TimelineActivity>> = callbackFlow {
        val ref = database.reference.child("history_timelines").child(friendId).child(dateString)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val activities = snapshot.children.mapNotNull { it.getValue(TimelineActivity::class.java) }
                    .sortedByDescending { it.timestamp }
                trySend(activities)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "getFriendTimeline cancelled: \${error.message}")
                close(error.toException())
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }
}

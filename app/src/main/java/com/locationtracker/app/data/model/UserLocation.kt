package com.locationtracker.app.data.model

/**
 * Represents a live location entry stored under:
 *   active_shares/{targetUserId}/{sharingUserId}
 */
data class UserLocation(
    val userId: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val displayName: String = "",
    val isSharing: Boolean = false,
    val timestamp: Long = 0L,
    val profilePictureUrl: String = ""
) {
    /** Converts this model to a Firebase-compatible map for writing. */
    fun toMap(): Map<String, Any> = mapOf(
        "latitude" to latitude,
        "longitude" to longitude,
        "displayName" to displayName,
        "isSharing" to isSharing,
        "timestamp" to timestamp,
        "profilePictureUrl" to profilePictureUrl
    )
}

package com.locationtracker.app.data.model

/**
 * Represents a friend relationship stored under:
 *   users/{userId}/friends/{friendId}
 */
data class Friend(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "uid" to uid,
        "displayName" to displayName,
        "email" to email,
        "photoUrl" to photoUrl
    )
}

package com.locationtracker.app.data.model

/**
 * Represents a friend request entry.
 *
 * Firebase path: friend_requests/{receiverUserId}/{senderUserId} = "pending"
 */
data class FriendRequest(
    val senderId: String = "",
    val senderName: String = "",
    val senderEmail: String = "",
    val status: String = STATUS_PENDING
) {
    companion object {
        const val STATUS_PENDING = "pending"
    }
}

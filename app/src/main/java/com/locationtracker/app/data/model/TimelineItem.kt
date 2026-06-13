package com.locationtracker.app.data.model

/**
 * Lightweight UI display model for the Timeline screen.
 * Populated from Firebase Realtime Database.
 */
data class TimelineItem(
    // "PLACE" or "TRAVEL"
    val type: String = "PLACE",

    // PLACE fields
    val locationName: String = "",
    val placeAddress: String = "",
    val placeIcon: String = "\uD83D\uDCCD",

    // TRAVEL fields
    val activityType: String = "WALKING",
    val activityEmoji: String = "\uD83D\uDEB6",
    val activityLabel: String = "Walking",
    val distanceMeters: Float = 0f,
    val durationMinutes: Int = 0,
    val speedKmh: Float = 0f,

    // Common
    val startTime: String = "",
    val endTime: String = "",
    val timestamp: Long = 0L
)

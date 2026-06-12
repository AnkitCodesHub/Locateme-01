package com.locationtracker.app.data.model

data class TimelineItem(
    val locationName: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val timestamp: Long = 0L
)

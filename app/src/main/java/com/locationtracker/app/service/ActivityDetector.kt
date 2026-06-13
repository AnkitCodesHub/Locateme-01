package com.locationtracker.app.service

object ActivityDetector {

    // Detect activity purely from GPS speed
    // speed is in meters/second from location
    fun detectFromSpeed(speedMps: Float): ActivityType {
        val kmh = speedMps * 3.6f
        return when {
            kmh < 1.0f  -> ActivityType.STATIONARY
            kmh < 7.0f  -> ActivityType.WALKING
            kmh < 15.0f -> ActivityType.RUNNING
            kmh < 30.0f -> ActivityType.CYCLING
            else        -> ActivityType.DRIVING
        }
    }

    fun getLabel(type: ActivityType): String {
        return when (type) {
            ActivityType.STATIONARY -> "Stationary"
            ActivityType.WALKING    -> "Walking"
            ActivityType.RUNNING    -> "Running"
            ActivityType.CYCLING    -> "Cycling"
            ActivityType.DRIVING    -> "Driving"
        }
    }

    fun getEmoji(type: ActivityType): String {
        return when (type) {
            ActivityType.STATIONARY -> "📍"
            ActivityType.WALKING    -> "🚶"
            ActivityType.RUNNING    -> "🏃"
            ActivityType.CYCLING    -> "🚴"
            ActivityType.DRIVING    -> "🚗"
        }
    }

    fun getSpeedLabel(speedMps: Float): String {
        val kmh = (speedMps * 3.6f).toInt()
        return "${kmh} km/h"
    }
}

enum class ActivityType {
    STATIONARY,
    WALKING,
    RUNNING,
    CYCLING,
    DRIVING
}

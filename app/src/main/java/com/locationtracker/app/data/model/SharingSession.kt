package com.locationtracker.app.data.model

/**
 * Represents an active location-sharing session between the current user and a friend.
 *
 * @param friendId      The UID of the friend being shared with.
 * @param durationMillis The total sharing duration in milliseconds; null means indefinite.
 * @param startedAt     System epoch milliseconds when sharing began.
 */
data class SharingSession(
    val friendId: String,
    val durationMillis: Long?,
    val startedAt: Long = System.currentTimeMillis()
) {
    /** Returns the epoch ms when this session should expire, or null if indefinite. */
    val expiresAt: Long?
        get() = durationMillis?.let { startedAt + it }

    /** Returns remaining milliseconds until expiry, or null if indefinite or already expired. */
    fun remainingMillis(): Long? {
        val exp = expiresAt ?: return null
        val remaining = exp - System.currentTimeMillis()
        return if (remaining > 0) remaining else null
    }

    /** Formats remaining time as a short string (e.g. "29m", "1h 3m"). */
    fun remainingLabel(): String {
        val rem = remainingMillis() ?: return "∞"
        val totalMinutes = rem / 1000 / 60
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }
}

/** Preset sharing duration options shown to the user. */
enum class SharingDuration(val label: String, val millis: Long?) {
    FIFTEEN_MIN("15 min", 15 * 60 * 1000L),
    THIRTY_MIN("30 min", 30 * 60 * 1000L),
    ONE_HOUR("1 hour", 60 * 60 * 1000L),
    FOUR_HOURS("4 hours", 4 * 60 * 60 * 1000L),
    INDEFINITE("Indefinite", null)
}

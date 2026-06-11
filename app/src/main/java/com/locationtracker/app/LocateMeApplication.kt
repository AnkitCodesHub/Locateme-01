package com.locationtracker.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.google.firebase.FirebaseApp

/**
 * Custom Application class.
 * Responsibilities:
 *  1. Initialise Firebase on app start.
 *  2. Create the notification channel for the foreground location service.
 *     (Creating the channel early is idempotent — safe to call multiple times.)
 */
class LocateMeApplication : Application() {

    companion object {
        const val LOCATION_CHANNEL_ID = "location_sharing_channel"
    }

    override fun onCreate() {
        super.onCreate()

        // 1. Initialise Firebase
        FirebaseApp.initializeApp(this)

        // 2. Create the notification channel for the foreground service (required on API 26+)
        createLocationNotificationChannel()
    }

    private fun createLocationNotificationChannel() {
        val channel = NotificationChannel(
            LOCATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }
}

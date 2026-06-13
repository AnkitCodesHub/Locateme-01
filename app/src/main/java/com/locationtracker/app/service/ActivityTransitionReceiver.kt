package com.locationtracker.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity

class ActivityTransitionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        
        if (!ActivityTransitionResult.hasResult(intent)) return

        val result = ActivityTransitionResult.extractResult(intent) ?: return

        val latestEvent = result.transitionEvents.lastOrNull() ?: return

        val activityType = when (latestEvent.activityType) {
            DetectedActivity.IN_VEHICLE -> ActivityType.DRIVING
            DetectedActivity.ON_BICYCLE -> ActivityType.CYCLING
            DetectedActivity.RUNNING    -> ActivityType.RUNNING
            DetectedActivity.WALKING,
            DetectedActivity.ON_FOOT    -> ActivityType.WALKING
            DetectedActivity.STILL      -> ActivityType.STATIONARY
            else -> ActivityType.WALKING
        }

        // Save to SharedPreferences so worker can read it
        context.getSharedPreferences("activity_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("last_activity", activityType.name)
            .apply()

        Log.d("ActivityReceiver", "Activity changed: ${activityType.name}")
    }
}

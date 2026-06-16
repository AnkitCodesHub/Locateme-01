package com.locationtracker.app.ui

import android.content.Context

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.ktx.Firebase
import com.google.firebase.database.ktx.database
import com.locationtracker.app.ui.navigation.AppNavGraph
import com.locationtracker.app.ui.navigation.Routes
import com.locationtracker.app.ui.theme.LocateMeTheme

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Constraints
import androidx.work.NetworkType
import java.util.concurrent.TimeUnit
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.google.android.gms.location.LocationServices
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.locationtracker.app.data.local.TimelineEntry
import com.locationtracker.app.data.local.LocationDatabase
import com.locationtracker.app.utils.LocationUtils.getPlaceName

class MainActivity : ComponentActivity() {

    private val auth = FirebaseAuth.getInstance()

    // Permission launcher for location + notifications
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handled reactively inside Compose via rememberMultiplePermissionsState
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestRuntimePermissions()

        val database = LocationDatabase.getInstance(this)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val dao = database.timelineDao()
                val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                // Clean old bad entries
                dao.deleteBadTravelEntries()

                // Get today's entries
                val todayEntries = dao.getEntriesForDate(todayKey).first()

                if (todayEntries.isEmpty()) {
                    // Get last known location
                    val fusedClient = LocationServices.getFusedLocationProviderClient(this@MainActivity)
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        val lastLoc = fusedClient.lastLocation.await()

                        if (lastLoc != null) {
                            val placeName = getPlaceName(this@MainActivity, lastLoc.latitude, lastLoc.longitude)
                            val entry = TimelineEntry(
                                type = "PLACE",
                                placeName = placeName,
                                startTime = System.currentTimeMillis(),
                                endTime = null,
                                latitude = lastLoc.latitude,
                                longitude = lastLoc.longitude,
                                date = todayKey
                            )
                            dao.insert(entry)
                            Log.d("MainActivity", "Initial place created: $placeName")
                        }
                    }
                }

                // FIX EXISTING BAD ENTRIES IN DATABASE
                val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                val fixed = prefs.getBoolean("regeocoded_v3", false)
                if (!fixed) {
                    val entries = dao.getEntriesForDate(todayKey).first()
                    for (entry in entries) {
                        // Fix entries whose placeName looks like coordinates or is empty / "Unknown location"
                        val needsFix = entry.type == "PLACE" && (
                            entry.placeName.isBlank() ||
                            entry.placeName.matches(Regex("-?\\d+\\.\\d+.*")) ||
                            entry.placeName == "Unknown location" ||
                            entry.placeName == "Unknown Place" ||
                            entry.placeName == "Unknown" ||
                            entry.placeName == "Current Location"
                        )

                        if (needsFix) {
                            val name = com.locationtracker.app.utils.LocationUtils.getPlaceName(
                                this@MainActivity, entry.latitude, entry.longitude
                            )
                            val addr = com.locationtracker.app.utils.LocationUtils.getPlaceAddress(
                                this@MainActivity, entry.latitude, entry.longitude
                            )
                            dao.update(entry.copy(
                                placeName = name,
                                placeAddress = addr
                            ))
                            Log.d("MainActivity", "Re-geocoded: $name")
                        }
                    }
                    prefs.edit().putBoolean("regeocoded_v3", true).apply()
                }

            } catch (e: Exception) {
                Log.e("MainActivity", "Startup error: ${e.message}")
            }
        }

        setContent {
            LocateMeTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val currentUser = auth.currentUser
                    if (currentUser != null) {
                        val userId = currentUser.uid
                        val userEmail = currentUser.email?.trim()?.lowercase() ?: ""
                        val userName = currentUser.displayName ?: "User"

                        Log.d("LocateMeAuth", "Forcing database write for UID: \$userId, Email: \$userEmail")

                        val userRef = com.google.firebase.ktx.Firebase.database.reference.child("users").child(userId)
                        val userMap = hashMapOf(
                            "uid" to userId,
                            "email" to userEmail,
                            "displayName" to userName,
                            "profilePictureUrl" to (currentUser.photoUrl?.toString() ?: "")
                        )

                        userRef.setValue(userMap)
                            .addOnSuccessListener {
                                Log.d("LocateMeAuth", "Database write SUCCESS for \$userEmail")
                            }
                            .addOnFailureListener { e: java.lang.Exception ->
                                Log.e("LocateMeAuth", "Database write FAILED", e)
                            }
                    }

                    // Determine start destination: skip auth if user is already signed in
                    val startDestination = if (currentUser != null) Routes.MAIN else Routes.AUTH
                    AppNavGraph(startDestination = startDestination)
                }
            }
        }
    }

    private fun requestRuntimePermissions() {
        val permissionsToRequest = buildList {
            if (ContextCompat.checkSelfPermission(
                    this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        this@MainActivity, Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (ContextCompat.checkSelfPermission(
                        this@MainActivity, Manifest.permission.ACTIVITY_RECOGNITION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    add(Manifest.permission.ACTIVITY_RECOGNITION)
                }
            }
        }
        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}

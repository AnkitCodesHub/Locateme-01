package com.locationtracker.app.ui

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
import com.locationtracker.app.service.LocationTrackingWorker
import java.util.concurrent.TimeUnit

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

        val periodicWorkRequest = PeriodicWorkRequestBuilder<LocationTrackingWorker>(10, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "LocationTimelineWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWorkRequest
        )

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
        }
        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}

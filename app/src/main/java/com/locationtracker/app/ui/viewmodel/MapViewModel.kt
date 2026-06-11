package com.locationtracker.app.ui.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.auth.FirebaseAuth
import com.locationtracker.app.data.model.Friend
import com.locationtracker.app.data.model.SharingDuration
import com.locationtracker.app.data.model.SharingSession
import com.locationtracker.app.data.model.UserLocation
import com.locationtracker.app.data.repository.FriendRepository
import com.locationtracker.app.data.repository.LocationSharingRepository
import com.locationtracker.app.service.LocationTrackingService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val TAG = "MapViewModel"

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()
    private val auth = FirebaseAuth.getInstance()
    private val locationRepo = LocationSharingRepository()
    private val friendRepo = FriendRepository()
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    // --- Friend locations visible on the map ---
    private val _friendLocations = MutableStateFlow<List<UserLocation>>(emptyList())
    val friendLocations: StateFlow<List<UserLocation>> = _friendLocations.asStateFlow()

    // --- Current user's own location ---
    private val _myLocation = MutableStateFlow<UserLocation?>(null)
    val myLocation: StateFlow<UserLocation?> = _myLocation.asStateFlow()

    // --- Friend list for the bottom sheet ---
    private val _friends = MutableStateFlow<List<Friend>>(emptyList())
    val friends: StateFlow<List<Friend>> = _friends.asStateFlow()

    // --- Active sharing sessions (friendId → session) ---
    private val _activeSessions = MutableStateFlow<Map<String, SharingSession>>(emptyMap())
    val activeSessions: StateFlow<Map<String, SharingSession>> = _activeSessions.asStateFlow()

    // --- Countdown labels (friendId → "29m", "∞", etc.) ---
    private val _countdownLabels = MutableStateFlow<Map<String, String>>(emptyMap())
    val countdownLabels: StateFlow<Map<String, String>> = _countdownLabels.asStateFlow()

    // --- Loading/error states ---
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Internal: per-friend countdown jobs
    private val countdownJobs = mutableMapOf<String, Job>()

    init {
        observeFriendLocations()
        loadFriends()
    }

    // -----------------------------------------------------------------------
    // Location Observation
    // -----------------------------------------------------------------------

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeFriendLocations() {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            friendRepo.getFriends(userId)
                .flatMapLatest { friendIds ->
                    if (friendIds.isEmpty()) {
                        flowOf(emptyList<UserLocation>())
                    } else {
                        val flows = friendIds.map { friend ->
                            locationRepo.observeFriendLocation(userId, friend.uid)
                        }
                        combine(flows) { locationsArray ->
                            locationsArray.filterNotNull().filter { it.isSharing }
                        }
                    }
                }
                .collect { locations ->
                    _friendLocations.value = locations
                }
        }
    }

    /** Fetches the current device location once (for map camera positioning). */
    fun fetchMyLocation() {
        val user = auth.currentUser ?: return
        viewModelScope.launch {
            try {
                val cts = CancellationTokenSource()
                val location = fusedLocationClient
                    .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                    .await()
                location?.let {
                    _myLocation.value = UserLocation(
                        userId = user.uid,
                        latitude = it.latitude,
                        longitude = it.longitude,
                        displayName = user.displayName ?: user.email ?: "Me",
                        isSharing = false,
                        timestamp = System.currentTimeMillis()
                    )
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Location permission denied", e)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get current location", e)
            }
        }
    }

    // -----------------------------------------------------------------------
    // Friend List
    // -----------------------------------------------------------------------

    private fun loadFriends() {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            friendRepo.getFriends(userId).collect { list ->
                _friends.value = list
            }
        }
    }

    // -----------------------------------------------------------------------
    // Sharing Control
    // -----------------------------------------------------------------------

    /**
     * Toggles sharing with the given friend. If already sharing, stops. If not, starts
     * sharing with the provided [duration] (null = indefinite, handled by SharingDuration).
     */
    fun toggleSharing(friend: Friend, duration: SharingDuration) {
        val sessions = _activeSessions.value
        if (sessions.containsKey(friend.uid)) {
            stopSharing(friend.uid)
        } else {
            startSharing(friend, duration)
        }
    }

    private fun startSharing(friend: Friend, duration: SharingDuration) {
        val currentUser = auth.currentUser ?: return
        Log.d(TAG, "Starting sharing with friend=${friend.uid} duration=${duration.label}")

        viewModelScope.launch {
            // Get current location to write immediately
            try {
                val cts = CancellationTokenSource()
                val location = fusedLocationClient
                    .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                    .await()

                location?.let {
                    // Write initial location immediately
                    locationRepo.startSharing(currentUser, friend.uid, it)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get location before starting share", e)
            }

            // Create the session
            val session = SharingSession(
                friendId = friend.uid,
                durationMillis = duration.millis,
                startedAt = System.currentTimeMillis()
            )

            _activeSessions.value = _activeSessions.value + (friend.uid to session)

            // Start the foreground service for continuous updates
            val allFriendIds = _activeSessions.value.keys.toList()
            context.startForegroundService(
                LocationTrackingService.startIntent(context, allFriendIds)
            )

            // Start countdown job if timed
            if (duration.millis != null) {
                startCountdown(friend.uid, session)
            } else {
                // Indefinite — just show ∞
                _countdownLabels.value = _countdownLabels.value + (friend.uid to "∞")
            }
        }
    }

    fun stopSharing(friendId: String) {
        Log.d(TAG, "Stopping sharing with friendId=$friendId")
        val currentUser = auth.currentUser ?: return

        // Cancel countdown
        countdownJobs[friendId]?.cancel()
        countdownJobs.remove(friendId)

        // Remove session
        _activeSessions.value = _activeSessions.value - friendId
        _countdownLabels.value = _countdownLabels.value - friendId

        // Stop Firebase entry
        locationRepo.stopSharing(currentUser.uid, friendId)

        // Update or stop service
        val remainingIds = _activeSessions.value.keys.toList()
        if (remainingIds.isEmpty()) {
            context.startService(LocationTrackingService.stopAllIntent(context))
        } else {
            context.startForegroundService(
                LocationTrackingService.startIntent(context, remainingIds)
            )
        }
    }

    private fun startCountdown(friendId: String, session: SharingSession) {
        countdownJobs[friendId]?.cancel()
        countdownJobs[friendId] = viewModelScope.launch {
            while (true) {
                val remaining = session.remainingMillis()
                if (remaining == null || remaining <= 0) {
                    // Time's up — auto-stop
                    Log.d(TAG, "Timer expired for friendId=$friendId — auto-stopping")
                    stopSharing(friendId)
                    break
                }
                _countdownLabels.value = _countdownLabels.value +
                        (friendId to session.remainingLabel())
                delay(30_000L) // Update label every 30 seconds
            }
        }
        // Set initial label immediately
        _countdownLabels.value = _countdownLabels.value + (friendId to session.remainingLabel())
    }

    fun clearError() {
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        countdownJobs.values.forEach { it.cancel() }
    }
}

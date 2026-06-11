package com.locationtracker.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.locationtracker.app.data.model.Friend
import com.locationtracker.app.data.model.FriendRequest
import com.locationtracker.app.data.model.TimelineActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.locationtracker.app.data.repository.FriendRepository
import com.locationtracker.app.data.repository.LocationSharingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FriendViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val friendRepo = FriendRepository()
    private val locationRepo = LocationSharingRepository()

    // --- Friend list ---
    private val _friends = MutableStateFlow<List<Friend>>(emptyList())
    val friends: StateFlow<List<Friend>> = _friends.asStateFlow()

    // --- Incoming Requests ---
    private val _incomingRequests = MutableStateFlow<List<FriendRequest>>(emptyList())
    val incomingRequests: StateFlow<List<FriendRequest>> = _incomingRequests.asStateFlow()

    // --- Selected friend profile ---
    private val _selectedFriend = MutableStateFlow<Friend?>(null)
    val selectedFriend: StateFlow<Friend?> = _selectedFriend.asStateFlow()

    // --- Is selected friend sharing live? ---
    private val _isFriendLive = MutableStateFlow(false)
    val isFriendLive: StateFlow<Boolean> = _isFriendLive.asStateFlow()

    // --- Timeline Activities ---
    private val _timelineActivities = MutableStateFlow<List<TimelineActivity>>(emptyList())
    val timelineActivities: StateFlow<List<TimelineActivity>> = _timelineActivities.asStateFlow()

    // --- Loading / Error ---
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    init {
        loadFriends()
        loadIncomingRequests()
    }

    private fun loadFriends() {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            friendRepo.getFriends(userId).collect { list ->
                // Fetch profiles for each friend ID
                val profiles = list.mapNotNull { friendKey ->
                    friendRepo.getUserProfile(friendKey.uid)
                }
                _friends.value = profiles
            }
        }
    }

    private fun loadIncomingRequests() {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            friendRepo.observeIncomingRequests(userId).collect { list ->
                val enriched = list.map { req ->
                    val profile = friendRepo.getUserProfile(req.senderId)
                    req.copy(
                        senderName = profile?.displayName ?: req.senderId,
                        senderEmail = profile?.email ?: ""
                    )
                }
                _incomingRequests.value = enriched
            }
        }
    }

    /**
     * Loads a specific friend's profile and starts observing their live sharing status.
     * Only succeeds if the friend is mutually verified.
     */
    fun loadFriendProfile(friendId: String) {
        val currentUserId = auth.currentUser?.uid ?: return

        viewModelScope.launch {
            _isLoading.value = true
            val profile = friendRepo.getFriendById(currentUserId, friendId)
            _selectedFriend.value = profile
            _isLoading.value = false

            if (profile == null) {
                _errorMessage.value = "Friend not found or you are not friends."
            }
        }

        // Observe live status
        viewModelScope.launch {
            locationRepo.observeFriendSharingStatus(currentUserId, friendId).collect { isLive ->
                _isFriendLive.value = isLive
            }
        }

        // Observe timeline
        viewModelScope.launch {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val dateString = dateFormat.format(Date())
            friendRepo.getFriendTimeline(friendId, dateString).collect { activities ->
                _timelineActivities.value = activities
            }
        }
    }

    fun sendFriendRequest(friendEmail: String) {
        val user = auth.currentUser ?: return
        viewModelScope.launch {
            _isLoading.value = true
            val result = friendRepo.sendFriendRequest(
                currentUserId = user.uid,
                currentUserDisplayName = user.displayName ?: user.email ?: "Unknown",
                currentUserEmail = user.email ?: "",
                friendEmail = friendEmail
            )
            _isLoading.value = false
            if (result.isSuccess) {
                _successMessage.value = "Friend request sent to ${result.getOrNull()}"
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "Failed to send request"
            }
        }
    }

    fun acceptRequest(senderId: String) {
        val user = auth.currentUser ?: return
        viewModelScope.launch {
            _isLoading.value = true
            val result = friendRepo.acceptFriendRequest(user.uid, senderId)
            _isLoading.value = false
            if (result.isSuccess) {
                _successMessage.value = "Friend request accepted"
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "Failed to accept request"
            }
        }
    }

    fun rejectRequest(senderId: String) {
        val user = auth.currentUser ?: return
        viewModelScope.launch {
            _isLoading.value = true
            val result = friendRepo.rejectFriendRequest(user.uid, senderId)
            _isLoading.value = false
            if (result.isSuccess) {
                _successMessage.value = "Friend request rejected"
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "Failed to reject request"
            }
        }
    }

    fun removeFriend(friendId: String) {
        val user = auth.currentUser ?: return
        viewModelScope.launch {
            val result = friendRepo.removeFriend(user.uid, friendId)
            if (result.isFailure) {
                _errorMessage.value = "Failed to remove friend"
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearSuccess() {
        _successMessage.value = null
    }
}

package com.locationtracker.app.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.userProfileChangeRequest
import com.locationtracker.app.data.repository.FriendRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val TAG = "AuthViewModel"

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val user: FirebaseUser) : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val friendRepository = FriendRepository()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    init {
        // Observe Firebase auth state changes (e.g. session restoration on launch)
        auth.addAuthStateListener { firebaseAuth ->
            _currentUser.value = firebaseAuth.currentUser
        }
    }

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _authState.value = AuthState.Error("Please enter email and password")
            return
        }

        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
                val user = result.user ?: throw Exception("Sign-in failed: no user returned")
                Log.d(TAG, "Sign-in success: ${user.uid}")
                _authState.value = AuthState.Success(user)
            } catch (e: Exception) {
                Log.e(TAG, "Sign-in failed", e)
                _authState.value = AuthState.Error(e.message ?: "Sign-in failed")
            }
        }
    }

    fun signUp(email: String, password: String, displayName: String) {
        if (email.isBlank() || password.isBlank() || displayName.isBlank()) {
            _authState.value = AuthState.Error("All fields are required")
            return
        }
        if (password.length < 6) {
            _authState.value = AuthState.Error("Password must be at least 6 characters")
            return
        }

        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
                val user = result.user ?: throw Exception("Registration failed: no user returned")

                // Set display name in Firebase Auth profile
                val profileUpdates = userProfileChangeRequest { this.displayName = displayName.trim() }
                user.updateProfile(profileUpdates).await()

                // Write profile to Realtime Database so friends can find by email
                friendRepository.writeUserProfile(user.uid, displayName.trim(), email.trim())

                Log.d(TAG, "Sign-up success: ${user.uid} displayName=${displayName}")
                _authState.value = AuthState.Success(user)
            } catch (e: Exception) {
                Log.e(TAG, "Sign-up failed", e)
                _authState.value = AuthState.Error(e.message ?: "Registration failed")
            }
        }
    }

    fun signOut() {
        auth.signOut()
        _authState.value = AuthState.Idle
        Log.d(TAG, "User signed out")
    }

    fun resetState() {
        _authState.value = AuthState.Idle
    }
}

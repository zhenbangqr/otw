package com.zhenbang.otw.profile // Or your profile package

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.zhenbang.otw.data.AuthRepository
import com.zhenbang.otw.data.FirebaseAuthRepository // Assuming direct instantiation
import com.zhenbang.otw.data.UserProfile
import com.zhenbang.otw.data.isProfileIncomplete // Import the helper function
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// Represents the status of fetching/checking the user profile
enum class ProfileStatus {
    LOADING,
    COMPLETE,
    INCOMPLETE,
    ERROR
}

// UI State for profile information
data class ProfileUiState(
    val userProfile: UserProfile? = null,
    val profileStatus: ProfileStatus = ProfileStatus.LOADING,
    val errorMessage: String? = null
)

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository: AuthRepository = FirebaseAuthRepository() // Use DI ideally
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    private val TAG = "ProfileViewModel"

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    // Trigger profile fetch when the ViewModel is created if a user is logged in
    init {
        observeAuthState()
    }

    // Observe Firebase Auth state to fetch profile when user logs in
    private fun observeAuthState() {
        viewModelScope.launch {
            // This is a simple way; a more robust way might involve a dedicated auth state flow
            firebaseAuth.addAuthStateListener { auth ->
                val user = auth.currentUser
                if (user != null) {
                    Log.d(TAG, "Auth state changed, user logged in: ${user.uid}. Fetching profile.")
                    fetchUserProfile(user.uid)
                } else {
                    Log.d(TAG, "Auth state changed, user logged out.")
                    _uiState.value = ProfileUiState(profileStatus = ProfileStatus.ERROR, errorMessage = "User logged out") // Or a LOGGED_OUT status
                }
            }
            // Initial fetch if already logged in
            firebaseAuth.currentUser?.uid?.let { fetchUserProfile(it) }
        }
    }


    fun fetchUserProfile(userId: String) {
        // Prevent fetching if already loading
        if (_uiState.value.profileStatus == ProfileStatus.LOADING && _uiState.value.userProfile != null) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(profileStatus = ProfileStatus.LOADING, errorMessage = null)
            Log.d(TAG, "Fetching profile for user: $userId")
            val result = authRepository.getUserProfile(userId)

            result.onSuccess { userProfile ->
                Log.d(TAG, "Profile fetch success for $userId. Profile: $userProfile")
                val status = if (userProfile.isProfileIncomplete()) {
                    ProfileStatus.INCOMPLETE
                } else {
                    ProfileStatus.COMPLETE
                }
                _uiState.value = ProfileUiState(userProfile = userProfile, profileStatus = status)

            }.onFailure { exception ->
                Log.e(TAG, "Profile fetch failed for $userId", exception)
                // If profile fetch fails, assume incomplete for safety? Or show error?
                // Let's treat fetch failure as an error state.
                _uiState.value = ProfileUiState(profileStatus = ProfileStatus.ERROR, errorMessage = "Failed to load profile: ${exception.message}")
            }
        }
    }

    // Function to update profile - can be called from CompleteProfileViewModel or ProfileScreen
    fun updateUserProfile(userId: String, updates: Map<String, Any?>) {
        viewModelScope.launch {
            // Optionally set a saving state
            Log.d(TAG, "Attempting profile update for $userId")
            val result = authRepository.updateUserProfile(userId, updates)
            result.onSuccess {
                Log.d(TAG, "Profile update successful for $userId. Refetching profile.")
                // Refetch profile to update the UI state
                fetchUserProfile(userId)
                // Optionally emit a success event
            }.onFailure { exception ->
                Log.e(TAG, "Profile update failed for $userId", exception)
                // Update state to show error
                _uiState.value = _uiState.value.copy(
                    profileStatus = ProfileStatus.ERROR, // Or keep previous status but show error msg
                    errorMessage = "Failed to update profile: ${exception.message}"
                )
            }
        }
    }

    // Helper to clear error messages
    fun clearError() {
        if (_uiState.value.errorMessage != null) {
            _uiState.value = _uiState.value.copy(errorMessage = null)
        }
    }
}

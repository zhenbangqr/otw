package com.zhenbang.otw.profile // Or your profile package

import android.app.Application
import android.net.Uri // Import Uri
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
    COMPLETE, // Profile exists and has essential details
    INCOMPLETE, // Profile exists but missing essential details, or doesn't exist yet
    ERROR,
    LOGGED_OUT
}

// UI State for profile information
data class ProfileUiState(
    val userProfile: UserProfile? = null,
    val profileStatus: ProfileStatus = ProfileStatus.LOADING,
    val errorMessage: String? = null,
    val isUploadingImage: Boolean = false
)

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository: AuthRepository = FirebaseAuthRepository() // Use DI ideally
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    private val TAG = "ProfileViewModel"

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        observeAuthState()
    }

    private fun observeAuthState() {
        viewModelScope.launch {
            firebaseAuth.addAuthStateListener { auth ->
                val user = auth.currentUser
                if (user != null) {
                    // Fetch profile only if user changed or profile is not settled
                    if (uiState.value.userProfile?.uid != user.uid || (uiState.value.profileStatus != ProfileStatus.COMPLETE && uiState.value.profileStatus != ProfileStatus.INCOMPLETE)) {
                        Log.d(TAG, "Auth state changed or profile not settled, user logged in: ${user.uid}. Fetching profile.")
                        fetchUserProfile(user.uid)
                    }
                } else {
                    Log.d(TAG, "Auth state changed, user logged out.")
                    _uiState.update { it.copy(userProfile = null, profileStatus = ProfileStatus.LOGGED_OUT, errorMessage = null, isUploadingImage = false) }
                }
            }
            // Initial fetch if already logged in and profile not loaded/settled
            firebaseAuth.currentUser?.uid?.let {
                if (uiState.value.userProfile == null || (uiState.value.profileStatus != ProfileStatus.COMPLETE && uiState.value.profileStatus != ProfileStatus.INCOMPLETE)) {
                    fetchUserProfile(it)
                }
            }
        }
    }


    fun fetchUserProfile(userId: String) {
        if (_uiState.value.profileStatus == ProfileStatus.LOADING && _uiState.value.userProfile?.uid == userId) {
            Log.d(TAG, "Already fetching profile for $userId, skipping.")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(profileStatus = ProfileStatus.LOADING, errorMessage = null) }
            Log.d(TAG, "Fetching profile for user: $userId")
            val result = authRepository.getUserProfile(userId)

            result.onSuccess { userProfile ->
                Log.d(TAG, "Profile fetch success for $userId. Profile: $userProfile")
                val status = if (userProfile.isProfileIncomplete()) {
                    ProfileStatus.INCOMPLETE
                } else {
                    ProfileStatus.COMPLETE
                }
                if (firebaseAuth.currentUser?.uid == userId) {
                    _uiState.update { it.copy(userProfile = userProfile, profileStatus = status) }
                } else {
                    Log.w(TAG,"Profile fetched for $userId but user has changed. Ignoring result.")
                }

            }.onFailure { exception ->
                Log.e(TAG, "Profile fetch failed for $userId", exception)
                if (firebaseAuth.currentUser?.uid == userId) {
                    _uiState.update { it.copy(profileStatus = ProfileStatus.ERROR, errorMessage = "Failed to load profile: ${exception.message}") }
                }
            }
        }
    }

    // --- Function to handle image selection and start upload ---
    fun handleProfileImageSelection(imageUri: Uri?) {
        if (imageUri == null) {
            Log.w(TAG, "Image selection cancelled or failed.")
            return
        }
        val userId = firebaseAuth.currentUser?.uid
        if (userId == null) {
            _uiState.update { it.copy(errorMessage = "Not logged in.", isUploadingImage = false) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingImage = true, errorMessage = null) }
            Log.d(TAG, "Starting profile image upload for user $userId")

            // *** Call the actual repository function ***
            val uploadResult = authRepository.uploadProfileImage(userId, imageUri)
            // ******************************************

            uploadResult.onSuccess { downloadUrl ->
                Log.d(TAG, "Image upload successful. URL: $downloadUrl. Updating profile.")
                // Now update the profileImageUrl field in Firestore
                val updateResult = authRepository.updateUserProfile(userId, mapOf("profileImageUrl" to downloadUrl))
                updateResult.onSuccess {
                    Log.d(TAG, "Profile image URL updated in Firestore.")
                    // Update local state immediately for instant UI feedback
                    _uiState.update { currentState ->
                        currentState.copy(
                            isUploadingImage = false,
                            userProfile = currentState.userProfile?.copy(profileImageUrl = downloadUrl)
                        )
                    }
                }.onFailure { updateException ->
                    Log.e(TAG, "Failed to update profileImageUrl in Firestore", updateException)
                    _uiState.update { it.copy(isUploadingImage = false, errorMessage = "Failed to save new picture.") }
                }

            }.onFailure { uploadException ->
                Log.e(TAG, "Profile image upload failed", uploadException)
                _uiState.update { it.copy(isUploadingImage = false, errorMessage = "Failed to upload image: ${uploadException.message}") }
            }
        }
    }
    // ---------------------------------------------------------


    // Function to update profile - can be called from CompleteProfileViewModel or ProfileScreen
    fun updateUserProfile(userId: String, updates: Map<String, Any?>) {
        viewModelScope.launch {
            Log.d(TAG, "Attempting profile update for $userId")
            val result = authRepository.updateUserProfile(userId, updates)
            result.onSuccess {
                Log.d(TAG, "Profile update successful for $userId. Refetching profile.")
                fetchUserProfile(userId) // Refetch to ensure UI has latest data
            }.onFailure { exception ->
                Log.e(TAG, "Profile update failed for $userId", exception)
                _uiState.update { it.copy(
                    errorMessage = "Failed to update profile: ${exception.message}"
                )}
            }
        }
    }

    // Helper to clear error messages
    fun clearError() {
        if (_uiState.value.errorMessage != null) {
            _uiState.update { it.copy(errorMessage = null) }
        }
    }
}

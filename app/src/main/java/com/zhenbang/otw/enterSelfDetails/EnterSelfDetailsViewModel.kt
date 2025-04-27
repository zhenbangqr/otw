package com.zhenbang.otw.enterSelfDetails // Or a more specific package like 'profile'

import android.app.Application
import android.util.Log
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.zhenbang.otw.data.AuthRepository
import com.zhenbang.otw.data.FirebaseAuthRepository // Assuming direct instantiation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch



class EnterSelfDetailsViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository: AuthRepository = FirebaseAuthRepository() // Use DI ideally
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    private val TAG = "SelfDetailsViewModel"

    private val _uiState = MutableStateFlow(SelfDetailsUiState())
    val uiState = _uiState.asStateFlow()

    // Function to update a specific field in the state
    fun updateField(field: String, value: String) {
        _uiState.value = when (field) {
            "displayName" -> _uiState.value.copy(displayName = value)
            "phoneNumber" -> _uiState.value.copy(phoneNumber = value)
            // Add cases for other fields
            else -> _uiState.value
        }
        // Clear error when user types
        if (_uiState.value.errorMessage != null) {
            _uiState.value = _uiState.value.copy(errorMessage = null)
        }
    }

    // Function to save the entered details
    fun saveDetails() {
        val currentState = _uiState.value
        val userId = firebaseAuth.currentUser?.uid

        // --- Basic Validation ---
        if (currentState.displayName.isBlank()) {
            _uiState.value = currentState.copy(errorMessage = "Display Name cannot be empty.")
            return
        }
        // Add more validation as needed (phone number format, etc.)
        // Example phone validation (very basic)
        if (currentState.phoneNumber.isNotBlank() && !Patterns.PHONE.matcher(currentState.phoneNumber).matches()) {
            _uiState.value = currentState.copy(errorMessage = "Invalid phone number format.")
            return
        }

        if (userId == null) {
            _uiState.value = currentState.copy(errorMessage = "Error: Not logged in.", isLoading = false)
            return
        }
        // -----------------------


        viewModelScope.launch {
            _uiState.value = currentState.copy(isLoading = true, errorMessage = null, isSaveSuccess = false)
            Log.d(TAG, "Attempting to save details for user: $userId")

            // Prepare the data map for Firestore update
            val profileUpdates = mutableMapOf<String, Any?>()
            if (currentState.displayName.isNotBlank()) profileUpdates["displayName"] = currentState.displayName
            if (currentState.phoneNumber.isNotBlank()) profileUpdates["phoneNumber"] = currentState.phoneNumber
            // Add other fields to the map

            if (profileUpdates.isEmpty()) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "No details entered.")
                return@launch
            }

            // Call the repository function to update the profile
            val result = authRepository.updateUserProfile(userId, profileUpdates)

            result.onSuccess {
                Log.d(TAG, "Profile details saved successfully for $userId.")
                _uiState.value = _uiState.value.copy(isLoading = false, isSaveSuccess = true)
            }.onFailure { exception ->
                Log.e(TAG, "Failed to save profile details for $userId", exception)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isSaveSuccess = false,
                    errorMessage = "Failed to save details: ${exception.message}"
                )
            }
        }
    }

    // Function to reset the success flag after navigation
    fun resetSaveSuccessFlag() {
        if (_uiState.value.isSaveSuccess) {
            _uiState.value = _uiState.value.copy(isSaveSuccess = false)
        }
    }
}

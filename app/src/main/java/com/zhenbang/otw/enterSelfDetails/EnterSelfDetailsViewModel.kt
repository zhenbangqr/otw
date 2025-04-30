package com.zhenbang.otw.enterSelfDetails

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp // Import Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.zhenbang.otw.data.AuthRepository
import com.zhenbang.otw.data.FirebaseAuthRepository // Assuming direct instantiation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update // Use update for cleaner state modification
import kotlinx.coroutines.launch
import java.util.Calendar // For Date Picker default

// UI State for the Self Details Screen - Updated Fields


class EnterSelfDetailsViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository: AuthRepository = FirebaseAuthRepository() // Use DI ideally
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    private val TAG = "EnterSelfDetailsVM" // Shortened Tag

    private val _uiState = MutableStateFlow(SelfDetailsUiState())
    val uiState = _uiState.asStateFlow()

    // Function to update a specific text field in the state
    fun updateTextField(field: String, value: String) {
        // Clear error when user types
        val clearError = _uiState.value.errorMessage != null

        _uiState.update { currentState ->
            when (field) {
                "displayName" -> currentState.copy(
                    displayName = value,
                    errorMessage = if (clearError) null else currentState.errorMessage
                )
                "phoneNumber" -> currentState.copy(
                    phoneNumber = value,
                    errorMessage = if (clearError) null else currentState.errorMessage
                )
                "bio" -> currentState.copy(
                    bio = value,
                    errorMessage = if (clearError) null else currentState.errorMessage
                )
                else -> currentState
            }
        }
    }

    // Function to update the selected birthdate
    fun updateBirthdate(selectedMillis: Long?) {
        _uiState.update { it.copy(birthdateMillis = selectedMillis, showDatePicker = false) }
        // Clear error when date is selected
        if (_uiState.value.errorMessage != null) {
            _uiState.update { it.copy(errorMessage = null) }
        }
    }

    // Function to show/hide the date picker
    fun showDatePicker(show: Boolean) {
        _uiState.update { it.copy(showDatePicker = show) }
    }


    // Function to save the entered details
    fun saveDetails() {
        val currentState = _uiState.value
        val userId = firebaseAuth.currentUser?.uid

        // --- Validation ---
        if (currentState.displayName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Display Name cannot be empty.") }
            return
        }
        if (currentState.phoneNumber.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Phone Number cannot be empty.") }
            return
        }
        // Basic phone format check (adjust regex if needed for specific formats)
        if (!android.util.Patterns.PHONE.matcher(currentState.phoneNumber).matches()) {
            _uiState.update { it.copy(errorMessage = "Invalid phone number format.") }
            return
        }
        if (currentState.birthdateMillis == null) {
            _uiState.update { it.copy(errorMessage = "Please select your birthdate.") }
            return
        }
        // -----------------------


        if (userId == null) {
            _uiState.update { it.copy(errorMessage = "Error: Not logged in.", isLoading = false) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, isSaveSuccess = false) }
            Log.d(TAG, "Attempting to save details for user: $userId")

            // Prepare the data map for Firestore update
            val profileUpdates = mutableMapOf<String, Any?>()
            profileUpdates["displayName"] = currentState.displayName
            profileUpdates["phoneNumber"] = currentState.phoneNumber
            // Convert Long millis to Firebase Timestamp
            currentState.birthdateMillis?.let { millis ->
                profileUpdates["dateOfBirth"] = Timestamp(millis / 1000, ((millis % 1000) * 1_000_000).toInt())
            }
            // Include bio only if it's not blank (optional field)
            if (currentState.bio.isNotBlank()) {
                profileUpdates["bio"] = currentState.bio
            }

            Log.d(TAG, "Profile updates map: $profileUpdates")

            // Call the repository function to update the profile
            val result = authRepository.updateUserProfile(userId, profileUpdates)

            result.onSuccess {
                Log.d(TAG, "Profile details saved successfully for $userId.")
                _uiState.update { it.copy(isLoading = false, isSaveSuccess = true) }
            }.onFailure { exception ->
                Log.e(TAG, "Failed to save profile details for $userId", exception)
                _uiState.update { it.copy(
                    isLoading = false,
                    isSaveSuccess = false,
                    errorMessage = "Failed to save details: ${exception.message}"
                )}
            }
        }
    }

    // Function to reset the success flag after navigation
    fun resetSaveSuccessFlag() {
        if (_uiState.value.isSaveSuccess) {
            _uiState.update { it.copy(isSaveSuccess = false) }
        }
    }

    // Function to clear error message manually if needed
    fun clearError() {
        if (_uiState.value.errorMessage != null) {
            _uiState.update { it.copy(errorMessage = null) }
        }
    }
}

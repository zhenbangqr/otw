package com.zhenbang.otw.enterSelfDetails

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.zhenbang.otw.data.AuthRepository
import com.zhenbang.otw.data.FirebaseAuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EnterSelfDetailsViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository: AuthRepository = FirebaseAuthRepository()
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    private val TAG = "EnterSelfDetailsVM"

    private val _uiState = MutableStateFlow(SelfDetailsUiState())
    val uiState = _uiState.asStateFlow()

    fun updateTextField(field: String, value: String) {
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

    fun updateBirthdate(selectedMillis: Long?) {
        _uiState.update { it.copy(birthdateMillis = selectedMillis, showDatePicker = false) }
        if (_uiState.value.errorMessage != null) {
            _uiState.update { it.copy(errorMessage = null) }
        }
    }

    fun showDatePicker(show: Boolean) {
        _uiState.update { it.copy(showDatePicker = show) }
    }

    fun saveDetails() {
        val currentState = _uiState.value
        val userId = firebaseAuth.currentUser?.uid

        if (currentState.displayName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Display Name cannot be empty.") }
            return
        }
        if (currentState.phoneNumber.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Phone Number cannot be empty.") }
            return
        }
        if (!android.util.Patterns.PHONE.matcher(currentState.phoneNumber).matches()) {
            _uiState.update { it.copy(errorMessage = "Invalid phone number format.") }
            return
        }
        if (currentState.birthdateMillis == null) {
            _uiState.update { it.copy(errorMessage = "Please select your birthdate.") }
            return
        }

        if (userId == null) {
            _uiState.update { it.copy(errorMessage = "Error: Not logged in.", isLoading = false) }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    isSaveSuccess = false
                )
            }
            Log.d(TAG, "Attempting to save details for user: $userId")

            val profileUpdates = mutableMapOf<String, Any?>()
            profileUpdates["displayName"] = currentState.displayName
            profileUpdates["phoneNumber"] = currentState.phoneNumber
            currentState.birthdateMillis?.let { millis ->
                profileUpdates["dateOfBirth"] =
                    Timestamp(millis / 1000, ((millis % 1000) * 1_000_000).toInt())
            }
            if (currentState.bio.isNotBlank()) {
                profileUpdates["bio"] = currentState.bio
            }

            Log.d(TAG, "Profile updates map: $profileUpdates")

            val result = authRepository.updateUserProfile(userId, profileUpdates)

            result.onSuccess {
                Log.d(TAG, "Profile details saved successfully for $userId.")
                _uiState.update { it.copy(isLoading = false, isSaveSuccess = true) }
            }.onFailure { exception ->
                Log.e(TAG, "Failed to save profile details for $userId", exception)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isSaveSuccess = false,
                        errorMessage = "Failed to save details: ${exception.message}"
                    )
                }
            }
        }
    }

    fun resetSaveSuccessFlag() {
        if (_uiState.value.isSaveSuccess) {
            _uiState.update { it.copy(isSaveSuccess = false) }
        }
    }

    fun clearError() {
        if (_uiState.value.errorMessage != null) {
            _uiState.update { it.copy(errorMessage = null) }
        }
    }
}
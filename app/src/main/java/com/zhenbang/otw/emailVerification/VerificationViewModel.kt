package com.zhenbang.otw.emailVerification

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
// --- Import FirebaseAuth if needed to get current user ---
import com.google.firebase.auth.FirebaseAuth
// ----------------------------------------------------
import com.zhenbang.otw.data.AuthRepository // Use the interface
import com.zhenbang.otw.data.FirebaseAuthRepository // Assuming direct instantiation for now
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.lang.IllegalStateException


class VerificationViewModel(application: Application) : AndroidViewModel(application) {

    // Instantiate repository (Ideally use Dependency Injection)
    private val authRepository: AuthRepository = FirebaseAuthRepository()
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow<VerificationUiState>(VerificationUiState.Idle)
    val uiState = _uiState.asStateFlow()

    var isVerified by mutableStateOf<Boolean?>(null)
        private set

    /**
     * Calls the repository to resend the Firebase verification email link.
     */
    fun resendVerificationLink() {
        if (_uiState.value == VerificationUiState.Resending) return

        viewModelScope.launch {
            _uiState.value = VerificationUiState.Resending
            Log.d("VerificationViewModel", "Requesting resend verification link")

            val result = authRepository.resendVerificationLink()

            result.onSuccess {
                Log.d("VerificationViewModel", "Resend link successful")
                _uiState.value = VerificationUiState.Idle
            }.onFailure { exception ->
                Log.e("VerificationViewModel", "Resend link failed", exception)
                val errorMsg = mapVerificationError(exception)
                _uiState.value = VerificationUiState.Error(errorMsg)
            }
        }
    }

    /**
     * Calls the repository to check the current user's email verification status.
     * If verified, triggers saving user data to the database.
     */
    fun checkVerificationStatus() {
        if (_uiState.value == VerificationUiState.Checking) return
        isVerified = null

        viewModelScope.launch {
            _uiState.value = VerificationUiState.Checking
            Log.d("VerificationViewModel", "Checking verification status")

            val checkResult = authRepository.checkCurrentUserVerificationStatus()

            checkResult.onSuccess { verifiedStatus ->
                Log.d("VerificationViewModel", "Verification status checked: $verifiedStatus")

                if (verifiedStatus) {
                    isVerified = true // Update state for UI effect/navigation

                    // Get current user info needed for saving
                    val currentUser = firebaseAuth.currentUser
                    val userId = currentUser?.uid
                    val userEmail = currentUser?.email

                    if (userId != null && userEmail != null) {
                        Log.d("VerificationViewModel", "User verified, attempting to save data for $userId")
                        val saveResult = authRepository.saveUserDataAfterVerification(userId, userEmail)
                        saveResult.onSuccess {
                            Log.d("VerificationViewModel", "User data saved successfully after verification.")
                        }.onFailure { saveError ->
                            Log.e("VerificationViewModel", "Failed to save user data after verification", saveError)
                            _uiState.value = VerificationUiState.Error("Email verified, but failed to save profile data. Please try logging in.")
                            isVerified = false
                        }
                    } else {
                        Log.e("VerificationViewModel", "User is verified but user ID or email is null. Cannot save data.")
                        _uiState.value = VerificationUiState.Error("Verification check error: User info missing.")
                        isVerified = false
                    }
                } else {
                    // --- Verification FAILED ---
                    isVerified = false
                }

            }.onFailure { exception ->
                // --- Verification Check FAILED (Error during check) ---
                Log.e("VerificationViewModel", "Checking status failed", exception)
                val errorMsg = mapVerificationError(exception)
                _uiState.value = VerificationUiState.Error(errorMsg)
                isVerified = false // Indicate check failed
            }

            // Reset UI state back to Idle *unless* an error occurred during check/save
            if (_uiState.value !is VerificationUiState.Error) {
                _uiState.value = VerificationUiState.Idle
            }
        }
    }

    /**
     * Resets the isVerified state, typically after the UI has reacted to it.
     */
    fun resetVerificationStatus() {
        isVerified = null
    }

    /**
     * Resets the UI state from Error back to Idle.
     */
    fun clearVerificationError() {
        if (_uiState.value is VerificationUiState.Error) {
            _uiState.value = VerificationUiState.Idle
        }
    }

    /**
     * Resets the entire UI state back to Idle. Useful if navigating away.
     */
    fun resetVerificationStateToIdle(){
        _uiState.value = VerificationUiState.Idle
    }


    /**
     * Maps common exceptions from verification operations to user-friendly messages.
     */
    private fun mapVerificationError(exception: Throwable): String {
        return when (exception) {
            is IOException -> "Network error. Please check connection."
            is IllegalStateException -> exception.message ?: "Cannot perform action now. Are you signed in?" // Often happens if currentUser is null
            else -> "Error: ${exception.message ?: "An unknown error occurred."}"
        }
    }
}
    
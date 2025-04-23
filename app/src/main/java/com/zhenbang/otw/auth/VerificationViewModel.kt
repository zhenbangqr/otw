package com.zhenbang.otw.auth

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zhenbang.otw.data.AuthRepository // Use the interface
import com.zhenbang.otw.data.FirebaseAuthRepository // Assuming direct instantiation for now
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.lang.IllegalStateException

/**
 * ViewModel for the Verification Screen.
 * Handles resending verification links and checking verification status.
 */
class VerificationViewModel(application: Application) : AndroidViewModel(application) {

    // Instantiate repository (Ideally use Dependency Injection - Hilt, Koin)
    private val authRepository: AuthRepository = FirebaseAuthRepository()

    // --- UI State Flow ---
    private val _uiState = MutableStateFlow<VerificationUiState>(VerificationUiState.Idle)
    val uiState = _uiState.asStateFlow()

    // --- Verification Status State ---
    // Observed by the VerificationScreen's LaunchedEffect to trigger navigation
    var isVerified by mutableStateOf<Boolean?>(null)
        private set // Can be read publicly, only settable internally

    /**
     * Calls the repository to resend the Firebase verification email link.
     */
    fun resendVerificationLink() {
        // Prevent concurrent resend requests
        if (_uiState.value == VerificationUiState.Resending) return

        viewModelScope.launch {
            _uiState.value = VerificationUiState.Resending
            Log.d("VerificationViewModel", "Requesting resend verification link")

            // Assuming resendVerificationLink is in FirebaseAuthRepository implementation
            // It's better if it's defined in the AuthRepository interface
            val result = (authRepository as? FirebaseAuthRepository)?.resendVerificationLink()
                ?: Result.failure(IllegalStateException("Repository method not available")) // Handle if cast fails or method missing

            result.onSuccess {
                Log.d("VerificationViewModel", "Resend link successful")
                _uiState.value = VerificationUiState.Idle // Return to Idle after success
                // Optionally emit a temporary success event/state for Snackbar feedback
            }.onFailure { exception ->
                Log.e("VerificationViewModel", "Resend link failed", exception)
                val errorMsg = mapVerificationError(exception)
                _uiState.value = VerificationUiState.Error(errorMsg)
            }
        }
    }

    /**
     * Calls the repository to check the current user's email verification status.
     * Requires the repository method to reload the user state first.
     */
    fun checkVerificationStatus() {
        // Prevent concurrent checks
        if (_uiState.value == VerificationUiState.Checking) return
        isVerified = null // Reset check status before starting

        viewModelScope.launch {
            _uiState.value = VerificationUiState.Checking
            Log.d("VerificationViewModel", "Checking verification status")

            // Assuming checkCurrentUserVerificationStatus is in FirebaseAuthRepository
            // It should ideally be in the AuthRepository interface
            val result = (authRepository as? FirebaseAuthRepository)?.checkCurrentUserVerificationStatus()
                ?: Result.failure(IllegalStateException("Repository method not available")) // Handle if cast fails or method missing

            result.onSuccess { verifiedStatus ->
                Log.d("VerificationViewModel", "Verification status checked: $verifiedStatus")
                isVerified = verifiedStatus // Update the observable state for the UI effect
                // UI state will reset below if no error occurred
            }.onFailure { exception ->
                Log.e("VerificationViewModel", "Checking status failed", exception)
                val errorMsg = mapVerificationError(exception)
                _uiState.value = VerificationUiState.Error(errorMsg)
                isVerified = false // Indicate check failed or user is definitely not verified
            }

            // Reset UI state back to Idle *unless* an error occurred (error state persists)
            // The `isVerified` state change will trigger navigation in the UI's LaunchedEffect
            if(_uiState.value !is VerificationUiState.Error){
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
        // You can add more specific Firebase exception checks here if needed
        return when (exception) {
            is IOException -> "Network error. Please check connection."
            is IllegalStateException -> exception.message ?: "Cannot perform action now. Are you signed in?" // Often happens if currentUser is null
            else -> "Error: ${exception.message ?: "An unknown error occurred."}"
        }
    }
}
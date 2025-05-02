package com.zhenbang.otw.register

import android.app.Application
import android.util.Log
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.zhenbang.otw.data.AuthRepository
import com.zhenbang.otw.data.FirebaseAuthRepository // Assuming direct instantiation for now
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException

class RegisterViewModel(application: Application) : AndroidViewModel(application) {
    private val authRepository: AuthRepository = FirebaseAuthRepository()

    private val _uiState = MutableStateFlow<RegisterUiState>(RegisterUiState.Idle)
    val uiState = _uiState.asStateFlow()

    /**
     * Initiates the user registration process via the repository.
     * Creates user and sends verification link.
     * Updates the UI state based on the outcome.
     */
    fun registerUser(email: String, password: String) {
        if (_uiState.value == RegisterUiState.Registering) return

        if (!isValidEmailFormat(email)) {
            _uiState.value = RegisterUiState.Error("Please enter a valid email address.")
            return
        }
        if (password.length < 6) {
            _uiState.value = RegisterUiState.Error("Password must be at least 6 characters.")
            return
        }

        viewModelScope.launch {
            _uiState.value = RegisterUiState.Registering
            Log.d("RegisterViewModel", "Attempting registration for $email via repository")
            try {
                val result = authRepository.createUserAndSendVerificationLink(email, password)

                result.onSuccess {
                    Log.d("RegisterViewModel", "Registration successful for $email, verification link sent.")
                    // Update state to indicate success and need for verification
                    _uiState.value = RegisterUiState.AccountCreatedPendingVerification
                }.onFailure { exception ->
                    Log.e("RegisterViewModel", "registerUser failed", exception)
                    // Map the exception from the repository to a user-friendly message
                    val errorMessage = mapErrorToMessage(exception, "Registration Failed")
                    _uiState.value = RegisterUiState.Error(errorMessage)
                }

            } catch (e: Exception) {
                // Catch any unexpected errors during the coroutine execution
                Log.e("RegisterViewModel", "Unexpected error in registerUser", e)
                _uiState.value = RegisterUiState.Error("An unexpected registration error occurred.")
            }
        }
    }

    private fun isValidEmailFormat(email: String): Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun mapErrorToMessage(exception: Throwable, defaultPrefix: String): String {
        // Adjust based on exceptions thrown by FirebaseAuthRepository
        return when (exception) {
            is IOException -> "Network error. Please check connection and try again."
            is IllegalArgumentException -> exception.message ?: "Invalid data provided." // e.g., Email exists, weak password
            is IllegalStateException -> exception.message ?: "Operation cannot be completed." // e.g., User null
            is FirebaseAuthUserCollisionException -> "Email address is already in use." // Caught in Repo now
            is FirebaseAuthWeakPasswordException -> "Password is too weak." // Caught in Repo now
            else -> "$defaultPrefix: ${exception.message ?: "Unknown error"}"
        }
    }

    fun clearErrorState() {
        if (_uiState.value is RegisterUiState.Error) {
            _uiState.value = RegisterUiState.Idle
        }
    }

    fun resetState() {
        _uiState.value = RegisterUiState.Idle
    }
}
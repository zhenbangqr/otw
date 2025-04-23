package com.zhenbang.otw.auth

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zhenbang.otw.data.AuthRepository
import com.zhenbang.otw.data.FirebaseAuthRepository // Assuming direct instantiation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.lang.IllegalArgumentException // For specific login errors

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository: AuthRepository = FirebaseAuthRepository() // Or inject

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState = _uiState.asStateFlow()

    fun signInUser(email: String, password: String) {
        if (_uiState.value == LoginUiState.Loading) return

        // Basic validation
        if (!isValidEmailFormat(email) || password.isBlank()) {
            _uiState.value = LoginUiState.Error("Please enter a valid email and password.")
            return
        }

        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            Log.d("LoginViewModel", "Attempting login for $email")

            // 1. Attempt Sign In using Repository (add this method to repo)
            val signInResult = (authRepository as FirebaseAuthRepository).signInWithEmailAndPassword(email, password) // Cast or add to interface

            signInResult.onSuccess {
                Log.d("LoginViewModel", "Sign in successful for $email. Checking verification...")
                // 2. Immediately check verification status
                val verificationResult = (authRepository as FirebaseAuthRepository).checkCurrentUserVerificationStatus() // Cast or add to interface

                verificationResult.onSuccess { isVerified ->
                    if (isVerified) {
                        Log.d("LoginViewModel", "Email is verified for $email.")
                        _uiState.value = LoginUiState.LoginSuccess
                    } else {
                        Log.d("LoginViewModel", "Email NOT verified for $email.")
                        _uiState.value = LoginUiState.VerificationNeeded(email)
                    }
                }.onFailure { verificationError ->
                    Log.e("LoginViewModel", "Failed to check verification status", verificationError)
                    // Treat as error, user might need to try login again
                    _uiState.value = LoginUiState.Error("Login successful, but failed to check verification status. Please try again.")
                }

            }.onFailure { signInException ->
                Log.e("LoginViewModel", "Sign in failed", signInException)
                val errorMsg = mapLoginError(signInException)
                _uiState.value = LoginUiState.Error(errorMsg)
            }
        }
    }

    // --- Helper Functions ---
    private fun isValidEmailFormat(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun mapLoginError(exception: Throwable): String {
        return when (exception) {
            is IOException -> "Network error. Please check connection."
            // Add specific Firebase Auth sign-in exceptions if needed (e.g., wrong password, user not found)
            is IllegalArgumentException -> exception.message ?: "Invalid credentials."
            else -> "Login failed: ${exception.message ?: "Unknown error"}"
        }
    }

    fun clearErrorState() {
        if (_uiState.value is LoginUiState.Error) {
            _uiState.value = LoginUiState.Idle
        }
    }

    fun resetState() {
        _uiState.value = LoginUiState.Idle
    }
}
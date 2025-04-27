package com.zhenbang.otw.login

import android.app.Application
import android.util.Log
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
// --- Add FirebaseAuth import ---
import com.google.firebase.auth.FirebaseAuth
// -----------------------------
import com.zhenbang.otw.data.AuthRepository
import com.zhenbang.otw.data.FirebaseAuthRepository // Assuming direct instantiation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.lang.IllegalArgumentException

// --- Update LoginUiState definition (ensure it's defined correctly, maybe in its own file) ---
/*
sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    object LoginVerifiedSuccess : LoginUiState() // State for verified success
    data class VerificationNeeded(val email: String) : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}
*/
// ---------------------------------------------------------------------------------------------

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository: AuthRepository = FirebaseAuthRepository() // Or inject
    // --- Add FirebaseAuth instance ---
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    // -----------------------------

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState = _uiState.asStateFlow()

    fun signInUser(email: String, password: String) {
        if (_uiState.value == LoginUiState.Loading) return

        if (!isValidEmailFormat(email) || password.isBlank()) {
            _uiState.value = LoginUiState.Error("Please enter a valid email and password.")
            return
        }

        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            Log.d("LoginViewModel", "Attempting login for $email")

            val signInResult = authRepository.signInWithEmailAndPassword(email, password) // Use interface method

            signInResult.onSuccess {
                Log.d("LoginViewModel", "Sign in successful for $email. Checking verification...")
                val verificationResult = authRepository.checkCurrentUserVerificationStatus() // Use interface method

                verificationResult.onSuccess { isVerified ->
                    if (isVerified) {
                        Log.d("LoginViewModel", "Email is verified for $email.")
                        // Don't set state directly yet, call handler function first
                        handleEmailPasswordLoginSuccess() // Call function to save data
                    } else {
                        Log.d("LoginViewModel", "Email NOT verified for $email.")
                        _uiState.value = LoginUiState.VerificationNeeded(email)
                    }
                }.onFailure { verificationError ->
                    Log.e("LoginViewModel", "Failed to check verification status", verificationError)
                    _uiState.value = LoginUiState.Error("Login successful, but failed to check verification status. Please try again.")
                }

            }.onFailure { signInException ->
                Log.e("LoginViewModel", "Sign in failed", signInException)
                val errorMsg = mapLoginError(signInException)
                _uiState.value = LoginUiState.Error(errorMsg)
            }
        }
    }

    // --- NEW FUNCTION for handling Email/Password Success ---
    fun handleEmailPasswordLoginSuccess() {
        viewModelScope.launch {
            val currentUser = firebaseAuth.currentUser
            if (currentUser != null) {
                Log.d("LoginViewModel", "Email/Pass login success, saving/updating info for ${currentUser.uid}")
                val saveResult = authRepository.saveOrUpdateUserLoginInfo(currentUser)
                saveResult.onSuccess {
                    Log.d("LoginViewModel", "User info saved/updated successfully.")
                    // Now set the final success state for navigation
                    _uiState.value = LoginUiState.LoginVerifiedSuccess
                }.onFailure { saveError ->
                    Log.e("LoginViewModel", "Failed to save/update user info after login", saveError)
                    // Decide how to handle: proceed with login but show error? Or block login?
                    // Option 1: Proceed but show error (might need another state)
                    _uiState.value = LoginUiState.Error("Login successful, but failed to save profile data.")
                    // Option 2: Treat as login failure
                    // _uiState.value = LoginUiState.Error("Login failed during profile save.")
                }
            } else {
                // Should not happen if login succeeded, but handle defensively
                Log.e("LoginViewModel", "Email/Pass login success but currentUser is null!")
                _uiState.value = LoginUiState.Error("Login failed: Could not retrieve user details.")
            }
        }
    }
    // ---------------------------------------------------

    // --- NEW FUNCTION for handling Google Sign-In Success ---
    fun handleGoogleSignInSuccess(idToken: String) {
        if (_uiState.value == LoginUiState.Loading) return // Avoid concurrent processing

        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading // Show loading indicator
            Log.d("LoginViewModel", "Handling Google Sign-In Success, attempting Firebase link...")

            // 1. Link Google Credential to Firebase
            val linkResult = authRepository.linkGoogleCredentialToFirebase(idToken)

            linkResult.onSuccess { firebaseUser ->
                Log.d("LoginViewModel", "Firebase link successful: ${firebaseUser.uid}. Saving/updating info...")

                // 2. Save/Update User Info in Firestore
                val saveResult = authRepository.saveOrUpdateUserLoginInfo(firebaseUser)
                saveResult.onSuccess {
                    Log.d("LoginViewModel", "Google user info saved/updated successfully.")
                    // Set final success state for navigation
                    _uiState.value = LoginUiState.LoginVerifiedSuccess
                }.onFailure { saveError ->
                    Log.e("LoginViewModel", "Failed to save/update Google user info after link", saveError)
                    // Decide how to handle: proceed with login but show error? Or block login?
                    _uiState.value = LoginUiState.Error("Google Sign-In successful, but failed to save profile data.")
                }

            }.onFailure { linkError ->
                Log.e("LoginViewModel", "Firebase link failed", linkError)
                // Map specific errors if needed (e.g., collision)
                _uiState.value = LoginUiState.Error("Failed to link Google Sign-In: ${linkError.message}")
                // Ensure AppAuth state is also cleared if link fails? Maybe AuthViewModel handles this.
            }
        }
    }
    // ------------------------------------------------------

    // --- Helper Functions ---
    private fun isValidEmailFormat(email: String): Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun mapLoginError(exception: Throwable): String {
        return when (exception) {
            is IOException -> "Network error. Please check connection."
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

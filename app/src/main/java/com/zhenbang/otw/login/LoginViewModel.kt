package com.zhenbang.otw.login

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser // Import FirebaseUser
import com.zhenbang.otw.data.AuthRepository
import com.zhenbang.otw.data.FirebaseAuthRepository // Assuming direct instantiation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.lang.IllegalArgumentException


class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository: AuthRepository = FirebaseAuthRepository()
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    private val TAG = "LoginViewModel"

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
            Log.d(TAG, "Attempting login for $email")

            val signInResult = authRepository.signInWithEmailAndPassword(email, password)

            signInResult.onSuccess {
                Log.d(TAG, "Sign in successful for $email. Checking verification...")
                val verificationResult = authRepository.checkCurrentUserVerificationStatus()

                verificationResult.onSuccess { isVerified ->
                    if (isVerified) {
                        Log.d(TAG, "Email is verified for $email.")
                        handleEmailPasswordLoginSuccess() // Call function to save data
                    } else {
                        Log.d(TAG, "Email NOT verified for $email.")
                        _uiState.value = LoginUiState.VerificationNeeded(email)
                    }
                }.onFailure { verificationError ->
                    Log.e(TAG, "Failed to check verification status", verificationError)
                    _uiState.value = LoginUiState.Error("Login successful, but failed to check verification status. Please try again.")
                }

            }.onFailure { signInException ->
                Log.e(TAG, "Sign in failed", signInException)
                val errorMsg = mapLoginError(signInException)
                _uiState.value = LoginUiState.Error(errorMsg)
            }
        }
    }

    // Handles success AFTER Email/Password login AND verification check
    fun handleEmailPasswordLoginSuccess() {
        viewModelScope.launch {
            val currentUser = firebaseAuth.currentUser
            if (currentUser != null) {
                Log.d(TAG, "Email/Pass login success, saving/updating login info for ${currentUser.uid}")
                val saveLoginResult = authRepository.saveOrUpdateUserLoginInfo(currentUser)
                saveLoginResult.onSuccess {
                    Log.d(TAG, "User login info saved/updated successfully.")
                    // Now set the final success state for navigation
                    _uiState.value = LoginUiState.LoginVerifiedSuccess
                }.onFailure { saveError ->
                    Log.e(TAG, "Failed to save/update user login info after login", saveError)
                    _uiState.value = LoginUiState.Error("Login successful, but failed to save profile data.")
                }
            } else {
                Log.e(TAG, "Email/Pass login success but currentUser is null!")
                _uiState.value = LoginUiState.Error("Login failed: Could not retrieve user details.")
            }
        }
    }

    // Handles success AFTER Google Sign-In completes in AuthViewModel
    fun handleGoogleSignInSuccess(idToken: String) {
        if (_uiState.value == LoginUiState.Loading) return

        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            Log.d(TAG, "Handling Google Sign-In Success, attempting Firebase link...")

            // 1. Link Google Credential to Firebase
            val linkResult = authRepository.linkGoogleCredentialToFirebase(idToken)

            linkResult.onSuccess { firebaseUser ->
                Log.d(TAG, "Firebase link successful: ${firebaseUser.uid}. Saving/updating login info...")

                // 2. Save basic login info (email, uid, lastLoginAt) - NO photoURL here
                val saveLoginResult = authRepository.saveOrUpdateUserLoginInfo(firebaseUser)

                saveLoginResult.onSuccess {
                    Log.d(TAG, "Google user login info saved/updated successfully.")

                    // *** 3. Check if Firestore profile exists, create if not OR update photo if missing ***
                    checkAndCreateOrUpdateGoogleProfile(firebaseUser) // Pass the linked user

                }.onFailure { saveLoginError ->
                    Log.e(TAG, "Failed to save/update Google user login info", saveLoginError)
                    _uiState.value = LoginUiState.Error("Google Sign-In successful, but failed to save basic profile data.")
                }

            }.onFailure { linkError ->
                Log.e(TAG, "Firebase link failed", linkError)
                _uiState.value = LoginUiState.Error("Failed to link Google Sign-In: ${linkError.message}")
            }
        }
    }

    // --- Helper function to check/create initial Google profile OR update missing photo ---
    private suspend fun checkAndCreateOrUpdateGoogleProfile(firebaseUser: FirebaseUser) {
        val userId = firebaseUser.uid
        val userEmail = firebaseUser.email ?: ""
        val photoUrl = firebaseUser.photoUrl?.toString()
        val displayName = firebaseUser.displayName

        Log.d(TAG, "Checking profile for Google user: $userId")
        val profileResult = authRepository.getUserProfile(userId)

        profileResult.onSuccess { existingProfile ->
            if (existingProfile == null) {
                Log.d(TAG, "Profile for $userId does not exist. Creating initial profile.")
                val initialProfileData = mutableMapOf<String, Any?>(
                    "uid" to userId,
                    "email" to userEmail,
                    "createdAt" to com.google.firebase.Timestamp.now()
                )
                if (photoUrl != null) {
                    initialProfileData["profileImageUrl"] = photoUrl
                    Log.d(TAG,"Adding initial profileImageUrl from Google: $photoUrl")
                }
                if (displayName?.isNotBlank() == true) {
                    initialProfileData["displayName"] = displayName
                    Log.d(TAG,"Adding initial displayName from Google: $displayName")
                }

                val createResult = authRepository.updateUserProfile(userId, initialProfileData)
                createResult.onSuccess {
                    Log.d(TAG, "Initial Google profile created successfully for $userId.")
                    _uiState.value = LoginUiState.LoginVerifiedSuccess
                }.onFailure { createError ->
                    Log.e(TAG, "Failed to create initial Google profile for $userId", createError)
                    _uiState.value = LoginUiState.Error("Google Sign-In successful, but failed to create profile.")
                }

            } else {
                Log.d(TAG, "Profile for $userId already exists.")
                if (existingProfile.profileImageUrl.isNullOrBlank() && photoUrl != null) {
                    Log.d(TAG,"Existing profile missing image. Updating with Google photo: $photoUrl")
                    // Call repository to update ONLY the image field
                    val updateResult = authRepository.updateUserProfile(userId, mapOf("profileImageUrl" to photoUrl))
                    updateResult.onSuccess {
                        Log.d(TAG, "Successfully added Google photo to existing profile.")
                        _uiState.value = LoginUiState.LoginVerifiedSuccess // Proceed after update
                    }.onFailure { updateError ->
                        Log.e(TAG, "Failed to add Google photo to existing profile", updateError)
                        _uiState.value = LoginUiState.Error("Login successful, but failed to update profile picture.")
                    }
                } else {
                    // Profile exists and either already has an image or Google doesn't have one
                    Log.d(TAG, "Existing profile has image or Google has no photo. Proceeding.")
                    _uiState.value = LoginUiState.LoginVerifiedSuccess // Proceed directly
                }
            }
        }.onFailure { fetchError ->
            Log.e(TAG, "Failed to check for existing profile for $userId", fetchError)
            _uiState.value = LoginUiState.Error("Google Sign-In successful, but failed check profile existence.")
        }
    }

    private fun isValidEmailFormat(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
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

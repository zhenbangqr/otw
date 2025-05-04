package com.zhenbang.otw.login

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.Timestamp // Import Timestamp if used in profile data map
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.messaging.FirebaseMessaging // For FCM token
import com.zhenbang.otw.data.AuthRepository
import com.zhenbang.otw.data.FirebaseAuthRepository // Assuming direct instantiation for now
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await // For awaiting token task
import java.io.IOException
import java.lang.IllegalArgumentException


class LoginViewModel(application: Application) : AndroidViewModel(application) {

    // Use dependency injection ideally, but direct instantiation for now
    private val authRepository: AuthRepository = FirebaseAuthRepository()
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    private val TAG = "LoginViewModel"

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState = _uiState.asStateFlow()

    private val _feedbackMessage = MutableSharedFlow<String>()
    val feedbackMessage: SharedFlow<String> = _feedbackMessage.asSharedFlow()

    private val _isResettingPassword = MutableStateFlow(false)
    val isResettingPassword: StateFlow<Boolean> = _isResettingPassword.asStateFlow()


    // --- Email/Password Sign In ---
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
                        // Call success handler (which now includes FCM token save)
                        handleEmailPasswordLoginSuccess()
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

    // --- NEW: Function to send password reset email ---
    fun sendPasswordReset(email: String) {
        // Basic validation
        if (!isValidEmailFormat(email)) {
            _uiState.value = LoginUiState.Error("Please enter a valid email address.")
            return
        }
        if (_isResettingPassword.value) return // Prevent multiple requests

        viewModelScope.launch {
            _isResettingPassword.value = true
            clearErrorState() // Clear previous general errors
            Log.d(TAG, "Attempting password reset for: $email")

            try {
                // 1. Check if user exists by fetching sign-in methods
                val queryResult: com.google.firebase.auth.SignInMethodQueryResult = firebaseAuth.fetchSignInMethodsForEmail(email).await()

                // --- FIX IS HERE: Check queryResult.signInMethods ---
                if (queryResult.signInMethods.isNullOrEmpty()) {
                    // ---------------------------------------------------
                    // Email does not exist or has no sign-in methods linked
                    Log.w(TAG, "Password reset requested for non-existent or unlinked email: $email")
                    firebaseAuth.sendPasswordResetEmail(email).await()
                    // Send a neutral message to avoid confirming if an email exists or not
                    _feedbackMessage.emit("If an account exists for $email, a password reset link has been sent.")
                } else {
                    // 2. User likely exists, send the reset email
                    Log.d(TAG, "Account likely exists for $email (methods: ${queryResult.signInMethods}). Sending reset email.")
                    firebaseAuth.sendPasswordResetEmail(email).await()
                    Log.d(TAG, "Password reset email send attempt complete for: $email")
                    // Send confirmation regardless of actual send success for security (prevents email enumeration)
                    _feedbackMessage.emit("If an account exists for $email, a password reset link has been sent. Check your inbox (and spam folder).")
                }
            } catch (e: FirebaseAuthInvalidUserException) {
                // This specific exception might indicate the user is disabled or truly not found,
                // but we still give the neutral message for security.
                Log.w(TAG, "Password reset failed (InvalidUser): $email", e)
                _feedbackMessage.emit("If an account exists for $email, a password reset link has been sent.")
            } catch (e: Exception) {
                // Handle other errors (network, service unavailable, etc.)
                Log.e(TAG, "Password reset failed for: $email", e)
                // Show a more generic error for other failures
                _uiState.value = LoginUiState.Error("Failed to send reset email: ${e.message ?: "Please try again later"}")
            } finally {
                _isResettingPassword.value = false // Reset loading state regardless of outcome
            }
        }
    }

    // --- Handles success AFTER Email/Password login AND verification check ---
    private fun handleEmailPasswordLoginSuccess() {
        viewModelScope.launch {
            val currentUser = firebaseAuth.currentUser
            if (currentUser != null) {
                Log.d(TAG, "Email/Pass login success, saving/updating login info for ${currentUser.uid}")
                val saveLoginResult = authRepository.saveOrUpdateUserLoginInfo(currentUser)

                saveLoginResult.onSuccess {
                    Log.d(TAG, "User login info saved/updated successfully.")
                    // --- Get and Save FCM Token ---
                    getAndSaveFcmToken(currentUser.uid)
                    // -----------------------------
                    // Set final success state AFTER attempting token save
                    _uiState.value = LoginUiState.LoginVerifiedSuccess
                }.onFailure { saveError ->
                    Log.e(TAG, "Failed to save/update user login info after login", saveError)
                    // Still set success state? Or show error? Let's show error for now.
                    _uiState.value = LoginUiState.Error("Login successful, but failed to save profile data.")
                }
            } else {
                Log.e(TAG, "Email/Pass login success but currentUser is null!")
                _uiState.value = LoginUiState.Error("Login failed: Could not retrieve user details.")
            }
        }
    }

    // --- Handles success AFTER Google Sign-In completes in AuthViewModel ---
    fun handleGoogleSignInSuccess(idToken: String) {
        if (_uiState.value == LoginUiState.Loading) return

        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            Log.d(TAG, "Handling Google Sign-In Success, attempting Firebase link...")

            // 1. Link Google Credential to Firebase
            val linkResult = authRepository.linkGoogleCredentialToFirebase(idToken)

            linkResult.onSuccess { firebaseUser ->
                Log.d(TAG, "Firebase link successful: ${firebaseUser.uid}. Saving/updating login info...")

                // 2. Save basic login info (email, uid, lastLoginAt)
                val saveLoginResult = authRepository.saveOrUpdateUserLoginInfo(firebaseUser)

                saveLoginResult.onSuccess {
                    Log.d(TAG, "Google user login info saved/updated successfully.")
                    // 3. Check/create profile AND save token
                    checkCreateProfileAndSaveToken(firebaseUser)
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

    // --- Helper function for Google flow: Check/Create Profile and Save Token ---
    private suspend fun checkCreateProfileAndSaveToken(firebaseUser: FirebaseUser) {
        val userId = firebaseUser.uid
        val userEmail = firebaseUser.email ?: ""
        val photoUrl = firebaseUser.photoUrl?.toString()
        val displayName = firebaseUser.displayName

        Log.d(TAG, "Checking profile for Google user: $userId")
        val profileResult = authRepository.getUserProfile(userId)

        profileResult.onSuccess { existingProfile ->
            // Logic to create initial profile or update existing one
            val profileUpdateOrCreationSuccess: Boolean = try {
                if (existingProfile == null) {
                    Log.d(TAG, "Profile for $userId does not exist. Creating initial profile.")
                    val initialProfileData = mutableMapOf<String, Any?>(
                        "uid" to userId,
                        "email" to userEmail,
                        "createdAt" to Timestamp.now() // Use Firebase Timestamp
                    )
                    if (photoUrl != null) { initialProfileData["profileImageUrl"] = photoUrl }
                    if (displayName?.isNotBlank() == true) { initialProfileData["displayName"] = displayName }

                    val createResult = authRepository.updateUserProfile(userId, initialProfileData)
                    if(createResult.isFailure) Log.e(TAG, "Failed to create initial profile", createResult.exceptionOrNull())
                    createResult.isSuccess // Return true if creation succeeded

                } else {
                    Log.d(TAG, "Profile for $userId already exists.")
                    // Optionally update existing profile (e.g., missing photo)
                    if (existingProfile.profileImageUrl.isNullOrBlank() && photoUrl != null) {
                        Log.d(TAG,"Existing profile missing image. Updating with Google photo: $photoUrl")
                        val updateResult = authRepository.updateUserProfile(userId, mapOf("profileImageUrl" to photoUrl))
                        if(updateResult.isFailure) Log.e(TAG, "Failed to add Google photo", updateResult.exceptionOrNull())
                        updateResult.isSuccess // Return true if update succeeded or wasn't needed
                    } else {
                        true // Profile exists and doesn't need photo update
                    }
                }
            } catch(e: Exception) {
                Log.e(TAG, "Error during profile check/create/update", e)
                false // Indicate failure
            }

            // Proceed only if profile handling was successful
            if (profileUpdateOrCreationSuccess) {
                Log.d(TAG, "Profile check/create/update successful for $userId.")
                // --- Get and Save FCM Token ---
                getAndSaveFcmToken(userId)
                // -----------------------------
                // Set final success state AFTER attempting token save
                _uiState.value = LoginUiState.LoginVerifiedSuccess
            } else {
                Log.e(TAG, "Failed to create/update profile for $userId")
                // Set error state
                _uiState.value = LoginUiState.Error("Google Sign-In successful, but failed to handle profile data.")
            }

        }.onFailure { fetchError ->
            Log.e(TAG, "Failed to check for existing profile for $userId", fetchError)
            _uiState.value = LoginUiState.Error("Google Sign-In successful, but failed check profile existence.")
        }
    }

    // --- NEW Helper Function to Get and Save FCM Token ---
    private suspend fun getAndSaveFcmToken(userId: String) {
        try {
            Log.d(TAG, "Attempting to retrieve FCM token...")
            // Get the FCM token using await() for coroutine compatibility
            val fcmToken = FirebaseMessaging.getInstance().token.await()
            Log.d(TAG, "FCM Token retrieved.")

            if (fcmToken.isNotEmpty()) {
                // Save token via repository
                Log.d(TAG, "Saving FCM token to Firestore for user $userId.")
                val saveTokenResult = authRepository.saveFcmToken(userId, fcmToken)
                if (saveTokenResult.isFailure) {
                    // Log error, but don't block login flow
                    Log.e(TAG, "Failed to save FCM token to Firestore.", saveTokenResult.exceptionOrNull())
                    // Optionally update UI state with a non-critical error?
                    // _uiState.update { it.copy(error = "Could not update notification token.")}
                } else {
                    Log.d(TAG, "FCM token save successful.")
                }
            } else {
                Log.w(TAG, "Retrieved FCM token is empty. Cannot save.")
            }
        } catch (e: Exception) {
            // Handle errors getting the token (e.g., network issues, Play Services missing)
            Log.e(TAG, "Failed to retrieve FCM token", e)
            // Don't block login flow for this error
            // Optionally update UI state with a non-critical error?
            // _uiState.update { it.copy(error = "Could not get notification token.")}
        }
    }
    // --- End FCM Helper ---


    // --- Utility Functions ---
    private fun isValidEmailFormat(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun mapLoginError(exception: Throwable): String {
        return when (exception) {
            is IOException -> "Network error. Please check connection."
            is IllegalArgumentException -> exception.message ?: "Invalid credentials." // Often used by Repo for specific errors
            // Add specific Firebase Auth exceptions if needed, though repo might map them already
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
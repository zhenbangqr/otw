package com.zhenbang.otw.login

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.messaging.FirebaseMessaging
import com.zhenbang.otw.data.AuthRepository
import com.zhenbang.otw.data.FirebaseAuthRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.IOException
import java.lang.IllegalArgumentException

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository: AuthRepository = FirebaseAuthRepository()
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    private val TAG = "LoginViewModel"

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState = _uiState.asStateFlow()

    private val _feedbackMessage = MutableSharedFlow<String>()
    val feedbackMessage: SharedFlow<String> = _feedbackMessage.asSharedFlow()

    private val _isResettingPassword = MutableStateFlow(false)
    val isResettingPassword: StateFlow<Boolean> = _isResettingPassword.asStateFlow()

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
                        handleEmailPasswordLoginSuccess()
                    } else {
                        Log.d(TAG, "Email NOT verified for $email.")
                        _uiState.value = LoginUiState.VerificationNeeded(email)
                    }
                }.onFailure { verificationError ->
                    Log.e(TAG, "Failed to check verification status", verificationError)
                    _uiState.value =
                        LoginUiState.Error("Login successful, but failed to check verification status. Please try again.")
                }

            }.onFailure { signInException ->
                Log.e(TAG, "Sign in failed", signInException)
                val errorMsg = mapLoginError(signInException)
                _uiState.value = LoginUiState.Error(errorMsg)
            }
        }
    }

    fun sendPasswordReset(email: String) {
        if (!isValidEmailFormat(email)) {
            _uiState.value = LoginUiState.Error("Please enter a valid email address.")
            return
        }
        if (_isResettingPassword.value) return

        viewModelScope.launch {
            _isResettingPassword.value = true
            clearErrorState()
            Log.d(TAG, "Attempting password reset for: $email")

            try {
                val queryResult: com.google.firebase.auth.SignInMethodQueryResult =
                    firebaseAuth.fetchSignInMethodsForEmail(email).await()

                if (queryResult.signInMethods.isNullOrEmpty()) {
                    Log.w(
                        TAG,
                        "Password reset requested for non-existent or unlinked email: $email"
                    )
                    firebaseAuth.sendPasswordResetEmail(email).await()
                    _feedbackMessage.emit("If an account exists for $email, a password reset link has been sent.")
                } else {
                    Log.d(
                        TAG,
                        "Account likely exists for $email (methods: ${queryResult.signInMethods}). Sending reset email."
                    )
                    firebaseAuth.sendPasswordResetEmail(email).await()
                    Log.d(TAG, "Password reset email send attempt complete for: $email")
                    _feedbackMessage.emit("If an account exists for $email, a password reset link has been sent. Check your inbox (and spam folder).")
                }
            } catch (e: FirebaseAuthInvalidUserException) {
                Log.w(TAG, "Password reset failed (InvalidUser): $email", e)
                _feedbackMessage.emit("If an account exists for $email, a password reset link has been sent.")
            } catch (e: Exception) {
                Log.e(TAG, "Password reset failed for: $email", e)
                _uiState.value =
                    LoginUiState.Error("Failed to send reset email: ${e.message ?: "Please try again later"}")
            } finally {
                _isResettingPassword.value = false
            }
        }
    }

    private fun handleEmailPasswordLoginSuccess() {
        viewModelScope.launch {
            val currentUser = firebaseAuth.currentUser
            if (currentUser != null) {
                Log.d(
                    TAG,
                    "Email/Pass login success, saving/updating login info for ${currentUser.uid}"
                )
                val saveLoginResult = authRepository.saveOrUpdateUserLoginInfo(currentUser)

                saveLoginResult.onSuccess {
                    Log.d(TAG, "User login info saved/updated successfully.")
                    getAndSaveFcmToken(currentUser.uid)
                    _uiState.value = LoginUiState.LoginVerifiedSuccess
                }.onFailure { saveError ->
                    Log.e(TAG, "Failed to save/update user login info after login", saveError)
                    _uiState.value =
                        LoginUiState.Error("Login successful, but failed to save profile data.")
                }
            } else {
                Log.e(TAG, "Email/Pass login success but currentUser is null!")
                _uiState.value =
                    LoginUiState.Error("Login failed: Could not retrieve user details.")
            }
        }
    }

    fun handleGoogleSignInSuccess(idToken: String) {
        if (_uiState.value == LoginUiState.Loading) return

        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            Log.d(TAG, "Handling Google Sign-In Success, attempting Firebase link...")

            val linkResult = authRepository.linkGoogleCredentialToFirebase(idToken)

            linkResult.onSuccess { firebaseUser ->
                Log.d(
                    TAG,
                    "Firebase link successful: ${firebaseUser.uid}. Saving/updating login info..."
                )

                val saveLoginResult = authRepository.saveOrUpdateUserLoginInfo(firebaseUser)

                saveLoginResult.onSuccess {
                    Log.d(TAG, "Google user login info saved/updated successfully.")
                    checkCreateProfileAndSaveToken(firebaseUser)
                }.onFailure { saveLoginError ->
                    Log.e(TAG, "Failed to save/update Google user login info", saveLoginError)
                    _uiState.value =
                        LoginUiState.Error("Google Sign-In successful, but failed to save basic profile data.")
                }

            }.onFailure { linkError ->
                Log.e(TAG, "Firebase link failed", linkError)
                _uiState.value =
                    LoginUiState.Error("Failed to link Google Sign-In: ${linkError.message}")
            }
        }
    }

    private suspend fun checkCreateProfileAndSaveToken(firebaseUser: FirebaseUser) {
        val userId = firebaseUser.uid
        val userEmail = firebaseUser.email ?: ""
        val photoUrl = firebaseUser.photoUrl?.toString()
        val displayName = firebaseUser.displayName

        Log.d(TAG, "Checking profile for Google user: $userId")
        val profileResult = authRepository.getUserProfile(userId)

        profileResult.onSuccess { existingProfile ->
            val profileUpdateOrCreationSuccess: Boolean = try {
                if (existingProfile == null) {
                    Log.d(TAG, "Profile for $userId does not exist. Creating initial profile.")
                    val initialProfileData = mutableMapOf<String, Any?>(
                        "uid" to userId,
                        "email" to userEmail,
                        "createdAt" to Timestamp.now()
                    )
                    if (photoUrl != null) {
                        initialProfileData["profileImageUrl"] = photoUrl
                    }
                    if (displayName?.isNotBlank() == true) {
                        initialProfileData["displayName"] = displayName
                    }

                    val createResult = authRepository.updateUserProfile(userId, initialProfileData)
                    if (createResult.isFailure) Log.e(
                        TAG,
                        "Failed to create initial profile",
                        createResult.exceptionOrNull()
                    )
                    createResult.isSuccess

                } else {
                    Log.d(TAG, "Profile for $userId already exists.")
                    if (existingProfile.profileImageUrl.isNullOrBlank() && photoUrl != null) {
                        Log.d(
                            TAG,
                            "Existing profile missing image. Updating with Google photo: $photoUrl"
                        )
                        val updateResult = authRepository.updateUserProfile(
                            userId,
                            mapOf("profileImageUrl" to photoUrl)
                        )
                        if (updateResult.isFailure) Log.e(
                            TAG,
                            "Failed to add Google photo",
                            updateResult.exceptionOrNull()
                        )
                        updateResult.isSuccess
                    } else {
                        true
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during profile check/create/update", e)
                false
            }

            if (profileUpdateOrCreationSuccess) {
                Log.d(TAG, "Profile check/create/update successful for $userId.")
                getAndSaveFcmToken(userId)
                _uiState.value = LoginUiState.LoginVerifiedSuccess
            } else {
                Log.e(TAG, "Failed to create/update profile for $userId")
                _uiState.value =
                    LoginUiState.Error("Google Sign-In successful, but failed to handle profile data.")
            }

        }.onFailure { fetchError ->
            Log.e(TAG, "Failed to check for existing profile for $userId", fetchError)
            _uiState.value =
                LoginUiState.Error("Google Sign-In successful, but failed check profile existence.")
        }
    }

    private suspend fun getAndSaveFcmToken(userId: String) {
        try {
            Log.d(TAG, "Attempting to retrieve FCM token...")
            val fcmToken = FirebaseMessaging.getInstance().token.await()
            Log.d(TAG, "FCM Token retrieved.")

            if (fcmToken.isNotEmpty()) {
                Log.d(TAG, "Saving FCM token to Firestore for user $userId.")
                val saveTokenResult = authRepository.saveFcmToken(userId, fcmToken)
                if (saveTokenResult.isFailure) {
                    Log.e(
                        TAG,
                        "Failed to save FCM token to Firestore.",
                        saveTokenResult.exceptionOrNull()
                    )
                } else {
                    Log.d(TAG, "FCM token save successful.")
                }
            } else {
                Log.w(TAG, "Retrieved FCM token is empty. Cannot save.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve FCM token", e)
        }
    }

    private fun isValidEmailFormat(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun mapLoginError(exception: Throwable): String {
        return when (exception) {
            is IOException -> "Network error. Please check connection."
            is IllegalArgumentException -> exception.message
                ?: "Invalid credentials."

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
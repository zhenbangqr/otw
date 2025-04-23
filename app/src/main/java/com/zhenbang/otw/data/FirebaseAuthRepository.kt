package com.zhenbang.otw.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import kotlinx.coroutines.tasks.await
import java.lang.Exception
import java.lang.IllegalArgumentException // Keep for mapping specific errors
import java.lang.IllegalStateException // Keep for mapping specific errors

// Implementation using Firebase Authentication SDK
// REMEMBER TO ADD FIREBASE AUTH DEPENDENCY to your build.gradle
// implementation(platform("com.google.firebase:firebase-bom:LATEST_VERSION")) // Check for latest BOM
// implementation("com.google.firebase:firebase-auth-ktx")
// implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:LATEST_VERSION") // For await()
class FirebaseAuthRepository : AuthRepository { // Implement the interface

    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    private val TAG = "FirebaseAuthRepository" // Tag for logging

    /**
     * Creates a new user with email and password, then sends a verification email link.
     */
    override suspend fun createUserAndSendVerificationLink(email: String, password: String): Result<Unit> {
        return try {
            Log.d(TAG, "Attempting to create user: $email")
            // 1. Create the user
            val authResult = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val user = authResult.user

            if (user == null) {
                Log.w(TAG, "User creation succeeded but user object is null.")
                Result.failure(IllegalStateException("User creation returned null user."))
            } else {
                Log.d(TAG, "User created successfully: ${user.uid}. Sending verification email.")
                // 2. Send verification link
                user.sendEmailVerification().await() // await() waits for the task to complete
                Log.d(TAG, "Verification email sent to: $email")
                // Optional: Sign the user out immediately after registration if desired
                // firebaseAuth.signOut()
                Result.success(Unit)
            }
        } catch (e: FirebaseAuthUserCollisionException) {
            Log.w(TAG, "User creation failed: Email already in use ($email).", e)
            Result.failure(IllegalArgumentException("Email address is already in use."))
        } catch (e: FirebaseAuthWeakPasswordException) {
            Log.w(TAG, "User creation failed: Weak password.", e)
            Result.failure(IllegalArgumentException("Password is too weak. Please choose a stronger password."))
        } catch (e: Exception) {
            Log.e(TAG, "User creation or verification email sending failed for $email", e)
            // Catch other Firebase/general exceptions
            Result.failure(e) // Propagate general exceptions
        }
    }

    /**
     * Signs in an existing user with their email and password.
     * Note: Does NOT check verification status here; ViewModel should do that after success.
     */
    override suspend fun signInWithEmailAndPassword(email: String, password: String): Result<Unit> {
        return try {
            Log.d(TAG, "Attempting to sign in user: $email")
            firebaseAuth.signInWithEmailAndPassword(email, password).await()
            // Sign in successful, Firebase Auth automatically persists the user session.
            Log.d(TAG, "Sign in successful for: $email")
            Result.success(Unit)
        } catch (e: FirebaseAuthInvalidUserException) {
            Log.w(TAG, "Sign in failed: User not found ($email).", e)
            Result.failure(IllegalArgumentException("No account found with this email address."))
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Log.w(TAG, "Sign in failed: Invalid credentials for $email.", e)
            // Covers wrong password, potentially disabled account etc.
            Result.failure(IllegalArgumentException("Incorrect password or invalid user."))
        } catch (e: Exception) {
            Log.e(TAG, "signInWithEmailAndPassword failed for $email", e)
            // Catch other potential exceptions during sign in
            Result.failure(Exception("Login failed. Please try again later.", e)) // Wrap general exception
        }
    }


    /**
     * Checks the verification status of the currently signed-in user.
     * IMPORTANT: This method reloads the user state from Firebase first.
     */
    override suspend fun checkCurrentUserVerificationStatus(): Result<Boolean> {
        return try {
            val user = firebaseAuth.currentUser
            if (user == null) {
                Log.d(TAG, "checkCurrentUserVerificationStatus: No user signed in.")
                return Result.success(false) // No user means not verified
            }
            Log.d(TAG, "checkCurrentUserVerificationStatus: Reloading user ${user.uid}")
            user.reload().await() // Reload to get latest status from Firebase backend
            val isVerified = user.isEmailVerified // Check status AFTER reload
            Log.d(TAG, "checkCurrentUserVerificationStatus: User ${user.uid}, isEmailVerified: $isVerified")
            Result.success(isVerified)
        } catch (e: Exception) {
            Log.e(TAG, "checkCurrentUserVerificationStatus failed", e)
            Result.failure(e) // Propagate exception
        }
    }

    /**
     * Resends the verification email link to the currently signed-in user,
     * if they are signed in and their email is not already verified.
     */
    override suspend fun resendVerificationLink(): Result<Unit> {
        return try {
            val user = firebaseAuth.currentUser
            if (user == null) {
                Log.w(TAG, "resendVerificationLink: No user is currently signed in.")
                Result.failure(IllegalStateException("No user is currently signed in to resend verification."))
            } else if (user.isEmailVerified) {
                Log.w(TAG, "resendVerificationLink: Email (${user.email}) is already verified.")
                Result.failure(IllegalStateException("Email is already verified."))
            } else {
                Log.d(TAG, "resendVerificationLink: Resending verification to ${user.email}")
                user.sendEmailVerification().await()
                Log.d(TAG, "resendVerificationLink: Resend email sent successfully.")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "resendVerificationLink failed", e)
            Result.failure(e) // Propagate exception
        }
    }

    // --- Add other repository methods as needed ---
    // Example:
    // override suspend fun signOut(): Result<Unit> {
    //     return try {
    //         firebaseAuth.signOut()
    //         Result.success(Unit)
    //     } catch (e: Exception) {
    //          Log.e(TAG, "signOut failed", e)
    //         Result.failure(e)
    //     }
    // }
}
package com.zhenbang.otw.data

import com.google.firebase.auth.FirebaseUser
import android.net.Uri

// Interface defining all authentication operations needed by ViewModels
interface AuthRepository {

    // --- Existing Auth Methods ---
    suspend fun createUserAndSendVerificationLink(email: String, password: String): Result<Unit>
    suspend fun signInWithEmailAndPassword(email: String, password: String): Result<Unit>
    suspend fun checkCurrentUserVerificationStatus(): Result<Boolean>
    suspend fun resendVerificationLink(): Result<Unit>
    suspend fun saveUserDataAfterVerification(userId: String, email: String): Result<Unit>
    suspend fun saveOrUpdateUserLoginInfo(user: FirebaseUser): Result<Unit>
    suspend fun linkGoogleCredentialToFirebase(idToken: String): Result<FirebaseUser>
    suspend fun getUserProfile(userId: String): Result<UserProfile?>
    suspend fun updateUserProfile(userId: String, profileUpdates: Map<String, Any?>): Result<Unit>
    suspend fun uploadProfileImage(userId: String, imageUri: Uri): Result<String>
    suspend fun getAllUserProfilesFromFirestore(): Result<List<UserProfile>>
}

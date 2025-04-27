package com.zhenbang.otw.data

import com.google.firebase.auth.FirebaseUser

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

    // --- NEW Profile Methods ---

    /**
     * Fetches the UserProfile data from Firestore for the given user ID.
     * @param userId The Firebase Auth User ID.
     * @return Result containing the UserProfile on success, or an exception on failure (e.g., not found, permission denied).
     */
    suspend fun getUserProfile(userId: String): Result<UserProfile?> // Return nullable UserProfile

    /**
     * Updates specific fields in the user's profile document in Firestore.
     * Uses merge to avoid overwriting existing fields unintentionally.
     * @param userId The Firebase Auth User ID.
     * @param profileUpdates A map containing the fields and new values to update.
     * @return Result indicating success or failure of the update operation.
     */
    suspend fun updateUserProfile(userId: String, profileUpdates: Map<String, Any?>): Result<Unit>
    // -------------------------

    // suspend fun signOut() // Example
}

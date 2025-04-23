package com.zhenbang.otw.data // Or your data layer package

// Interface defining all authentication operations needed by ViewModels
interface AuthRepository {

    suspend fun createUserAndSendVerificationLink(email: String, password: String): Result<Unit>

    suspend fun signInWithEmailAndPassword(email: String, password: String): Result<Unit> // Add this

    suspend fun checkCurrentUserVerificationStatus(): Result<Boolean>

    suspend fun resendVerificationLink(): Result<Unit>

    // Add other common auth functions if needed by your app:
    // fun getCurrentUser(): FirebaseUser? // Example non-suspending
    // suspend fun signOut() // Example
}
package com.zhenbang.otw.data.repository

import android.net.Uri
import com.google.firebase.auth.FirebaseUser
import com.zhenbang.otw.data.model.Comment
import com.zhenbang.otw.data.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface AuthRepository {

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
    suspend fun getUserByEmail(email: String): Result<UserProfile?>
    suspend fun getAllUserProfilesFromFirestore(): Result<List<UserProfile>>
    suspend fun blockUser(currentUserId: String, userIdToBlock: String): Result<Unit>
    suspend fun unblockUser(currentUserId: String, userIdToUnblock: String): Result<Unit>
    suspend fun saveFcmToken(userId: String, token: String): Result<Unit>
    fun getCommentsFlow(issueId: Int): Flow<List<Comment>>
    suspend fun addComment(issueId: Int, comment: Comment): Result<Unit>
    suspend fun updateComment(issueId: Int, commentId: String, newText: String): Result<Unit>
    suspend fun deleteComment(issueId: Int, commentId: String): Result<Unit>
}
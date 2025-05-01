package com.zhenbang.otw.data

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.size
import androidx.core.graphics.values
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.tasks.await
import java.lang.Exception
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.util.UUID

class FirebaseAuthRepository : AuthRepository {

    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db = Firebase.firestore
    private val storage = Firebase.storage
    private val usersCollection = db.collection("users")
    private val TAG = "FirebaseAuthRepository"

    /**
     * Saves basic user information (like email) to the database after verification.
     * Creates the document or merges data if it exists. Sets initial profileImageUrl to null.
     */
    override suspend fun saveUserDataAfterVerification(userId: String, email: String): Result<Unit> {
        return try {
            Log.d(TAG, "Attempting to save user data for $userId with email $email after verification")
            val userProfile = hashMapOf(
                "email" to email,
                "uid" to userId,
                "createdAt" to com.google.firebase.Timestamp.now(),
                "profileImageUrl" to null
            )

            usersCollection.document(userId)
                .set(userProfile, SetOptions.merge())
                .await()

            Log.d(TAG, "User data saved successfully for $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save user data for $userId", e)
            Result.failure(e)
        }
    }

    /**
     * Saves or updates basic user login info (email, uid, lastLoginAt).
     * *** Does NOT update profileImageUrl anymore ***
     */
    override suspend fun saveOrUpdateUserLoginInfo(user: FirebaseUser): Result<Unit> {
        val userId = user.uid
        val userEmail = user.email

        if (userEmail == null) {
            Log.w(TAG, "Cannot save user info: email is null for user $userId")
            return Result.failure(IllegalStateException("User email is null."))
        }

        Log.d(TAG, "Preparing to save/update login info for $userId")
        return try {
            val userLoginInfo = mapOf<String, Any?>(
                "email" to userEmail,
                "uid" to userId,
                "lastLoginAt" to com.google.firebase.Timestamp.now()
            )
            Log.d(TAG, "Data to save/merge (login info only): $userLoginInfo")

            val docRef = usersCollection.document(userId)
            Log.d(TAG, "Obtained DocumentReference: ${docRef.path}")

            Log.d(TAG, "Executing Firestore set(merge) operation for login info...")
            docRef.set(userLoginInfo, SetOptions.merge()).await()
            Log.d(TAG, "Firestore set(merge) for login info completed successfully for $userId")

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "!!! Firestore Error in saveOrUpdateUserLoginInfo for $userId !!!", e)
            Result.failure(e)
        }
    }

    /**
     * Creates a new user with email and password, then sends a verification email link.
     * User remains logged in but unverified after this call.
     */
    override suspend fun createUserAndSendVerificationLink(email: String, password: String): Result<Unit> {
        return try {
            Log.d(TAG, "Attempting to create user: $email")
            val authResult = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val user = authResult.user
            if (user == null) {
                Log.w(TAG, "User creation succeeded but user object is null.")
                Result.failure(IllegalStateException("User creation returned null user."))
            } else {
                Log.d(TAG, "User created successfully: ${user.uid}. Sending verification email.")
                user.sendEmailVerification().await()
                Log.d(TAG, "Verification email sent to: $email")
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
            Result.failure(e)
        }
    }

    /**
     * Signs in an existing user with their email and password.
     */
    override suspend fun signInWithEmailAndPassword(email: String, password: String): Result<Unit> {
        return try {
            Log.d(TAG, "Attempting to sign in user: $email")
            firebaseAuth.signInWithEmailAndPassword(email, password).await()
            Log.d(TAG, "Sign in successful for: $email")
            Result.success(Unit)
        } catch (e: FirebaseAuthInvalidUserException) {
            Log.w(TAG, "Sign in failed: User not found ($email).", e)
            Result.failure(IllegalArgumentException("No account found with this email address."))
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Log.w(TAG, "Sign in failed: Invalid credentials for $email.", e)
            Result.failure(IllegalArgumentException("Incorrect password or invalid user."))
        } catch (e: Exception) {
            Log.e(TAG, "signInWithEmailAndPassword failed for $email", e)
            Result.failure(Exception("Login failed. Please try again later.", e))
        }
    }


    /**
     * Checks the verification status of the currently signed-in user.
     */
    override suspend fun checkCurrentUserVerificationStatus(): Result<Boolean> {
        return try {
            val user = firebaseAuth.currentUser
            if (user == null) {
                Log.d(TAG, "checkCurrentUserVerificationStatus: No user signed in.")
                return Result.success(false)
            }
            Log.d(TAG, "checkCurrentUserVerificationStatus: Reloading user ${user.uid}")
            user.reload().await() // Reload to get latest status from Firebase backend
            val isVerified = user.isEmailVerified // Check status AFTER reload
            Log.d(TAG, "checkCurrentUserVerificationStatus: User ${user.uid}, isEmailVerified: $isVerified")
            Result.success(isVerified)
        } catch (e: Exception) {
            Log.e(TAG, "checkCurrentUserVerificationStatus failed", e)
            Result.failure(e)
        }
    }

    /**
     * Resends the verification email link to the currently signed-in user.
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
            Result.failure(e)
        }
    }

    /**
     * Links a Google ID token to Firebase Auth, signing the user in.
     */
    override suspend fun linkGoogleCredentialToFirebase(idToken: String): Result<FirebaseUser> {
        return try {
            Log.d(TAG, "Attempting to link Google ID token to Firebase")
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            val firebaseUser = authResult.user
            if (firebaseUser != null) {
                Log.d(TAG, "Successfully linked/signed in Google user to Firebase: ${firebaseUser.uid}")
                Result.success(firebaseUser)
            } else {
                Log.w(TAG, "Firebase sign-in with Google credential returned null user.")
                Result.failure(IllegalStateException("Firebase sign-in with Google credential returned null user."))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to link/sign in Google credential to Firebase", e)
            Result.failure(e)
        }
    }

    /**
     * Fetches the UserProfile data from Firestore for the given user ID.
     */
    override suspend fun getUserProfile(userId: String): Result<UserProfile?> {
        return try {
            Log.d(TAG, "Fetching user profile for $userId")
            val documentSnapshot = usersCollection.document(userId).get().await()
            if (documentSnapshot.exists()) {
                val userProfile = documentSnapshot.toObject<UserProfile>()
                Log.d(TAG, "User profile fetched successfully for $userId. Profile: $userProfile")
                Result.success(userProfile)
            } else {
                Log.d(TAG, "User profile document does not exist for $userId")
                Result.success(null) // Return success with null data if document doesn't exist
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch user profile for $userId", e)
            Result.failure(e)
        }
    }

    /**
     * Updates specific fields in the user's profile document in Firestore using merge.
     * Creates the document if it doesn't exist.
     */
    override suspend fun updateUserProfile(userId: String, profileUpdates: Map<String, Any?>): Result<Unit> {
        return try {
            Log.d(TAG, "Attempting to update profile for $userId with data: $profileUpdates")
            usersCollection.document(userId)
                .set(profileUpdates, SetOptions.merge())
                .await()

            Log.d(TAG, "User profile created/updated successfully for $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create/update user profile for $userId", e)
            Result.failure(e)
        }
    }

    /**
     * Uploads the selected profile image to Firebase Storage.
     */
    override suspend fun uploadProfileImage(userId: String, imageUri: Uri): Result<String> {
        return try {
            val filename = "${UUID.randomUUID()}.jpg"
            val storageRef = storage.reference.child("profile_images/$userId/$filename")
            Log.d(TAG, "Uploading image to: ${storageRef.path}")
            val uploadTask = storageRef.putFile(imageUri).await()
            val downloadUrl = uploadTask.storage.downloadUrl.await().toString()
            Log.d(TAG, "Image uploaded successfully. Download URL: $downloadUrl")
            Result.success(downloadUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Image upload failed for user $userId", e)
            Result.failure(e)
        }
    }

    override suspend fun getAllUserProfilesFromFirestore(): Result<List<UserProfile>> {
        return try {
            Log.d(TAG, "Attempting to fetch all user profiles from Firestore collection 'users'")
            // Get all documents from the 'users' collection
            val querySnapshot = usersCollection.get().await()
            // Convert each document to a UserProfile object, filtering out nulls if conversion fails
            val userProfiles = querySnapshot.documents.mapNotNull { document ->
                try {
                    document.toObject<UserProfile>()
                } catch (e: Exception) {
                    Log.e(TAG, "Error converting document ${document.id} to UserProfile", e)
                    null // Skip documents that fail conversion
                }
            }
            Log.d(TAG, "Fetched ${userProfiles.size} user profiles successfully.")
            Result.success(userProfiles)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch all user profiles from Firestore", e)
            Result.failure(e) // Propagate the exception (likely a permission error if rules aren't set)
        }
    }

    override suspend fun blockUser(currentUserId: String, userIdToBlock: String): Result<Unit> {
        Log.d(TAG, "User $currentUserId attempting to block user $userIdToBlock")
        if (currentUserId == userIdToBlock) {
            Log.w(TAG,"User cannot block themselves.")
            return Result.failure(IllegalArgumentException("You cannot block yourself."))
        }
        return try {
            usersCollection.document(currentUserId)
                .update("blockedUserIds", FieldValue.arrayUnion(userIdToBlock)) // Add ID to array
                .await()
            Log.d(TAG, "User $userIdToBlock blocked successfully by $currentUserId.")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to block user $userIdToBlock for $currentUserId", e)
            Result.failure(e)
        }
    }

    override suspend fun unblockUser(currentUserId: String, userIdToUnblock: String): Result<Unit> {
        Log.d(TAG, "User $currentUserId attempting to unblock user $userIdToUnblock")
        return try {
            usersCollection.document(currentUserId)
                .update("blockedUserIds", FieldValue.arrayRemove(userIdToUnblock)) // Remove ID from array
                .await()
            Log.d(TAG, "User $userIdToUnblock unblocked successfully by $currentUserId.")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unblock user $userIdToUnblock for $currentUserId", e)
            Result.failure(e)
        }
    }

    override suspend fun getUserByEmail(email: String): Result<UserProfile?> {
        return try {
            Log.d(TAG, "Attempting to fetch user profile with email: $email from Firestore")
            val querySnapshot = usersCollection.whereEqualTo("email", email).limit(1).get().await()

            if (!querySnapshot.isEmpty) {
                val document = querySnapshot.documents.first()
                // Assuming you have a UserProfile data class and your Firestore
                // document can be directly mapped to it.
                val userProfile = document.toObject(UserProfile::class.java)
                Log.d(TAG, "Successfully fetched user profile: $userProfile")
                Result.success(userProfile)
            } else {
                Log.d(TAG, "No user found with email: $email")
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch user profile by email from Firestore", e)
            Result.failure(e)
        }
    }
}

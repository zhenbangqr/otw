package com.zhenbang.otw.data

import com.google.firebase.Timestamp

/**
 * Represents the user profile data stored in Firestore.
 * Make fields nullable if they are optional.
 */
data class UserProfile(
    // Required fields from Auth
    val uid: String = "", // Should always be present
    val email: String = "", // Should always be present

    // Fields to be filled in by the user
    var displayName: String? = null, // e.g., "John Doe" or a chosen username
    var phoneNumber: String? = null, // Store as String for formatting flexibility
    var firstName: String? = null, // Optional: Separate first/last name
    var lastName: String? = null, // Optional
    var dateOfBirth: Timestamp? = null, // Store as Timestamp for querying
    var bio: String? = null, // Short user description
    var profileImageUrl: String? = null, // URL to profile picture (handle upload separately)

    // Timestamps (Managed by server or on creation/update)
    val createdAt: Timestamp? = null,
    val lastLoginAt: Timestamp? = null // Updated by saveOrUpdateUserLoginInfo
) {
    // Add a no-argument constructor for Firestore deserialization
    constructor() : this("", "")
}

// Helper function to check if essential profile details are missing
fun UserProfile?.isProfileIncomplete(): Boolean {
    // Define what constitutes an "incomplete" profile for your app
    // For example, require at least a display name.
    return this == null || this.displayName.isNullOrBlank() // || this.phoneNumber.isNullOrBlank() // Add other required fields
}

package com.zhenbang.otw.data // Or your model package

import com.google.firebase.Timestamp // Import Timestamp for date fields

/**
 * Represents the user profile data stored in Firestore.
 * Make fields nullable if they are optional.
 */
data class UserProfile(
    // Required fields from Auth
    val uid: String = "",
    val email: String = "",
    var displayName: String? = null,
    var phoneNumber: String? = null,
    var dateOfBirth: Timestamp? = null,
    var bio: String? = null,
    var profileImageUrl: String? = null,

    // Timestamps (Managed by server or on creation/update)
    val createdAt: Timestamp? = null,
    val lastLoginAt: Timestamp? = null
) {
    constructor() : this("", "")
}

fun UserProfile?.isProfileIncomplete(): Boolean {
    return this == null || this.displayName.isNullOrBlank() || this.phoneNumber.isNullOrBlank() || this.dateOfBirth == null
}

package com.zhenbang.otw.data.model

import com.google.firebase.Timestamp

data class UserProfile(
    val uid: String = "",
    val email: String = "",
    var displayName: String? = null,
    var phoneNumber: String? = null,
    var dateOfBirth: Timestamp? = null,
    var bio: String? = null,
    var profileImageUrl: String? = null,
    val blockedUserIds: List<String> = emptyList(),
    val createdAt: Timestamp? = null,
    val lastLoginAt: Timestamp? = null
) {
    constructor() : this("", "")
}

fun UserProfile?.isProfileIncomplete(): Boolean {
    return this == null || this.displayName.isNullOrBlank() || this.phoneNumber.isNullOrBlank() || this.dateOfBirth == null
}
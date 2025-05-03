package com.zhenbang.otw.data

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Comment(
    @DocumentId // Firestore document ID will be mapped here
    val id: String = "",

    val text: String = "",
    val authorUid: String = "", // Firebase Auth UID
    val authorEmail: String? = null, // Denormalized email

    @ServerTimestamp // Automatically set by Firestore on creation
    val timestamp: Date? = null
) {
    // Add no-argument constructor for Firestore deserialization
    constructor() : this("", "", "", null, null)
}
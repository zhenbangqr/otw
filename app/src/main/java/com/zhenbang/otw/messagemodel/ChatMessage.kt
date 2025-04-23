package com.zhenbang.otw.model // Or your data package

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint // Import GeoPoint for location
import com.google.firebase.firestore.ServerTimestamp

// Define message types using an Enum for clarity
enum class MessageType {
    TEXT, IMAGE, LOCATION, AUDIO
}

data class ChatMessage(
    val messageId: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val timestamp: Timestamp? = null, // Keep using @ServerTimestamp in VM write if possible
    val messageType: MessageType = MessageType.TEXT, // Add type field

    // Fields for specific types (nullable)
    val text: String? = null, // Make text nullable
    val imageUrl: String? = null, // URL after uploading to Cloud Storage
    val location: GeoPoint? = null, // Store location as Firestore GeoPoint
    val audioUrl: String? = null, // URL after uploading to Cloud Storage
    val audioDurationMillis: Long? = null // Optional: Duration for UI
) {
    constructor() : this("", "", "", null, MessageType.TEXT, null, null, null, null, null) // Firestore constructor
}
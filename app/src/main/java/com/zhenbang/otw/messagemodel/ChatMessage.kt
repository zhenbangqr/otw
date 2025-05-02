package com.zhenbang.otw.messagemodel // Or your data package

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.ServerTimestamp
import java.security.Timestamp

enum class MessageType { TEXT, IMAGE, LOCATION, AUDIO }

data class ChatMessage(
    val messageId: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val timestamp: Timestamp? = null, // MUST be Timestamp? or Date?, MUST be initialized/set to null before saving
    val messageType: MessageType = MessageType.TEXT,

    // Fields for specific types
    val text: String? = null, // Nullable
    val imageUrl: String? = null,
    val location: GeoPoint? = null,
    val locationName: String? = null,
    val staticMapUrl: String? = null,
    val audioUrl: String? = null,
    val audioDurationMillis: Long? = null
) {
    constructor() : this("", "", "", null, MessageType.TEXT, null, null, null, null, null)
}
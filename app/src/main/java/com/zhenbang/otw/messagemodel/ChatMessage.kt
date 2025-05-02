package com.zhenbang.otw.messagemodel

import com.google.firebase.Timestamp // Keep using Firebase Timestamp internally
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.ServerTimestamp
import kotlinx.serialization.Contextual // For potential advanced serialization later if needed
import kotlinx.serialization.Serializable

// Define MessageType Enum (if not already defined)
// Make sure it can be easily stored/retrieved, storing as String is common
enum class MessageType {
    TEXT, IMAGE, AUDIO, LOCATION
}

// Your original ChatMessage data class
// Add @ServerTimestamp if you haven't already
data class ChatMessage(
    val messageId: String? = null, // Added during fetch usually
    val senderId: String? = null,
    val receiverId: String? = null,
    val text: String? = null,
    val imageUrl: String? = null,
    val audioUrl: String? = null,
    val audioDurationMillis: Long? = null,
    val location: GeoPoint? = null, // Keep GeoPoint for internal use
    val locationName: String? = null,
    val staticMapUrl: String? = null,
    val messageType: MessageType? = null, // Keep Enum for internal use

    @ServerTimestamp // Ensure this is here
    val timestamp: Timestamp? = null // Keep Firebase Timestamp for internal use
)

// --- NEW: Serializable version for JSON export ---
// Uses basic types (String, Long, Double, Boolean, List, Map) suitable for JSON
@Serializable // Make this class serializable
data class SerializableChatMessage(
    val messageId: String?,
    val senderId: String?,
    val receiverId: String?,
    val text: String?,
    val imageUrl: String?,
    val audioUrl: String?,
    val audioDurationMillis: Long?,
    val location: SerializableGeoPoint?, // Use serializable GeoPoint version
    val locationName: String?,
    val staticMapUrl: String?,
    val messageType: String?, // Store enum as String
    val timestampMillis: Long? // Store timestamp as epoch milliseconds (Long)
)

// NEW: Serializable representation of GeoPoint
@Serializable
data class SerializableGeoPoint(
    val latitude: Double,
    val longitude: Double
)

// --- NEW: Conversion function ---
fun ChatMessage.toSerializableChatMessage(): SerializableChatMessage {
    return SerializableChatMessage(
        messageId = this.messageId,
        senderId = this.senderId,
        receiverId = this.receiverId,
        text = this.text,
        imageUrl = this.imageUrl,
        audioUrl = this.audioUrl,
        audioDurationMillis = this.audioDurationMillis,
        location = this.location?.let { SerializableGeoPoint(it.latitude, it.longitude) },
        locationName = this.locationName,
        staticMapUrl = this.staticMapUrl,
        messageType = this.messageType?.name, // Convert enum to String (its name)
        timestampMillis = this.timestamp?.toDate()?.time // Convert Timestamp to epoch milliseconds (Long)
    )
}
package com.zhenbang.otw.util // Example utility package

import com.zhenbang.otw.messagemodel.ChatMessage
import com.zhenbang.otw.messagemodel.SerializableChatMessage
import com.zhenbang.otw.messagemodel.toSerializableChatMessage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object JsonFormatter {

    // Configure JSON instance (optional: pretty printing)
    private val json = Json {
        prettyPrint = true // Makes the output JSON readable
        encodeDefaults = true // Include fields with default values
        ignoreUnknownKeys = true // Useful if JSON might have extra fields later
    }

    fun formatMessagesToJson(messages: List<ChatMessage>): String {
        // 1. Convert the list of ChatMessage to list of SerializableChatMessage
        val serializableList = messages.map { it.toSerializableChatMessage() }

        // 2. Encode the serializable list to a JSON string
        return json.encodeToString(serializableList)
    }
}
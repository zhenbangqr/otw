package com.zhenbang.otw.data.model

import com.google.gson.annotations.SerializedName

/**
 * Represents the main request payload sent to the API.
 * Contains all configurable parameters for the API call.
 */

data class ZpRequest(

    @SerializedName("model")
    val model: String,

    @SerializedName("messages")
    val messages: List<RequestMessage>,
)

/**
 * Represents a single message in the conversation history sent to the model.
 */
data class RequestMessage(
    @SerializedName("role")
    val role: String, // "user", "assistant", "system", "tool"

    @SerializedName("content")
    val content: String? = null, // Content is usually required for user/assistant/system

    // Include tool_call_id if this message is of role "tool", replying to a specific tool call
    // @SerializedName("tool_call_id")
    // val toolCallId: String? = null
)
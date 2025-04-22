package com.zhenbang.otw.zpApi

import com.google.gson.annotations.SerializedName

data class ResponseZPAPI(
    val choices: List<Choice>,
    val created: Long,
    val id: String,
    val model: String,

    @SerializedName("request_id")
    val requestId: String,

    val usage: Usage
)

// Represents an item within the 'choices' list in the JSON response
data class Choice(
    @SerializedName("finish_reason")
    val finishReason: String,
    val index: Int,

    // The 'message' object is nested inside each 'choice'
    val message: Message
)

data class Message(
    val role: String,
    val content: String?, // Nullable is correct
    @SerializedName("tool_calls")
    val toolCalls: List<ToolCall>? // Nullable is correct
)

data class Usage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int,
    @SerializedName("completion_tokens")
    val completionTokens: Int,
    @SerializedName("total_tokens")
    val totalTokens: Int
)

data class ToolCall(
    val function: FunctionCall,
    val id: String,
    val type: String
)

data class FunctionCall(
    val name: String,
    val arguments: String // Keep as String unless you parse the JSON schema arguments
)
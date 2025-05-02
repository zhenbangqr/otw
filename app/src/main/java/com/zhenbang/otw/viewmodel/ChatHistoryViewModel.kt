package com.zhenbang.otw.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp // Import Firebase Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObject // Keep if you need to parse specific objects later
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.* // For Date conversion from Timestamp

// Data class to represent an item in the chat history list
data class ChatHistoryItem(
    val chatId: String = "",            // e.g., uid1_uid2
    val otherUserId: String = "",
    val otherUserName: String? = "User...", // Name of the other user
    val lastMessagePreview: String? = null, // Preview text (e.g., "How are you?", "[Image]", "[Location]")
    val lastMessageTimestamp: Date? = null,  // Timestamp of the last message
    // Add other fields like otherUserProfileImageUrl if available
)

class ChatHistoryViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val currentUser = FirebaseAuth.getInstance().currentUser // Get user object once

    // StateFlow for the list of chat history items
    private val _chatHistory = MutableStateFlow<List<ChatHistoryItem>>(emptyList())
    val chatHistory: StateFlow<List<ChatHistoryItem>> = _chatHistory.asStateFlow()

    // StateFlow for loading status
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // StateFlow for errors
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        fetchChatHistory()
    }

    fun fetchChatHistory() {
        val currentUserId = currentUser?.uid
        Log.d("ChatHistoryViewModel", "Attempting query for user ID: $currentUserId / Email: ${currentUser?.email}")

        if (currentUserId == null) {
            _error.value = "Not logged in"
            Log.e("ChatHistoryViewModel", "Error: currentUserUid is null before query!")
            _isLoading.value = false // Stop loading if not logged in
            return
        }

        _isLoading.value = true
        _error.value = null

        // Query the 'chats' collection where the current user is a participant
        // Order by the denormalized timestamp to get recent chats first
        db.collection("chats")
            .whereArrayContains("participants", currentUserId)
            .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { chatSnapshots, e ->
                if (e != null) {
                    // Check for PERMISSION_DENIED specifically
                    if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        Log.e("ChatHistoryViewModel", "PERMISSION_DENIED loading chat list. Check Firestore Rules!", e)
                        _error.value = "Permission Denied. Cannot load chats. Check Firestore Rules."
                    } else {
                        Log.w("ChatHistoryViewModel", "Listen failed on chats collection.", e)
                        _error.value = "Failed to load chat list: ${e.localizedMessage}"
                    }
                    _isLoading.value = false
                    return@addSnapshotListener
                }

                if (chatSnapshots == null) {
                    Log.d("ChatHistoryViewModel", "Chat snapshots are null")
                    _isLoading.value = false
                    _chatHistory.value = emptyList() // Clear list if snapshots are null
                    return@addSnapshotListener
                }

                Log.d("ChatHistoryViewModel", "Received ${chatSnapshots.size()} chat documents.")

                // Use viewModelScope for structured concurrency
                viewModelScope.launch {
                    try {
                        // Process snapshots - Fetching names can happen concurrently if needed,
                        // but here we process sequentially for simplicity after getting snapshots.
                        val historyItems = mutableListOf<ChatHistoryItem>()
                        for (chatDoc in chatSnapshots.documents) {
                            val chatId = chatDoc.id
                            val participants = chatDoc.get("participants") as? List<String>
                            val otherUserId = participants?.firstOrNull { it != currentUserId }

                            if (otherUserId == null) {
                                Log.w("ChatHistoryViewModel", "Could not find other user ID in chat $chatId. Participants: $participants")
                                continue // Skip this chat if data is inconsistent
                            }

                            // Fetch other user's name (asynchronously)
                            // If you have many chats, using async/awaitAll as in the previous example is better
                            val otherUserName = fetchUserName(otherUserId)

                            // Get denormalized last message data directly from the chat document
                            val lastMsgPreview = chatDoc.getString("lastMessagePreview")
                            val lastMsgTimestamp = chatDoc.getTimestamp("lastMessageTimestamp")?.toDate() // Convert Firebase Timestamp to Date

                            historyItems.add(
                                ChatHistoryItem(
                                    chatId = chatId,
                                    otherUserId = otherUserId,
                                    otherUserName = otherUserName ?: "User...",
                                    lastMessagePreview = lastMsgPreview ?: "", // Provide default if null
                                    lastMessageTimestamp = lastMsgTimestamp
                                    // Add other fields like profile image URL if needed
                                )
                            )
                        }

                        _chatHistory.value = historyItems // Update the UI list
                        _error.value = null
                        Log.d("ChatHistoryViewModel", "Successfully processed ${historyItems.size} chat history items.")

                    } catch (fetchError: Exception) {
                        Log.e("ChatHistoryViewModel", "Error processing chat history items", fetchError)
                        _error.value = "Error loading chat details: ${fetchError.localizedMessage}"
                    } finally {
                        _isLoading.value = false // Ensure loading stops
                    }
                }
            }
    }

    // Helper to fetch user name (can be optimized with caching)
    private suspend fun fetchUserName(userId: String): String? {
        // Check if we are trying to fetch the current user's name unnecessarily
        if (userId == currentUser?.uid) {
            // Optionally return current user's display name if available directly
            // return currentUser?.displayName ?: "Me" // Example
        }
        return try {
            val snapshot = db.collection("users").document(userId).get().await()
            // Prioritize 'username', fallback to 'name', then return null
            snapshot.getString("username") ?: snapshot.getString("name")
        } catch (e: Exception) {
            // Check for permission denied specifically
            if (e is com.google.firebase.firestore.FirebaseFirestoreException && e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                Log.e("ChatHistoryViewModel", "PERMISSION_DENIED fetching user name for $userId. Check Firestore rules for /users/{userId}", e)
            } else {
                Log.e("ChatHistoryViewModel", "Error fetching user name for $userId", e)
            }
            null // Return null on any error
        }
    }

    // No longer need fetchLastMessage as we rely on denormalized data
    /*
    private suspend fun fetchLastMessage(chatId: String): ChatMessage? {
        // ... (Removed) ...
    }
    */
}
package com.zhenbang.otw.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObject // Extension for converting documents
import com.zhenbang.otw.messagemodel.ChatMessage // Import your data class
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MessagingViewModel(
    private val currentUserUid: String,
    private val otherUserUid: String
) : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val chatDocId = generateChatId(currentUserUid, otherUserUid)
    private val messagesCollectionRef = db.collection("chats").document(chatDocId).collection("messages")

    // StateFlow for messages
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    // StateFlow for partner's name (optional, could be passed via navigation)
    private val _partnerName = MutableStateFlow("User...") // Default placeholder
    val partnerName: StateFlow<String> = _partnerName.asStateFlow()

    // StateFlow for errors
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        listenForMessages()
        fetchPartnerName() // Fetch partner's name when VM is created
    }

    // Generates a consistent chat ID for two users
    private fun generateChatId(uid1: String, uid2: String): String {
        return if (uid1 < uid2) "${uid1}_${uid2}" else "${uid2}_${uid1}"
    }

    private fun listenForMessages() {
        messagesCollectionRef
            .orderBy("timestamp", Query.Direction.ASCENDING) // Order by time
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("MessagingViewModel", "Listen failed.", e)
                    _error.value = "Failed to load messages: ${e.localizedMessage}"
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    val loadedMessages = mutableListOf<ChatMessage>()
                    for (doc in snapshots.documents) { // Iterate through DocumentSnapshot
                        try {
                            val message = doc.toObject<ChatMessage>() // Convert document to ChatMessage
                            if (message != null) {
                                // Include document ID if needed (e.g., for keys in LazyColumn)
                                loadedMessages.add(message.copy(messageId = doc.id))
                            }
                        } catch (convError: Exception) {
                            Log.e("MessagingViewModel", "Error converting message doc ${doc.id}", convError)
                        }
                    }
                    _messages.value = loadedMessages
                    _error.value = null // Clear previous errors on success
                    Log.d("MessagingViewModel", "Loaded ${loadedMessages.size} messages.")
                } else {
                    Log.d("MessagingViewModel", "Current messages data: null")
                }
            }
    }

    fun sendMessage(text: String) {
        val trimmedText = text.trim()
        if (trimmedText.isBlank()) return // Don't send empty messages

        val newMessage = ChatMessage(
            senderId = currentUserUid,
            receiverId = otherUserUid,
            text = trimmedText
            // timestamp will be set by the server (@ServerTimestamp)
        )

        viewModelScope.launch {
            try {
                messagesCollectionRef.add(newMessage).await() // Add the message
                Log.d("MessagingViewModel", "Message sent successfully.")
                _error.value = null // Clear error on success
            } catch (e: Exception) {
                Log.e("MessagingViewModel", "Error sending message", e)
                _error.value = "Failed to send message: ${e.localizedMessage}"
                // Optionally, handle retry logic or keep the message in the input field
            }
        }
    }

    // Fetches the partner's name from Firestore "users" collection
    private fun fetchPartnerName() {
        viewModelScope.launch {
            try {
                val docRef = db.collection("users").document(otherUserUid)
                val snapshot = docRef.get().await()
                if (snapshot.exists()) {
                    val name = snapshot.getString("name") // Assuming name is stored in "name" field
                    if (!name.isNullOrEmpty()) {
                        _partnerName.value = name
                    } else {
                        _partnerName.value = "User ${otherUserUid.take(6)}..." // Fallback if name field is empty/missing
                    }
                } else {
                    _partnerName.value = "Unknown User" // User document doesn't exist
                }
            } catch (e: Exception) {
                Log.e("MessagingViewModel", "Error fetching partner name for $otherUserUid", e)
                _partnerName.value = "User..." // Fallback on error
            }
        }
    }

    // --- Factory for creating ViewModel with parameters ---
    companion object {
        fun provideFactory(
            currentUserUid: String,
            otherUserUid: String
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(MessagingViewModel::class.java)) {
                    return MessagingViewModel(currentUserUid, otherUserUid) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }
}
package com.zhenbang.otw.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// Simple data class to hold user info for the list
data class UserInfo(
    val uid: String = "",
    val name: String? = null, // Or username, adjust field name as needed
    // Add other relevant fields like profileImageUrl if you have them
)

class UserListViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid

    // StateFlow for the list of users
    private val _users = MutableStateFlow<List<UserInfo>>(emptyList())
    val users: StateFlow<List<UserInfo>> = _users.asStateFlow()

    // StateFlow for loading status
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // StateFlow for errors
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        fetchUsers()
    }

    fun fetchUsers() {
        if (currentUserUid == null) {
            _error.value = "Not logged in"
            return
        }

        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                val snapshot = db.collection("users")
                    // Optional: Order users by name
                    // .orderBy("name") // Make sure 'name' field exists and you have an index
                    .get()
                    .await()

                val userList = mutableListOf<UserInfo>()
                for (document in snapshot.documents) {
                    // Skip the current user
                    if (document.id == currentUserUid) continue

                    try {
                        // Attempt to convert Firestore document to UserInfo object
                        // Make sure field names ("uid", "name") match your Firestore documents
                        val user = document.toObject<UserInfo>()?.copy(uid = document.id) // Get UID from doc ID
                        if (user != null) {
                            userList.add(user)
                        } else {
                            Log.w("UserListViewModel", "Failed to convert document ${document.id} to UserInfo")
                            // Optionally add a placeholder user or skip
                        }
                    } catch (e: Exception) {
                        Log.e("UserListViewModel", "Error converting user document ${document.id}", e)
                        // Handle specific conversion errors if needed
                    }
                }
                _users.value = userList
                Log.d("UserListViewModel", "Fetched ${userList.size} users.")

            } catch (e: Exception) {
                Log.e("UserListViewModel", "Error fetching users", e)
                _error.value = "Failed to load users: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
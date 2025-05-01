package com.zhenbang.otw.profile.privacy

import android.app.Application
import android.util.Log
import androidx.compose.runtime.mutableStateOf // Use Compose state for search query
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth // To get current user ID
import com.zhenbang.otw.data.AuthRepository
import com.zhenbang.otw.data.FirebaseAuthRepository // Use Factory for DI later
import com.zhenbang.otw.data.UserProfile
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class BlockUserUiState(
    val isLoading: Boolean = true,
    val allUsers: List<UserProfile> = emptyList(),
    val searchResults: List<UserProfile> = emptyList(),
    val blockedUserProfiles: List<UserProfile> = emptyList(),
    val blockedUserIds: Set<String> = emptySet(), // Store IDs for quick checking
    val currentUserId: String? = null,
    val errorMessage: String? = null
)

class PrivacyViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: AuthRepository = FirebaseAuthRepository() // TODO: Use DI/Factory
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    private val TAG = "BlockUserViewModel"

    private val _uiState = MutableStateFlow(BlockUserUiState())
    val uiState: StateFlow<BlockUserUiState> = _uiState.asStateFlow()

    // Use Compose state for search query as it's UI input state
    var searchQuery by mutableStateOf("")
        private set

    init {
        loadInitialData()
        // Observe changes in search query to update results
        snapshotFlow { searchQuery }
            .debounce(300) // Add debounce to avoid filtering on every key press
            .onEach { query -> filterUsers(query) }
            .launchIn(viewModelScope)
    }

    private fun loadInitialData() {
        val currentUserId = firebaseAuth.currentUser?.uid
        if (currentUserId == null) {
            _uiState.update { it.copy(isLoading = false, errorMessage = "User not logged in") }
            return
        }

        _uiState.update { it.copy(isLoading = true, currentUserId = currentUserId) }
        viewModelScope.launch {
            try {
                // Fetch current user's profile first to get blocked list
                val currentUserProfileResult = repository.getUserProfile(currentUserId)
                val blockedIds = currentUserProfileResult.getOrNull()?.blockedUserIds?.toSet() ?: emptySet()

                // Fetch all users
                val allUsersResult = repository.getAllUserProfilesFromFirestore()

                if (allUsersResult.isSuccess) {
                    val allUsers = allUsersResult.getOrThrow()
                    val blockedProfiles = allUsers.filter { blockedIds.contains(it.uid) }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            allUsers = allUsers,
                            searchResults = allUsers.filter { user -> // Initial filter
                                user.uid != currentUserId && !blockedIds.contains(user.uid)
                            },
                            blockedUserIds = blockedIds,
                            blockedUserProfiles = blockedProfiles,
                            errorMessage = null
                        )
                    }
                    filterUsers(searchQuery) // Apply initial search query if any
                } else {
                    Log.e(TAG, "Failed to load all users", allUsersResult.exceptionOrNull())
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to load users") }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error loading initial data", e)
                _uiState.update { it.copy(isLoading = false, errorMessage = "Error loading data: ${e.message}") }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        searchQuery = query
    }

    private fun filterUsers(query: String) {
        val currentState = _uiState.value
        if (query.isBlank()) {
            // Show all non-blocked, non-self users if query is blank
            _uiState.update {
                it.copy(searchResults = it.allUsers.filter { user ->
                    user.uid != it.currentUserId && !it.blockedUserIds.contains(user.uid)
                })
            }
        } else {
            val lowerCaseQuery = query.lowercase()
            _uiState.update {
                it.copy(searchResults = it.allUsers.filter { user ->
                    // Filter by name or email, excluding self and already blocked
                    user.uid != it.currentUserId && !it.blockedUserIds.contains(user.uid) &&
                            (user.displayName?.lowercase()?.contains(lowerCaseQuery) == true ||
                                    user.email.lowercase().contains(lowerCaseQuery))
                })
            }
        }
    }


    fun blockUser(userIdToBlock: String) {
        val currentUserId = _uiState.value.currentUserId ?: return
        viewModelScope.launch {
            val result = repository.blockUser(currentUserId, userIdToBlock)
            if (result.isSuccess) {
                // Refresh data or update locally for immediate feedback
                loadInitialData() // Simple way to refresh everything
                // Or update locally:
                // _uiState.update { currentState ->
                //    val newBlockedIds = currentState.blockedUserIds + userIdToBlock
                //    val newBlockedProfiles = currentState.allUsers.filter { newBlockedIds.contains(it.uid) }
                //    currentState.copy(
                //        blockedUserIds = newBlockedIds,
                //        blockedUserProfiles = newBlockedProfiles,
                //        searchResults = currentState.searchResults.filter { it.uid != userIdToBlock } // Remove from search
                //    )
                // }
            } else {
                _uiState.update { it.copy(errorMessage = "Failed to block user: ${result.exceptionOrNull()?.message}") }
            }
        }
    }

    fun unblockUser(userIdToUnblock: String) {
        val currentUserId = _uiState.value.currentUserId ?: return
        viewModelScope.launch {
            val result = repository.unblockUser(currentUserId, userIdToUnblock)
            if (result.isSuccess) {
                // Refresh data or update locally for immediate feedback
                loadInitialData() // Simple way to refresh everything
                // Or update locally:
                // _uiState.update { currentState ->
                //      val newBlockedIds = currentState.blockedUserIds - userIdToUnblock
                //      val newBlockedProfiles = currentState.blockedUserProfiles.filter { it.uid != userIdToUnblock }
                //      currentState.copy(
                //          blockedUserIds = newBlockedIds,
                //          blockedUserProfiles = newBlockedProfiles
                //          // Re-filter search results if needed, or handled by loadInitialData
                //      )
                // }
                filterUsers(searchQuery) // Re-apply filter after unblocking
            } else {
                _uiState.update { it.copy(errorMessage = "Failed to unblock user: ${result.exceptionOrNull()?.message}") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // Optional: Factory for DI
    // class Factory(private val application: Application) : ViewModelProvider.Factory { ... }
}
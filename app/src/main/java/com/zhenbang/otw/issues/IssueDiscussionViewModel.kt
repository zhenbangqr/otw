// In com/zhenbang/otw/issues/IssueDiscussionViewModel.kt (or similar package)
package com.zhenbang.otw.issues

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle // Import SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.zhenbang.otw.data.AuthRepository
import com.zhenbang.otw.data.FirebaseAuthRepository // Or use DI/Factory
import com.zhenbang.otw.data.Comment
import com.zhenbang.otw.data.UserProfile // Keep UserProfile import
import com.zhenbang.otw.database.Issue
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class IssueDiscussionUiState(
    val issue: Issue? = null,
    val comments: List<Comment> = emptyList(),
    // Store commenter profiles separately for efficient updates
    val commenterProfiles: Map<String, UserProfile> = emptyMap(),
    val creatorProfile: UserProfile? = null, // Keep creator profile separate
    val isLoadingIssue: Boolean = true,
    val isLoadingComments: Boolean = true,
    val isLoadingCreatorProfile: Boolean = false,
    val error: String? = null,
    val newCommentText: String = ""
)

class IssueDiscussionViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle // Inject SavedStateHandle
) : AndroidViewModel(application) {

    // --- Dependencies ---
    private val issueRepository: IssueRepository = IssueRepository.getRepository(application) // Use existing Repo/Factory
    private val authRepository: AuthRepository = FirebaseAuthRepository() // Or use DI/Factory
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    private val TAG = "IssueDiscussionVM"

    // --- Issue ID from Navigation ---
    val issueId: StateFlow<Int> = savedStateHandle.getStateFlow("issueId", 0)

    // --- UI State ---
    private val _uiState = MutableStateFlow(IssueDiscussionUiState())
    val uiState: StateFlow<IssueDiscussionUiState> = _uiState.asStateFlow()

    // --- Current User Info ---
    val currentUserId: String? get() = firebaseAuth.currentUser?.uid
    val currentUserEmail: String? get() = firebaseAuth.currentUser?.email

    init {
        // Observe issueId changes
        viewModelScope.launch {
            issueId.collectLatest { id ->
                if (id > 0) {
                    Log.d(TAG, "Issue ID changed to $id, loading data.")
                    loadIssueDetails(id)
                    listenForComments(id)
                } else {
                    _uiState.update { it.copy(error = "Invalid Issue ID", isLoadingIssue = false, isLoadingComments = false) }
                }
            }
        }
    }

    private fun loadIssueDetails(id: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingIssue = true) }
            issueRepository.getIssueById(id).collectLatest { issue ->
                _uiState.update { it.copy(issue = issue, isLoadingIssue = false) }
                // Fetch creator profile once issue (and creatorEmail) is loaded
                fetchCreatorProfile(issue?.creatorEmail)
            }
        }
    }

    private fun fetchCreatorProfile(email: String?) {
        if (email.isNullOrBlank()) {
            _uiState.update { it.copy(creatorProfile = null, isLoadingCreatorProfile = false) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCreatorProfile = true) }
            val result = authRepository.getUserByEmail(email)
            _uiState.update {
                it.copy(
                    creatorProfile = result.getOrNull(),
                    isLoadingCreatorProfile = false,
                    error = if(result.isFailure) "Failed to load creator profile" else it.error
                )
            }
        }
    }

    private fun fetchCommenterProfilesIfNeeded(comments: List<Comment>) {
        viewModelScope.launch {
            if (comments.isEmpty()) return@launch // No need to fetch if no comments

            val currentProfiles = _uiState.value.commenterProfiles
            val neededUids = comments
                .mapNotNull { it.authorUid.takeIf { uid -> uid.isNotBlank() } }
                .distinct()
                .filter { uid -> !currentProfiles.containsKey(uid) }

            if (neededUids.isEmpty()) {
                // Log.d(TAG, "All commenter profiles already available.") // Optional log
                return@launch
            }

            Log.d(TAG, "Need to fetch profiles for UIDs: $neededUids")
            val newlyFetchedProfiles = mutableMapOf<String, UserProfile>()
            var fetchErrorOccurred = false // Track if any error happens

            neededUids.forEach { uid ->
                Log.d(TAG, "Fetching profile for UID: $uid")
                // Use getUserProfile (by UID) from AuthRepository
                val result = authRepository.getUserProfile(uid) // Assumes this fetches by UID
                if (result.isSuccess) {
                    val profile = result.getOrNull()
                    if (profile != null) {
                        Log.d(TAG, "Successfully fetched profile for UID: $uid. Name: ${profile.displayName}, ImageUrl: ${profile.profileImageUrl}")
                        newlyFetchedProfiles[uid] = profile
                    } else {
                        // Found document, but couldn't convert or it was null - less likely with getOrNull
                        Log.w(TAG, "Fetched profile for UID: $uid was null or failed conversion.")
                        // Optionally store a marker that fetching failed for this user?
                        fetchErrorOccurred = true
                    }
                } else {
                    Log.e(TAG, "Failed to fetch profile for commenter UID: $uid", result.exceptionOrNull())
                    fetchErrorOccurred = true
                    // Store null or skip? Skipping for now.
                }
            }

            Log.d(TAG, "Finished fetching batch. Found ${newlyFetchedProfiles.size} new profiles.")

            // Update the state only if new profiles were found or an error occurred
            if (newlyFetchedProfiles.isNotEmpty() || fetchErrorOccurred) {
                _uiState.update { currentState ->
                    Log.d(TAG, "Updating commenterProfiles state map.")
                    currentState.copy(
                        commenterProfiles = currentState.commenterProfiles + newlyFetchedProfiles,
                        error = if (fetchErrorOccurred && currentState.error == null) "Failed to load some commenter details" else currentState.error
                    )
                }
            }
        }
    }

    private fun listenForComments(id: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingComments = true) }
            authRepository.getCommentsFlow(id)
                .catch { e -> _uiState.update { it.copy(error = "Failed to load comments: ${e.message}", isLoadingComments = false) } }
                .collectLatest { comments ->
                    _uiState.update { it.copy(comments = comments, isLoadingComments = false) }
                    fetchCommenterProfilesIfNeeded(comments)
                }
        }
    }

    fun updateNewCommentText(text: String) {
        _uiState.update { it.copy(newCommentText = text) }
    }

    fun sendComment() {
        val currentText = _uiState.value.newCommentText.trim()
        val currentIssueId = issueId.value
        val uid = currentUserId
        val email = currentUserEmail

        if (currentText.isBlank() || currentIssueId <= 0 || uid == null) {
            Log.w(TAG, "Cannot send comment. Blank text, invalid issueId, or user not logged in.")
            return
        }

        val comment = Comment(
            text = currentText,
            authorUid = uid,
            authorEmail = email // Store email for easier display initially
            // timestamp is set by Firestore @ServerTimestamp
        )

        viewModelScope.launch {
            // Optimistically clear input field
            _uiState.update { it.copy(newCommentText = "") }
            val result = authRepository.addComment(currentIssueId, comment)
            if (result.isFailure) {
                Log.e(TAG, "Failed to send comment", result.exceptionOrNull())
                _uiState.update { it.copy(
                    error = "Failed to send comment.",
                    newCommentText = currentText // Restore text on failure
                )}
            } else {
                Log.d(TAG, "Comment sent successfully.")
                // Comment list will update automatically via the Flow
            }
        }
    }

    fun editComment(commentId: String, newText: String) {
        val currentIssueId = issueId.value
        if (currentIssueId <= 0 || newText.isBlank()) return

        viewModelScope.launch {
            val result = authRepository.updateComment(currentIssueId, commentId, newText.trim())
            if (result.isFailure) {
                Log.e(TAG, "Failed to edit comment $commentId", result.exceptionOrNull())
                _uiState.update { it.copy(error = "Failed to update comment.") }
            }
            // List updates via flow
        }
    }

    fun deleteComment(commentId: String) {
        val currentIssueId = issueId.value
        if (currentIssueId <= 0) return

        viewModelScope.launch {
            val result = authRepository.deleteComment(currentIssueId, commentId)
            if (result.isFailure) {
                Log.e(TAG, "Failed to delete comment $commentId", result.exceptionOrNull())
                _uiState.update { it.copy(error = "Failed to delete comment.") }
            }
            // List updates via flow
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }



    // Optional: Factory if needed for SavedStateHandle injection
    // class Factory(private val application: Application, private val savedStateHandle: SavedStateHandle) : ViewModelProvider.Factory { ... }
}
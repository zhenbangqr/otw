package com.zhenbang.otw.issues

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.zhenbang.otw.data.AuthRepository
import com.zhenbang.otw.data.FirebaseAuthRepository
import com.zhenbang.otw.data.Comment
import com.zhenbang.otw.data.UserProfile
import com.zhenbang.otw.database.Issue
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class IssueDiscussionUiState(
    val issue: Issue? = null,
    val comments: List<Comment> = emptyList(),
    val commenterProfiles: Map<String, UserProfile> = emptyMap(),
    val creatorProfile: UserProfile? = null,
    val isLoadingIssue: Boolean = true,
    val isLoadingComments: Boolean = true,
    val isLoadingCreatorProfile: Boolean = false,
    val error: String? = null,
    val newCommentText: String = ""
)

class IssueDiscussionViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val issueRepository: IssueRepository =
        IssueRepository.getRepository(application)
    private val authRepository: AuthRepository = FirebaseAuthRepository()
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    private val TAG = "IssueDiscussionVM"

    val issueId: StateFlow<Int> = savedStateHandle.getStateFlow("issueId", 0)

    private val _uiState = MutableStateFlow(IssueDiscussionUiState())
    val uiState: StateFlow<IssueDiscussionUiState> = _uiState.asStateFlow()

    val currentUserId: String? get() = firebaseAuth.currentUser?.uid
    val currentUserEmail: String? get() = firebaseAuth.currentUser?.email

    init {
        viewModelScope.launch {
            issueId.collectLatest { id ->
                if (id > 0) {
                    Log.d(TAG, "Issue ID changed to $id, loading data.")
                    loadIssueDetails(id)
                    listenForComments(id)
                } else {
                    _uiState.update {
                        it.copy(
                            error = "Invalid Issue ID",
                            isLoadingIssue = false,
                            isLoadingComments = false
                        )
                    }
                }
            }
        }
    }

    private fun loadIssueDetails(id: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingIssue = true) }
            issueRepository.getIssueById(id).collectLatest { issue ->
                _uiState.update { it.copy(issue = issue, isLoadingIssue = false) }
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
                    error = if (result.isFailure) "Failed to load creator profile" else it.error
                )
            }
        }
    }

    private fun fetchCommenterProfilesIfNeeded(comments: List<Comment>) {
        viewModelScope.launch {
            if (comments.isEmpty()) return@launch

            val currentProfiles = _uiState.value.commenterProfiles
            val neededUids = comments
                .mapNotNull { it.authorUid.takeIf { uid -> uid.isNotBlank() } }
                .distinct()
                .filter { uid -> !currentProfiles.containsKey(uid) }

            if (neededUids.isEmpty()) {
                return@launch
            }

            Log.d(TAG, "Need to fetch profiles for UIDs: $neededUids")
            val newlyFetchedProfiles = mutableMapOf<String, UserProfile>()
            var fetchErrorOccurred = false

            neededUids.forEach { uid ->
                Log.d(TAG, "Fetching profile for UID: $uid")
                val result = authRepository.getUserProfile(uid)
                if (result.isSuccess) {
                    val profile = result.getOrNull()
                    if (profile != null) {
                        Log.d(
                            TAG,
                            "Successfully fetched profile for UID: $uid. Name: ${profile.displayName}, ImageUrl: ${profile.profileImageUrl}"
                        )
                        newlyFetchedProfiles[uid] = profile
                    } else {
                        Log.w(TAG, "Fetched profile for UID: $uid was null or failed conversion.")
                        fetchErrorOccurred = true
                    }
                } else {
                    Log.e(
                        TAG,
                        "Failed to fetch profile for commenter UID: $uid",
                        result.exceptionOrNull()
                    )
                    fetchErrorOccurred = true
                }
            }

            Log.d(TAG, "Finished fetching batch. Found ${newlyFetchedProfiles.size} new profiles.")

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
                .catch { e ->
                    _uiState.update {
                        it.copy(
                            error = "Failed to load comments: ${e.message}",
                            isLoadingComments = false
                        )
                    }
                }
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
            authorEmail = email
        )

        viewModelScope.launch {
            _uiState.update { it.copy(newCommentText = "") }
            val result = authRepository.addComment(currentIssueId, comment)
            if (result.isFailure) {
                Log.e(TAG, "Failed to send comment", result.exceptionOrNull())
                _uiState.update {
                    it.copy(
                        error = "Failed to send comment.",
                        newCommentText = currentText
                    )
                }
            } else {
                Log.d(TAG, "Comment sent successfully.")
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
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
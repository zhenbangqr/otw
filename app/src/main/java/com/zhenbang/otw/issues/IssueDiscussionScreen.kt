package com.zhenbang.otw.issues

// --- Imports ---
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi // For combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable // For long press
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items // Import correct items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send // Import Send icon
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel // Ensure correct viewModel import
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.zhenbang.otw.R
import com.zhenbang.otw.data.AuthRepository
import com.zhenbang.otw.data.FirebaseAuthRepository
import com.zhenbang.otw.data.Comment // Import Comment
import com.zhenbang.otw.data.UserProfile
import com.zhenbang.otw.database.Issue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
// --- End Imports ---

fun formatTimestampMillis(timestamp: Long?, pattern: String = "dd MMM yy HH:mm"): String {
    return timestamp?.let { SimpleDateFormat(pattern, Locale.getDefault()).format(Date(it)) } ?: "Unknown time"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class) // Add ExperimentalFoundationApi
@Composable
fun IssueDiscussionScreen(
    navController: NavController,
    // issueId is now handled by ViewModel using SavedStateHandle
    // Inject ViewModel directly (assumes Hilt or default factory with SavedStateHandle)
    viewModel: IssueDiscussionViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentUserId = viewModel.currentUserId // Get current user ID from VM
    val context = LocalContext.current

    // State for edit comment dialog
    var showEditCommentDialog by rememberSaveable { mutableStateOf(false) }
    var commentToEdit by remember { mutableStateOf<Comment?>(null) }

    // State for delete confirmation
    var showDeleteCommentDialog by rememberSaveable { mutableStateOf(false) }
    var commentToDelete by remember { mutableStateOf<Comment?>(null) }


    // Show general errors
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearError() // Clear error after showing
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.issue?.issueTitle ?: "Issue", maxLines = 1) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { /* TODO */ }) { Icon(Icons.Default.MoreVert, "More options") } }
            )
        },
        bottomBar = {
            // Functional Comment Input Bar
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = uiState.newCommentText,
                        onValueChange = { viewModel.updateNewCommentText(it) },
                        placeholder = { Text("Add a comment...") },
                        modifier = Modifier.weight(1f),
                        // Add styling as needed (e.g., maxLines)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { viewModel.sendComment() },
                        enabled = uiState.newCommentText.isNotBlank() // Enable only if text exists
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send comment")
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp),
            // Reverse layout might be useful for chat, but needs correct ordering from query
            // reverseLayout = true
        ) {
            // --- Header Item: Issue Details ---
            item {
                // Display Issue details (Creator Pic/Name/Time, Description)
                // Uses uiState.issue and uiState.creatorProfile
                IssueDetailsHeader(
                    issue = uiState.issue,
                    creatorProfile = uiState.creatorProfile,
                    isLoadingProfile = uiState.isLoadingCreatorProfile
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Discussion", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
            }

            // --- Loading indicator for comments ---
            if (uiState.isLoadingComments && uiState.comments.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }

            // --- Comments List ---
            items(uiState.comments, key = { it.id }) { comment ->
                val isOwnComment = comment.authorUid == currentUserId
                CommentItem(
                    comment = comment,
                    // TODO: Pass actual profile if/when fetched, for now pass null
                    authorProfile = null, // uiState.commenterProfiles[comment.authorUid],
                    isOwnComment = isOwnComment,
                    onEditClick = { commentToEditFromItem ->
                        commentToEdit = commentToEditFromItem
                        showEditCommentDialog = true
                    },
                    onDeleteClick = { // Pass specific delete action
                        if (isOwnComment) {
                            commentToDelete = comment
                            showDeleteCommentDialog = true
                        }
                    }
                )
            }

            // --- Padding at the bottom ---
            item { Spacer(modifier = Modifier.height(16.dp)) }

        } // End LazyColumn

        // --- Edit Comment Dialog ---
        if (showEditCommentDialog && commentToEdit != null) {
            EditCommentDialog(
                initialText = commentToEdit!!.text,
                onDismiss = { showEditCommentDialog = false; commentToEdit = null },
                onSave = { newText ->
                    viewModel.editComment(commentToEdit!!.id, newText)
                    showEditCommentDialog = false
                    commentToEdit = null
                }
            )
        }

        // --- Delete Comment Confirmation Dialog ---
        if (showDeleteCommentDialog && commentToDelete != null) {
            AlertDialog(
                onDismissRequest = { showDeleteCommentDialog = false; commentToDelete = null },
                title = { Text("Delete Comment?") },
                text = { Text("Are you sure you want to delete this comment?") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteComment(commentToDelete!!.id)
                            showDeleteCommentDialog = false
                            commentToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Delete") }
                },
                dismissButton = {
                    Button(onClick = { showDeleteCommentDialog = false; commentToDelete = null }) { Text("Cancel") }
                }
            )
        }

    } // End Scaffold
} // End IssueDiscussionScreen


// --- Separate Composable for Issue Details Header ---
@Composable
fun IssueDetailsHeader(
    issue: Issue?,
    creatorProfile: UserProfile?,
    isLoadingProfile: Boolean
) {
    // ... (Implementation similar to previous response: Row with AsyncImage, Column with Name/Time, Text for Description)
    Spacer(modifier = Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically) { /* ... Creator Image/Name/Time ... */ }
    Spacer(modifier = Modifier.height(12.dp))
    Text(text = issue?.issueDescription ?: "...", style = MaterialTheme.typography.bodyLarge)
    Spacer(modifier = Modifier.height(24.dp))
    HorizontalDivider()
}

// --- Separate Composable for a Single Comment ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CommentItem(
    comment: Comment,
    authorProfile: UserProfile?, // Pass profile when available
    isOwnComment: Boolean,
    onEditClick: (comment: Comment) -> Unit,
    onDeleteClick: (comment: Comment) -> Unit
) {
    var showActions by remember { mutableStateOf(false) }

    Surface( // Add surface for elevation/background if desired
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            // Use combinedClickable for long press action
            .combinedClickable(
                onClick = { showActions = false }, // Hide actions on simple click
                onLongClick = { if (isOwnComment) showActions = true } // Show actions only for own comments on long press
            ),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = if (showActions) 2.dp else 0.dp // Elevate slightly when actions shown
    ) {
        Row(modifier = Modifier.padding(8.dp)) {
            // Author Avatar (Placeholder for now)
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(authorProfile?.profileImageUrl) // Use fetched profile later
                    .placeholder(R.drawable.ic_placeholder_profile)
                    .error(R.drawable.ic_placeholder_profile)
                    .crossfade(true).build(),
                contentDescription = "Commenter Avatar",
                modifier = Modifier.size(36.dp).clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        // Use authorProfile?.displayName later, fallback to email
                        text = authorProfile?.displayName ?: comment.authorEmail ?: "User",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatTimestampMillis(comment.timestamp?.time), // Format timestamp
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = comment.text, style = MaterialTheme.typography.bodyMedium)

                // --- Show Edit/Delete buttons conditionally ---
                AnimatedVisibility(visible = showActions) {
                    Row(modifier = Modifier.padding(top = 4.dp)) {
                        // Edit Button: Call onEditClick callback with the comment
                        TextButton(onClick = {
                            onEditClick(comment) // Pass the comment up
                            showActions = false  // Hide buttons after click
                        }) {
                            Text("Edit")
                        }
                        // Delete Button: Call onDeleteClick callback with the comment
                        TextButton(onClick = {
                            onDeleteClick(comment) // Pass the comment up
                            showActions = false   // Hide buttons after click
                        }) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                // --- End Edit/Delete ---
            }
        }
    }
}


// --- Edit Comment Dialog Composable ---
@Composable
fun EditCommentDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember(initialText) { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Comment") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onSave(text.trim()) }, enabled = text.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Cancel") }
        }
    )
}


// --- Update AppNavigation.kt ---
// Ensure IssueDiscussionViewModel is correctly provided in the NavHost composable definition
// for the ISSUE_DISCUSSION_ROUTE. Example:
/*
composable(
    route = AppDestinations.ISSUE_DISCUSSION_ROUTE,
    arguments = listOf(navArgument(AppDestinations.ISSUE_ID_ARG) { type = NavType.IntType })
) { backStackEntry ->
    // ViewModel will get issueId from SavedStateHandle automatically if configured
    IssueDiscussionScreen(navController = navController) // Pass only NavController
}
*/
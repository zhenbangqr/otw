package com.zhenbang.otw.issues

// --- Imports ---
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.zhenbang.otw.R
import com.zhenbang.otw.data.Comment
import com.zhenbang.otw.data.UserProfile
import com.zhenbang.otw.database.Issue
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.zhenbang.otw.departments.Screen as DepartmentScreen
// --- End Imports ---

fun formatTimestampMillis(timestamp: Long?, pattern: String = "dd MMM yy HH:mm"): String {
    return timestamp?.let { SimpleDateFormat(pattern, Locale.getDefault()).format(Date(it)) }
        ?: "Unknown time"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun IssueDiscussionScreen(
    navController: NavController,
    viewModel: IssueDiscussionViewModel = viewModel(),
) {

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentUserId = viewModel.currentUserId
    val currentUserEmail = viewModel.currentUserEmail
    val context = LocalContext.current
    val issue = uiState.issue

    val issueViewModel: IssueViewModel = viewModel(factory = IssueViewModel.Factory(context))

    var showEditCommentDialog by rememberSaveable { mutableStateOf(false) }
    var commentToEdit by remember { mutableStateOf<Comment?>(null) }
    var showDeleteCommentDialog by rememberSaveable { mutableStateOf(false) }
    var commentToDelete by remember { mutableStateOf<Comment?>(null) }
    var showIssueOptionsMenu by rememberSaveable { mutableStateOf(false) }
    var showDeleteIssueDialog by rememberSaveable { mutableStateOf(false) }

    val creatorProfile = uiState.creatorProfile

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val showScrollToTopButton by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    val isCurrentUserTheCreator = remember(currentUserEmail, issue?.creatorEmail) {
        issue?.creatorEmail != null && issue.creatorEmail == currentUserEmail
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.issue?.issueTitle ?: "Issue", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "Back"
                        )
                    }
                },
                actions = {
                    if (isCurrentUserTheCreator) {
                        Box {
                            IconButton(onClick = { showIssueOptionsMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Issue options")
                            }
                            DropdownMenu(
                                expanded = showIssueOptionsMenu,
                                onDismissRequest = { showIssueOptionsMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Edit Issue") },
                                    onClick = {
                                        showIssueOptionsMenu = false
                                        if (issue != null) {
                                            val route = DepartmentScreen.AddEditIssue.createRoute(
                                                departmentId = issue.departmentId,
                                                issueId = issue.issueId
                                            )
                                            navController.navigate(route)
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "Cannot edit, issue data missing.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete Issue") },
                                    onClick = {
                                        showIssueOptionsMenu = false
                                        showDeleteIssueDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = uiState.newCommentText,
                        onValueChange = { viewModel.updateNewCommentText(it) },
                        placeholder = { Text("Add a comment...") },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(max = 150.dp),
                        maxLines = 10
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { viewModel.sendComment() },
                        enabled = uiState.newCommentText.isNotBlank()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send comment")
                    }
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = showScrollToTopButton,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        coroutineScope.launch {
                            listState.animateScrollToItem(index = 0)
                        }
                    },
                    modifier = Modifier.padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(50.dp),
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        hoveredElevation = 0.dp,
                        focusedElevation = 0.dp
                    )
                ) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Scroll to Top")
                }
            }
        },
        floatingActionButtonPosition = FabPosition.End
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp)
        ) {
            item {
                IssueDetailsHeader(
                    issue = uiState.issue,
                    creatorProfile = creatorProfile,
                    isLoadingProfile = uiState.isLoadingCreatorProfile
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Discussion",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (uiState.isLoadingComments && uiState.comments.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            items(uiState.comments, key = { it.id }) { comment ->
                val isOwnComment = comment.authorUid == currentUserId
                CommentItem(
                    comment = comment,
                    authorProfile = uiState.commenterProfiles[comment.authorUid],
                    isOwnComment = isOwnComment,
                    onEditClick = { commentToEditFromItem ->
                        commentToEdit = commentToEditFromItem
                        showEditCommentDialog = true
                    },
                    onDeleteClick = {
                        if (isOwnComment) {
                            commentToDelete = comment
                            showDeleteCommentDialog = true
                        }
                    }
                )
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

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
                    Button(onClick = {
                        showDeleteCommentDialog = false; commentToDelete = null
                    }) { Text("Cancel") }
                }
            )
        }

        if (showDeleteIssueDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteIssueDialog = false },
                title = { Text("Delete Issue?") },
                text = { Text("Are you sure you want to permanently delete this issue and all its comments?") },
                confirmButton = {
                    Button(
                        onClick = {
                            if (issue != null) {
                                issueViewModel.deleteIssue(issue)
                                showDeleteIssueDialog = false
                                navController.popBackStack()
                            } else {
                                Toast.makeText(
                                    context,
                                    "Cannot delete, issue data missing.",
                                    Toast.LENGTH_SHORT
                                ).show()
                                showDeleteIssueDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Delete Issue") }
                },
                dismissButton = {
                    Button(onClick = { showDeleteIssueDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
fun IssueDetailsHeader(
    issue: Issue?,
    creatorProfile: UserProfile?,
    isLoadingProfile: Boolean
) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(creatorProfile?.profileImageUrl)
            .placeholder(R.drawable.ic_placeholder_profile)
            .error(R.drawable.ic_placeholder_profile)
            .build(),
        contentDescription = "Creator Avatar",
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    )
    Spacer(modifier = Modifier.width(12.dp))
    Column {
        Text(
            text = if (isLoadingProfile) "Loading..." else creatorProfile?.displayName
                ?: "Unknown User",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Posted ${formatTimestampMillis(issue?.creationTimestamp)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = issue?.issueDescription ?: "Loading description...",
        style = MaterialTheme.typography.bodyLarge
    )
    Spacer(modifier = Modifier.height(24.dp))
    HorizontalDivider()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CommentItem(
    comment: Comment,
    authorProfile: UserProfile?,
    isOwnComment: Boolean,
    onEditClick: (comment: Comment) -> Unit,
    onDeleteClick: (comment: Comment) -> Unit
) {
    var showActions by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .combinedClickable(
                onClick = { showActions = false },
                onLongClick = { if (isOwnComment) showActions = true }
            ),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = if (showActions) 2.dp else 0.dp
    ) {
        Row(modifier = Modifier.padding(8.dp)) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(authorProfile?.profileImageUrl)
                    .placeholder(R.drawable.ic_placeholder_profile)
                    .error(R.drawable.ic_placeholder_profile)
                    .crossfade(true)
                    .build(),
                contentDescription = "Commenter Avatar",
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = authorProfile?.displayName ?: comment.authorEmail ?: "User",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatTimestampMillis(comment.timestamp?.time),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = comment.text, style = MaterialTheme.typography.bodyMedium)

                AnimatedVisibility(visible = showActions) {
                    Row(modifier = Modifier.padding(top = 4.dp)) {
                        TextButton(onClick = {
                            onEditClick(comment)
                            showActions = false
                        }) {
                            Text("Edit")
                        }
                        TextButton(onClick = {
                            onDeleteClick(comment)
                            showActions = false
                        }) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

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
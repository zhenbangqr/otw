package com.zhenbang.otw // Or your features/messaging package

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send // Send Icon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel // Import viewModel composable
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth // Import FirebaseAuth
import com.zhenbang.otw.messagemodel.ChatMessage // Import data class
import com.zhenbang.otw.viewmodel.MessagingViewModel // Import ViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagingScreen(
    navController: NavController,
    userIdToChatWith: String
) {
    // Get current user ID (handle potential null case)
    val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid

    // Handle case where current user isn't logged in (shouldn't happen if MainActivity logic is correct)
    if (currentUserUid == null) {
        Log.e("MessagingScreen", "Current user is null, cannot proceed.")
        // Optionally show an error message and navigate back
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Error: You are not logged in.")
        }
        LaunchedEffect(Unit) { navController.popBackStack() } // Go back if user is null
        return // Stop rendering further
    }

    // Create ViewModel using the Factory
    val viewModel: MessagingViewModel = viewModel(
        factory = MessagingViewModel.provideFactory(
            currentUserUid = currentUserUid,
            otherUserUid = userIdToChatWith
        )
    )

    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val partnerName by viewModel.partnerName.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState() // State for controlling LazyColumn scroll
    val coroutineScope = rememberCoroutineScope()

    // Effect to scroll to bottom when messages list changes
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            // Scroll to the last item
            listState.animateScrollToItem(index = messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(partnerName) }, // Use name from ViewModel
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
            // Don't add extra padding here if MessageInput adds its own
        ) {
            // Display errors if any
            error?.let {
                Text(
                    text = "Error: $it",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // Message List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // Takes up available space
                    .padding(horizontal = 8.dp),
                contentPadding = PaddingValues(vertical = 8.dp) // Padding for top/bottom of list
            ) {
                items(messages, key = { it.messageId }) { message -> // Use messageId as key if available
                    MessageBubble(
                        message = message,
                        isCurrentUserMessage = message.senderId == currentUserUid
                    )
                }
            }

            // Input Field and Send Button
            MessageInput(
                text = messageText,
                onTextChanged = { messageText = it },
                onSendClick = {
                    viewModel.sendMessage(messageText)
                    messageText = "" // Clear input field after sending
                }
            )
        }
    }
}

// Composable for a single message bubble
@Composable
fun MessageBubble(message: ChatMessage, isCurrentUserMessage: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isCurrentUserMessage) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium, // Rounded corners
            color = if (isCurrentUserMessage) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.padding( // Add padding around the bubble itself if needed
                start = if (isCurrentUserMessage) 64.dp else 0.dp, // Indent opposite side
                end = if (isCurrentUserMessage) 0.dp else 64.dp    // Indent opposite side
            )
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                color = if (isCurrentUserMessage) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
            )
            // TODO: Add timestamp display below text if desired
        }
    }
}

// Composable for the input field + send button row
@Composable
fun MessageInput(
    text: String,
    onTextChanged: (String) -> Unit,
    onSendClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp), // Padding around the input row
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChanged,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Type a message...") },
            // Add styling as needed (shape, etc.)
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(
            onClick = onSendClick,
            enabled = text.isNotBlank() // Enable button only if text exists
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send message",
                tint = if (text.isNotBlank()) MaterialTheme.colorScheme.primary else Color.Gray // Change tint when enabled
            )
        }
    }
}
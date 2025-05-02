package com.zhenbang.otw // Or your UI package

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack // Import the AutoMirrored ArrowBack icon
import androidx.compose.material.icons.filled.AccountCircle // Example Icon
import androidx.compose.material.icons.filled.AddComment // Icon for new chat FAB
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember // Import remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.zhenbang.otw.viewmodel.ChatHistoryItem
import com.zhenbang.otw.viewmodel.ChatHistoryViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatHistoryScreen(
    navController: NavController,
    chatHistoryViewModel: ChatHistoryViewModel = viewModel()
) {
    val chatHistory by chatHistoryViewModel.chatHistory.collectAsState()
    val isLoading by chatHistoryViewModel.isLoading.collectAsState()
    val error by chatHistoryViewModel.error.collectAsState()

    // SimpleDateFormat for formatting timestamp (adjust pattern as needed)
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    val todayCal = remember { Calendar.getInstance() }
    val yesterdayCal = remember { Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) } }


    fun formatTimestamp(date: Date?): String {
        if (date == null) return ""
        val messageCal = Calendar.getInstance().apply { time = date }

        return when {
            messageCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                    messageCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR) -> timeFormat.format(date)
            messageCal.get(Calendar.YEAR) == yesterdayCal.get(Calendar.YEAR) &&
                    messageCal.get(Calendar.DAY_OF_YEAR) == yesterdayCal.get(Calendar.DAY_OF_YEAR) -> "Yesterday"
            else -> dateFormat.format(date)
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chats") },
                // *** ADDED NAVIGATION ICON ***
                navigationIcon = {
                    // Add IconButton for click handling
                    IconButton(onClick = { navController.navigateUp() }) {
                        // Use AutoMirrored ArrowBack for LTR/RTL support
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back" // For accessibility
                        )
                    }
                }
                // *** END OF ADDED CODE ***
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    // Navigate to the UserListScreen to start a new chat
                    navController.navigate(Routes.USER_LIST)
                }
            ) {
                Icon(Icons.Filled.AddComment, contentDescription = "New Chat")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading && chatHistory.isEmpty() -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                error != null -> {
                    Text(
                        text = "Error: $error",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center).padding(16.dp)
                    )
                }
                chatHistory.isEmpty() && !isLoading -> {
                    Text(
                        text = "No chats yet. Start a new one!", // Updated text
                        modifier = Modifier.align(Alignment.Center).padding(16.dp)
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(chatHistory, key = { it.chatId }) { chatItem ->
                            ChatHistoryItemRow(
                                item = chatItem,
                                formatTimestamp = ::formatTimestamp,
                                onClick = {
                                    navController.navigate(Routes.messagingWithUser(chatItem.otherUserId))
                                }
                            )
                            Divider(modifier = Modifier.padding(start = 72.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatHistoryItemRow(
    item: ChatHistoryItem,
    formatTimestamp: (Date?) -> String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.AccountCircle,
            contentDescription = "User profile picture",
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = item.otherUserName ?: "User ${item.otherUserId.take(6)}...",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.lastMessagePreview ?: "...", // Used lastMessagePreview here
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = formatTimestamp(item.lastMessageTimestamp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
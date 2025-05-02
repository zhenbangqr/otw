package com.zhenbang.otw // Or your UI package

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack // Import AutoMirrored ArrowBack
import androidx.compose.material.icons.filled.AccountCircle // Example Icon
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.zhenbang.otw.viewmodel.UserInfo
import com.zhenbang.otw.viewmodel.UserListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserListScreen(
    navController: NavController,
    userListViewModel: UserListViewModel = viewModel()
) {
    val users by userListViewModel.users.collectAsState()
    val isLoading by userListViewModel.isLoading.collectAsState()
    val error by userListViewModel.error.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select User to Chat") },
                // *** ADDED NAVIGATION ICON ***
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) { // Use navigateUp for consistency
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack, // Use AutoMirrored icon
                            contentDescription = "Back" // Accessibility description
                        )
                    }
                }
                // *** END OF ADDED CODE ***
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading -> {
                    // Show loading indicator
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                error != null -> {
                    // Show error message
                    Text(
                        text = "Error: $error",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center).padding(16.dp)
                    )
                }
                users.isEmpty() -> {
                    // Show message if no users found (excluding self)
                    Text(
                        text = "No other users found.",
                        modifier = Modifier.align(Alignment.Center).padding(16.dp)
                    )
                }
                else -> {
                    // Display the list of users
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp) // Added padding for list items
                    ) {
                        items(users, key = { it.uid }) { user ->
                            UserListItemRow(
                                user = user,
                                onClick = {
                                    // Navigate to MessagingScreen with the selected user's ID
                                    navController.navigate(Routes.messagingWithUser(user.uid)) // Use your route helper
                                }
                            )
                            Divider() // Add divider between items
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UserListItemRow(
    user: UserInfo,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Optional: Add user avatar/icon here
        Icon(
            imageVector = Icons.Default.AccountCircle,
            contentDescription = "User profile picture", // Change if you have actual images
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = user.name ?: "User ${user.uid.take(6)}...", // Display name or fallback
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
package com.zhenbang.otw.ui.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.zhenbang.otw.R // Import R
import com.zhenbang.otw.data.model.UserProfile
import com.zhenbang.otw.ui.viewmodel.PrivacyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(
    navController: NavController,
    privacyViewModel: PrivacyViewModel = viewModel() // Obtain ViewModel
) {
    val uiState by privacyViewModel.uiState.collectAsState()
    val searchQuery = privacyViewModel.searchQuery // Get query from VM
    val context = LocalContext.current

    // Show errors as Toasts
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            privacyViewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Block Users") },
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
                .padding(horizontal = 16.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { privacyViewModel.updateSearchQuery(it) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                label = { Text("Search by name or email") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                singleLine = true
            )

            // Loading Indicator
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) { // Make column scrollable and take space

                    // Search Results Section (Only show if query is not blank)
                    if (searchQuery.isNotBlank()) {
                        item {
                            ListHeader("Search Results")
                        }
                        if (uiState.searchResults.isEmpty()) {
                            item { EmptyResultMessage("No users found matching your search.") }
                        } else {
                            items(uiState.searchResults, key = { it.uid }) { user ->
                                UserListItem(user = user, isBlocked = false) { // Action button is "Block"
                                    privacyViewModel.blockUser(user.uid)
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(16.dp)) } // Space before blocked list
                    }


                    // Blocked Users Section
                    item {
                        ListHeader("Blocked Users")
                    }
                    if (uiState.blockedUserProfiles.isEmpty()) {
                        item { EmptyResultMessage("You haven't blocked any users.") }
                    } else {
                        items(uiState.blockedUserProfiles, key = { it.uid }) { user ->
                            UserListItem(user = user, isBlocked = true) { // Action button is "Unblock"
                                privacyViewModel.unblockUser(user.uid)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ListHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun EmptyResultMessage(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
    )
}


@Composable
fun UserListItem(
    user: UserProfile,
    isBlocked: Boolean, // Determines button text/action
    onActionButtonClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(user.profileImageUrl)
                .crossfade(true)
                .placeholder(R.drawable.ic_placeholder_profile)
                .error(R.drawable.ic_placeholder_profile)
                .build(),
            contentDescription = user.displayName ?: "User Picture",
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(user.displayName ?: "Unknown User", style = MaterialTheme.typography.bodyLarge)
            Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = onActionButtonClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isBlocked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp) // Smaller padding
        ) {
            Text(if (isBlocked) "Unblock" else "Block")
        }
    }
    HorizontalDivider()
}
package com.zhenbang.otw.ui.screen // Adjust package as needed

// --- Imports ---
// --- Import your specific classes ---
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
// ... other Material Icons imports ...
import androidx.compose.material.icons.filled.AddComment // Keep this for FAB
import androidx.compose.material3.Card // Import Card
import androidx.compose.material3.CardDefaults // Import CardDefaults for elevation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhenbang.otw.ui.viewmodel.DepartmentViewModel
import com.zhenbang.otw.ui.component.NewsSection
import com.zhenbang.otw.ui.component.WeatherSection
import com.zhenbang.otw.ui.viewmodel.NewsViewModel
import com.zhenbang.otw.ui.viewmodel.WeatherViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.zhenbang.otw.R
import com.zhenbang.otw.ui.viewmodel.ProfileViewModel
import androidx.navigation.NavController
import com.zhenbang.otw.Routes
// Import your ChatHistoryViewModel and ChatHistoryItem
import com.zhenbang.otw.ui.viewmodel.ChatHistoryViewModel
import com.zhenbang.otw.ui.viewmodel.ChatHistoryItem // Make sure this is imported
import com.zhenbang.otw.ui.viewmodel.LiveLocationViewModel
import java.text.SimpleDateFormat // For formatting date/time
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.clickable // Import clickable
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sort
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    navController: NavController,
    newsViewModel: NewsViewModel = viewModel(),
    weatherViewModel: WeatherViewModel = viewModel(),
    departmentViewModel: DepartmentViewModel = viewModel(factory = DepartmentViewModel.Factory(LocalContext.current)),
    profileViewModel: ProfileViewModel = viewModel(),
    liveLocationViewModel: LiveLocationViewModel = viewModel(),
    chatHistoryViewModel: ChatHistoryViewModel = viewModel(), // ViewModel is already here
    onNavigateToProfile: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToDepartmentDetails: (departmentId: Int, departmentName: String) -> Unit,
    onNavigateToMessaging: (otherUserId: String) -> Unit
) {
    val profileUiState by profileViewModel.uiState.collectAsState()
    val userProfile = profileUiState.userProfile
    var selectedContent by rememberSaveable { mutableStateOf("Dashboard") }
    var isWorkspaceGridView by rememberSaveable { mutableStateOf(true) }
    var isSortAscending by rememberSaveable { mutableStateOf(true) }

    // *** Collect chat history state ***
    val chatHistoryState by chatHistoryViewModel.chatHistory.collectAsState()
    // Get the most recent chat item (ViewModel already sorts it)
    val lastChatItem = chatHistoryState.firstOrNull()

    val bottomNavItems = listOf("Dashboard", "Location", "Workspaces", "Chat")

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Clickable Profile Picture
                        IconButton(onClick = onNavigateToProfile) { // Keep IconButton for click area
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(userProfile?.profileImageUrl)
                                    .crossfade(true)
                                    .placeholder(R.drawable.ic_placeholder_profile) // Placeholder
                                    .error(R.drawable.ic_placeholder_profile)       // Error placeholder
                                    .build(),
                                contentDescription = "Profile",
                                modifier = Modifier
                                    .size(36.dp) // Adjust size as needed for TopAppBar
                                    .clip(CircleShape)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                },
                navigationIcon = {},
                actions = {
                    if (selectedContent == "Workspaces") {
                        IconButton(onClick = { isSortAscending = !isSortAscending }) {
                            Icon(Icons.Filled.Sort, contentDescription = "Sort")
                        }
                        IconButton(onClick = { isWorkspaceGridView = !isWorkspaceGridView }) {
                            Icon(
                                imageVector = if (isWorkspaceGridView) Icons.Filled.List else Icons.Filled.GridView,
                                contentDescription = if (isWorkspaceGridView) "Switch to List View" else "Switch to Grid View"
                            )
                        }
                    }
                    if (selectedContent == "Dashboard") {
                        IconButton(onClick = {
                            weatherViewModel.fetchWeather("Kuala Lumpur") // Refresh default location
                            newsViewModel.fetchNews() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                bottomNavItems.forEach { screenName ->
                    val icon = when (screenName) {
                        "Dashboard" -> Icons.Filled.Home
                        "Location" -> Icons.Filled.LocationOn
                        "Workspaces" -> Icons.Filled.Edit
                        "Chat" -> Icons.Filled.Email
                        else -> Icons.Filled.Add // Should not happen with current list
                    }
                    NavigationBarItem(
                        icon = { Icon(icon, contentDescription = screenName) },
                        label = { Text(screenName) },
                        selected = (selectedContent == screenName),
                        onClick = {
                            selectedContent = screenName
                            Log.d("HomeScreen", "Bottom nav clicked: $screenName")
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            if (selectedContent == "Chat") {
                FloatingActionButton(
                    onClick = { navController.navigate(Routes.USER_LIST) }
                ) {
                    Icon(Icons.Filled.AddComment, contentDescription = "New Chat")
                }
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            tonalElevation = 1.dp
        ) {
            when (selectedContent) {
                "Dashboard" -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Hi, ${userProfile?.displayName ?: "User"}",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(top = 10.dp, bottom = 24.dp, start = 10.dp)
                                .align(Alignment.Start)
                        )
                        WeatherSection(weatherViewModel = weatherViewModel)
                        Spacer(modifier = Modifier.height(16.dp)) // Spacing

                        // *** Add the Last Message Preview Section ***
                        LastMessagePreviewSection(
                            lastChatItem = lastChatItem, // Pass the latest chat item
                            onNavigateToMessaging = onNavigateToMessaging // Pass the navigation callback
                        )

                        Spacer(modifier = Modifier.height(24.dp)) // Spacing
                        NewsSection(newsViewModel = newsViewModel)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
                "Workspaces" -> {
                    WorkspaceContent(
                        modifier = Modifier.fillMaxSize(),
                        departmentViewModel = departmentViewModel,
                        isGridView = isWorkspaceGridView,
                        onNavigateToDepartmentDetails = onNavigateToDepartmentDetails,
                        isSortAscending = isSortAscending
                    )
                }
                "Location" -> {
                    LiveLocationScreen(
                        navController = navController,
                        liveLocationViewModel = liveLocationViewModel
                    )
                }
                "Chat" -> {
                    ChatHistoryContent(
                        modifier = Modifier.fillMaxSize(),
                        chatHistoryViewModel = chatHistoryViewModel,
                        onNavigateToMessaging = onNavigateToMessaging
                    )
                }
            }
        }
    }
}

// --- New Composable Function for the Last Message Preview ---

@Composable
fun LastMessagePreviewSection(
    modifier: Modifier = Modifier,
    lastChatItem: ChatHistoryItem?,
    onNavigateToMessaging: (otherUserId: String) -> Unit
) {
    // Only display if there is a recent chat item
    if (lastChatItem != null && lastChatItem.otherUserId.isNotEmpty()) {
        Card(
            modifier = modifier
                .fillMaxSize()
                .clickable { onNavigateToMessaging(lastChatItem.otherUserId) }, // Make card clickable
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp) // Optional elevation
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Profile Picture
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(lastChatItem.otherUserProfileImageUrl)
                        .crossfade(true)
                        .placeholder(R.drawable.ic_placeholder_profile) // Reuse placeholder
                        .error(R.drawable.ic_placeholder_profile)       // Reuse error placeholder
                        .build(),
                    contentDescription = "${lastChatItem.otherUserName ?: "User"}'s profile picture",
                    modifier = Modifier
                        .size(40.dp) // Adjust size as needed
                        .clip(CircleShape)
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Column for Name and Message Preview
                Column(modifier = Modifier.weight(1f)) { // Takes remaining space
                    Text(
                        text = lastChatItem.otherUserName ?: "User...",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = lastChatItem.lastMessagePreview ?: "", // Show preview or empty string
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, // Slightly muted color
                        maxLines = 1 // Ensure preview doesn't wrap excessively
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Timestamp (Formatted) - Aligned to the end
                Text(
                    text = formatTimestampForPreview(lastChatItem.lastMessageTimestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        // Optionally, show a placeholder if there are no chats yet
        // Text("No recent chats.", modifier = modifier.padding(vertical = 8.dp))
        // Or just display nothing by having no `else` block
    }
}

// --- Helper Function to Format Timestamp ---

// Simple formatter, you can make this more sophisticated (e.g., "Yesterday", "5 min ago")
fun formatTimestampForPreview(timestamp: Date?): String {
    if (timestamp == null) return ""
    // Example format: "10:35 AM" or "May 05" if it's not today
    val todayFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val otherDayFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
    val cal = Calendar.getInstance()
    val todayMillis = cal.timeInMillis
    cal.time = timestamp
    val timestampMillis = cal.timeInMillis

    // Simple check if it's today (ignoring time zones for simplicity here)
    val isToday = android.text.format.DateUtils.isToday(timestampMillis)

    return if (isToday) {
        todayFormat.format(timestamp)
    } else {
        otherDayFormat.format(timestamp)
    }
}

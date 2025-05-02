package com.zhenbang.otw.ui.screen // Adjust package as needed

// --- Imports ---
// --- Import your specific classes ---
import android.util.Log
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhenbang.otw.departments.DepartmentViewModel
import com.zhenbang.otw.departments.WorkspaceContent
import com.zhenbang.otw.ui.component.NewsSection
import com.zhenbang.otw.ui.component.WeatherSection
import com.zhenbang.otw.ui.theme.OnTheWayTheme
import com.zhenbang.otw.ui.viewmodel.NewsViewModel
import com.zhenbang.otw.ui.viewmodel.WeatherViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.zhenbang.otw.R
import com.zhenbang.otw.profile.ProfileViewModel


@OptIn(ExperimentalMaterial3Api::class) // For TopAppBar, Scaffold, FilterChip
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    newsViewModel: NewsViewModel = viewModel(),
    weatherViewModel: WeatherViewModel = viewModel(),
    departmentViewModel: DepartmentViewModel = viewModel(factory = DepartmentViewModel.Factory(LocalContext.current)),
    profileViewModel: ProfileViewModel = viewModel(),
    onNavigateToProfile: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onNavigateBottomBar: (String) -> Unit = {},
    onNavigateToDepartmentDetails: (departmentId: Int, departmentName: String) -> Unit,
) {
    val profileUiState by profileViewModel.uiState.collectAsState()
    val userProfile = profileUiState.userProfile
    var selectedContent by rememberSaveable { mutableStateOf("Dashboard") }

    var isWorkspaceGridView by rememberSaveable { mutableStateOf(true) }
    var isSortAscending by rememberSaveable { mutableStateOf(true) }
    // Modify the displayed list based on the sorting state

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
                    IconButton(onClick = onNavigateToNotifications) {
                        Icon(Icons.Filled.Notifications, contentDescription = "Notifications")
                    }
                }
                // Optional: Add colors = TopAppBarDefaults.topAppBarColors(...)
            )
        },
        bottomBar = {
            NavigationBar {
                bottomNavItems.forEach { screenName ->
                    val icon = when (screenName) {
                        "Dashboard" -> Icons.Filled.Home
                        "Location" -> Icons.Filled.LocationOn
                        "Workspaces" -> Icons.Filled.Edit // Changed icon
                        "Chat" -> Icons.Filled.Email // Changed icon
                        else -> Icons.Filled.Add // Changed icon
                    }
                    NavigationBarItem(
                        icon = { Icon(icon, contentDescription = screenName) },
                        label = { Text(screenName) },
                        selected = (selectedContent == screenName), // Selection based on content state
                        onClick = {
                            selectedContent = screenName // Update the content state
                            Log.d("HomeScreen", "Bottom nav clicked: $screenName")
                            // Navigation for other items might still be needed via a callback if they go to separate screens
                            // if (screenName != "Dashboard" && screenName != "Workspaces") {
                            //    onNavigateBottomBar(screenName) // Call original callback for items that navigate away
                            // }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        // --- Add Surface for background shadow ---
        Surface(
            modifier = Modifier
                .padding(innerPadding) // Apply Scaffold padding here
                .fillMaxSize(),
            tonalElevation = 1.dp // Add slight elevation for shadow effect
        ) {
            when (selectedContent) {
                "Dashboard" -> {
                    // Show original Dashboard content (Weather/News)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            // Use display name from profile state
                            text = "Hi, ${userProfile?.displayName ?: "User"}",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 10.dp, bottom = 24.dp, start = 10.dp)
                                .align(Alignment.Start) // Align text to the start
                        )
                        //WeatherSection(weatherViewModel = weatherViewModel)
                        Spacer(modifier = Modifier.height(24.dp))
                        //NewsSection(newsViewModel = newsViewModel)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
                "Workspaces" -> {
                    // Show the new WorkspaceContent composable
                    WorkspaceContent(
                        modifier = Modifier.fillMaxSize(),
                        departmentViewModel = departmentViewModel,
                        isGridView = isWorkspaceGridView, // Pass the view mode state
                        onNavigateToDepartmentDetails = onNavigateToDepartmentDetails, // Pass the callback down
                        isSortAscending = isSortAscending
                    )
                }
                "Location" -> {
                    // Placeholder for Location Content
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center){ Text("Location Content Area")}
                }
                "Chat" -> {
                    // Placeholder for Chat Content
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center){ Text("Chat Content Area")}
                }
            }
        }
    }
}


// Preview function might need adjustments if ViewModels require specific setup
@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    OnTheWayTheme { // Ensure your theme is applied
        HomeScreen(onNavigateToDepartmentDetails = { _, _ -> })
    }
}

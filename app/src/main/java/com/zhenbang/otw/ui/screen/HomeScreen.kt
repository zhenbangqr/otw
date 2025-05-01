package com.zhenbang.otw.ui.screen // Adjust package as needed

// --- Imports ---
// --- Import your specific classes ---
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhenbang.otw.ui.component.NewsSection
import com.zhenbang.otw.ui.component.WeatherSection
import com.zhenbang.otw.ui.theme.OnTheWayTheme
import com.zhenbang.otw.ui.viewmodel.NewsViewModel
import com.zhenbang.otw.ui.viewmodel.WeatherViewModel


@OptIn(ExperimentalMaterial3Api::class) // For TopAppBar, Scaffold, FilterChip
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    newsViewModel: NewsViewModel = viewModel(),
    weatherViewModel: WeatherViewModel = viewModel(),
    onNavigateToProfile: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateBottomBar: (String) -> Unit = {}
) {
    var selectedBottomNavItem by remember { mutableStateOf("Dashboard") }
    val bottomNavItems = listOf("Dashboard", "Location", "Workspaces", "Chat")

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { /* No title */ },
                navigationIcon = {
                    IconButton(onClick = onNavigateToProfile) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = "Profile")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToNotifications) {
                        Icon(Icons.Filled.Notifications, contentDescription = "Notifications")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
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
                        selected = (selectedBottomNavItem == screenName),
                        onClick = {
                            selectedBottomNavItem = screenName
                            onNavigateBottomBar(screenName)
                            Log.d("HomeScreen", "Bottom nav clicked: $screenName")
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
            // Main content area Column is now inside the Surface
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()) // Simple scrolling
                    .padding(horizontal = 16.dp), // Add horizontal padding for content
                horizontalAlignment = Alignment.Start // Center children like WeatherSection
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Welcome Text
                Text(
                    text = "Hi, Zhen Bang",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 24.dp)
                        .align(Alignment.Start) // Align welcome text to the start
                )

                // --- Weather Section ---
                WeatherSection(weatherViewModel = weatherViewModel)

                Spacer(modifier = Modifier.height(24.dp)) // Add space between sections

                // --- News Section ---
                NewsSection(newsViewModel = newsViewModel)

                Spacer(modifier = Modifier.height(16.dp)) // Add space at the bottom
            }
        }
    }
}


// Preview function might need adjustments if ViewModels require specific setup
@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    OnTheWayTheme { // Ensure your theme is applied
        HomeScreen()
    }
}

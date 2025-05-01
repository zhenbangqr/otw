package com.zhenbang.otw.ui.screen // Adjust package as needed

// --- Imports ---
// *** ADDED back ViewModelNews import ***
// *** ADDED back NewsSection import ***
//import com.zhenbang.otw.news.ui.NewsSection // Import your NewsSection composable
// Import your WeatherDetails composable
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhenbang.otw.ui.theme.OnTheWayTheme
import com.zhenbang.otw.ui.viewmodel.ViewModelNews
import com.zhenbang.otw.ui.viewmodel.ViewModelWeather
import com.zhenbang.otw.util.UiState

@OptIn(ExperimentalMaterial3Api::class) // For TopAppBar, Scaffold
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    // *** ADDED back viewModelNews parameter ***
    viewModelNews: ViewModelNews = viewModel(),
    viewModelWeather: ViewModelWeather = viewModel(),
    // Placeholder navigation actions (replace with actual navigation logic)
    onNavigateToProfile: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateBottomBar: (String) -> Unit = {} // Argument could be route name
) {
    // State for the selected item in the bottom navigation bar
    var selectedBottomNavItem by remember { mutableStateOf("Dashboard") } // Default selection
    val bottomNavItems = listOf("Dashboard", "Location", "Workspaces", "Chat") // Example items

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {},
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
            )
        },
        bottomBar = {
            NavigationBar { // Material 3 Bottom Navigation
                bottomNavItems.forEach { screenName ->
                    val icon = when (screenName) {
                        "Dashboard" -> Icons.Filled.Home
                        "Location" -> Icons.Filled.LocationOn
                        "Workspaces" -> Icons.Filled.Edit
                        "Chat" -> Icons.Filled.Email
                        else -> Icons.Filled.Add
                    }
                    NavigationBarItem(
                        icon = { Icon(icon, contentDescription = screenName) },
                        label = { Text(screenName) },
                        selected = (selectedBottomNavItem == screenName),
                        onClick = {
                            selectedBottomNavItem = screenName
                            onNavigateBottomBar(screenName) // Trigger navigation
                            Log.d("HomeScreen", "Bottom nav clicked: $screenName")
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        // Main content area
        Column(
            modifier = Modifier
                .padding(innerPadding) // Apply padding from Scaffold
                .fillMaxSize()
                .verticalScroll(rememberScrollState()) // Simple scrolling
                .padding(horizontal = 16.dp) // Add horizontal padding for content
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Welcome Text
            Text(
                text = "Hi, Zhen Bang", // Replace with dynamic user name if available
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // --- Weather Section ---
            WeatherSection(viewModelWeather = viewModelWeather)

            // --- ADDED back News Section ---
            Spacer(modifier = Modifier.height(24.dp)) // Add space between sections
            // Ensure NewsSection composable is defined and imported correctly
            NewsSection(viewModelNews = viewModelNews)
            // --------------------------

            Spacer(modifier = Modifier.height(16.dp)) // Add space at the bottom
        }
    }
}

// Helper composable to encapsulate weather state handling for the home screen
@Composable
fun WeatherSection(
    modifier: Modifier = Modifier,
    viewModelWeather: ViewModelWeather // Get ViewModel instance
) {
    val weatherState by viewModelWeather.weatherState.collectAsState()

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium, // Add some shape
        tonalElevation = 2.dp // Add slight elevation
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp), // Padding inside the weather section
            contentAlignment = Alignment.Center
        ) {
            when (val state = weatherState) {
                is UiState.Idle -> {
                    CircularProgressIndicator(modifier = Modifier.size(30.dp))
                }

                is UiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.size(30.dp))
                }

                is UiState.Error -> {
                    Text(
                        "Weather unavailable: ${state.message}",
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }

                is UiState.Success -> {
                    // Ensure WeatherDetails is imported and uses WeatherApiResponse
                    WeatherDetails(data = state.data)
                }
            }
        }
    }
}

// Ensure NewsSection composable is defined (or imported) correctly elsewhere
// and uses ViewModelNews.

@Composable
fun NewsSection(
    modifier: Modifier = Modifier,
    viewModelNews: ViewModelNews // Get ViewModel instance
) {
    val newsState by viewModelNews.newsState.collectAsState()

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium, // Add some shape
        tonalElevation = 2.dp // Add slight elevation
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp), // Padding inside the weather section
            contentAlignment = Alignment.Center
        ) {
            when (val state = newsState) {
                is UiState.Idle -> {
                    CircularProgressIndicator(modifier = Modifier.size(30.dp))
                }

                is UiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.size(30.dp))
                }

                is UiState.Error -> {
                    Text(
                        "News unavailable: ${state.message}",
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }

                is UiState.Success -> {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Ensure NewsArticle has a unique ID for the key
                        items(state.data.take(5), key = { it.articleId }) { article ->
                            NewsItemCard(article = article) // Call the Card composable
                        }
                    }
                }
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    OnTheWayTheme {
        HomeScreen()
    }
}
package com.zhenbang.otw.ui.screen // Adjust package as needed

// --- Imports ---
// --- Import your specific classes ---
import android.util.Log
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.setValue
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


@OptIn(ExperimentalMaterial3Api::class) // For TopAppBar, Scaffold, FilterChip
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModelNews: ViewModelNews = viewModel(),
    viewModelWeather: ViewModelWeather = viewModel(),
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
                WeatherSection(viewModelWeather = viewModelWeather)

                Spacer(modifier = Modifier.height(24.dp)) // Add space between sections

                // --- News Section ---
                NewsSection(viewModelNews = viewModelNews)


                Spacer(modifier = Modifier.height(16.dp)) // Add space at the bottom
            }
        }
    }
}

// Helper composable for Weather Section
@Composable
fun WeatherSection(
    modifier: Modifier = Modifier,
    viewModelWeather: ViewModelWeather
) {
    val weatherState by viewModelWeather.weatherState.collectAsState()

    // Use Card similar to NewsSection, apply width constraint here
    Card(
        modifier = modifier
            .fillMaxWidth(0.6f), // Occupy 60% of parent width
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column { // Use Column to stack Title and Content Box
            // Section Title
            Text(
                "Current Weather :", // Added title
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)
            )

            // Content Box (Loading/Error/Success)
            Box(
                modifier = Modifier
                    .fillMaxWidth() // Fill width of the Card
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp), // Padding inside the card below title
                contentAlignment = Alignment.Center
            ) {
                when (val state = weatherState) {
                    is UiState.Idle, is UiState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.size(30.dp))
                    }
                    is UiState.Error -> {
                        Text(
                            "Weather unavailable:\n${state.message}", // Added newline for potentially longer messages
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall // Smaller text for error in small box
                        )
                    }
                    is UiState.Success -> {
                        // WeatherDetails will determine its own size within the Box
                        WeatherDetails(data = state.data)
                    }
                }
            }
        }
    }
}

// News Section Composable (Ensure it's defined/imported correctly elsewhere)
@OptIn(ExperimentalMaterial3Api::class) // Needed for FilterChip
@Composable
fun NewsSection(
    modifier: Modifier = Modifier,
    viewModelNews: ViewModelNews // Get ViewModel instance
) {
    val newsState by viewModelNews.newsState.collectAsState()
    // State for category selection moved inside NewsSection
    var selectedCategory by remember { mutableStateOf("technology") }
    val categories = listOf("Business", "Technology", "Health", "Science", "Sports") // Example list

    // Using Card for news section
    Card(
        modifier = modifier.fillMaxWidth(), // News takes full width
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column { // Use Column to stack Title, Chips, and Content
            // Section Title
            Text(
                "Latest News :",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)
            )

            // --- Category Selection using FilterChips ---
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState()) // Allow scrolling if many chips
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { category ->
                    FilterChip(
                        selected = (selectedCategory == category),
                        onClick = {
                            selectedCategory = category
                            viewModelNews.fetchNews(category = selectedCategory) // Refetch on selection
                        },
                        label = { Text(category) },
                        // Optional: Add leading icon
                        // leadingIcon = if (selectedCategory == category) { { Icon(Icons.Filled.Done, contentDescription = "Selected") } } else { null }
                    )
                }
            }
            // ---------------------------------------------

            HorizontalDivider(modifier = Modifier.padding(top = 8.dp)) // Divider after chips

            // Content Box (Loading/Error/Success List)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // Adjust height or let LazyRow determine height
                    .defaultMinSize(minHeight = 180.dp) // Give Box a min height for loading/error states
                    .padding(vertical = 16.dp), // Padding for the content area
                contentAlignment = Alignment.Center
            ) {
                when (val state = newsState) {
                    is UiState.Idle, is UiState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.size(30.dp))
                    }
                    is UiState.Error -> {
                        Text(
                            "News unavailable: ${state.message}",
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    is UiState.Success -> {
                        if (state.data.isEmpty()) {
                            Text("No news articles found.", modifier = Modifier.padding(16.dp))
                        } else {
                            LazyRow(
                                // Removed fillMaxWidth here, let content determine width within Box
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(state.data.take(5), key = { it.articleId }) { article ->
                                    NewsItemCard(article = article)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


//// Ensure NewsItemCard composable is defined (or imported) correctly elsewhere
//@Composable
//fun NewsItemCard(article: NewsArticle) {
//    val uriHandler = LocalUriHandler.current
//
//    Card(
//        modifier = Modifier
//            .width(160.dp) // Slightly smaller card
//            .clickable(enabled = !article.link.isNullOrBlank()) {
//                article.link?.let { url ->
//                    try {
//                        uriHandler.openUri(url)
//                    } catch (e: Exception) {
//                        Log.e("NewsItemCard", "Failed to open URI: $url", e)
//                    }
//                }
//            }
//    ) {
//        Column(modifier = Modifier.fillMaxWidth()) {
//            AsyncImage(
//                model = article.imageUrl,
//                contentDescription = article.title ?: "News Image",
//                modifier = Modifier
//                    .height(90.dp) // Adjusted height
//                    .fillMaxWidth(),
//                contentScale = ContentScale.Crop
//            )
//            article.title?.let { title ->
//                Text(
//                    text = title,
//                    style = MaterialTheme.typography.labelLarge, // Adjusted style
//                    maxLines = 2,
//                    overflow = TextOverflow.Ellipsis,
//                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp) // Adjusted padding
//                )
//            }
//            article.sourceName?.let { source ->
//                Text(
//                    text = source,
//                    style = MaterialTheme.typography.labelSmall, // Adjusted style
//                    maxLines = 1,
//                    overflow = TextOverflow.Ellipsis,
//                    modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 6.dp) // Adjusted padding
//                )
//            }
//        }
//    }
//}

// Preview function might need adjustments if ViewModels require specific setup
@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    OnTheWayTheme { // Ensure your theme is applied
        HomeScreen()
    }
}

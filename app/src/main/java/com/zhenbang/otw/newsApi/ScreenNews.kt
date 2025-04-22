package com.zhenbang.otw.newsApi // Ensure this package is correct

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.util.Log
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.clickable
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)

@Composable
fun NewsScreen(
    modifier: Modifier = Modifier, viewModelNews: ViewModelNews = viewModel()
) {
    var selectedCategory by remember { mutableStateOf("technology") }
    val categories = listOf("business", "technology", "health", "science", "sports") // Example

    // Removed LaunchedEffect - fetch happens via init in ViewModel now
    // Or keep it if you prefer triggering from UI start

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("News Reader") }, actions = {
                IconButton(onClick = {
                    Log.d("NewsScreen", "Refresh clicked")
                    viewModelNews.fetchNews(category = selectedCategory) // Pass current selection
                }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh News")
                }
            })
        }, modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // Category Selection Row (or Dropdown/Chips)
            Text("Select Category:", modifier = Modifier.padding(start = 16.dp, top = 8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { category ->
                    Button(
                        onClick = {
                            selectedCategory = category
                            viewModelNews.fetchNews(category = selectedCategory) // Refetch on selection
                        },
                        colors = if (selectedCategory == category) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors()
                    ) { Text(category) }
                }
            }
            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // News List Display Area
            NewsListSection(
                modifier = Modifier.weight(1f), // Takes remaining space
                newsViewModel = viewModelNews // Pass ViewModel down
            )
        }
    }
}

// Displays Loading/Error/Success states and the list itself
@Composable
fun NewsListSection(
    modifier: Modifier = Modifier, newsViewModel: ViewModelNews // Accept ViewModel
) {
    val newsState by newsViewModel.newsState.collectAsState() // Collect state

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp), // Add bottom padding
        contentAlignment = Alignment.Center
    ) {
        when (val state = newsState) {
            is UiState.Idle -> {
                Text("Select a category to see news.")
            }

            is UiState.Loading -> {
                CircularProgressIndicator()
            }

            is UiState.Error -> {
                Text(
                    "Error loading news: ${state.message}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }

            is UiState.Success -> {
                if (state.data.isEmpty()) {
                    Text("No news articles found.", modifier = Modifier.padding(16.dp))
                } else {
                    // Horizontal list for the top 5 articles
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.data.take(5), key = { it.articleId }) { article ->
                            NewsItemCard(article = article) // Call the Card composable
                        }
                    }
                }
            }
        }
    }
}


// Displays a single News Article Card
@Composable
fun NewsItemCard(article: NewsArticle) {
    val uriHandler = LocalUriHandler.current

    Card(
        modifier = Modifier
            .width(180.dp) // Adjust width as needed
            .clickable(enabled = !article.link.isNullOrBlank()) {
                article.link?.let { url ->
                    try {
                        uriHandler.openUri(url)
                    } catch (e: Exception) {
                        Log.e("NewsItemCard", "Failed to open URI: $url", e)
                    }
                }
            }) {
        Column(modifier = Modifier.fillMaxWidth()) { // Fill card width
            AsyncImage(
                model = article.imageUrl, // Directly use the image URL as the model
                contentDescription = article.title ?: "News Image",
                modifier = Modifier
                    .height(100.dp)
                    .fillMaxWidth(),
                contentScale = ContentScale.Crop
            )
            article.title?.let { title ->
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            article.sourceName?.let { source ->
                Text(
                    text = source,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                )
            }
        }
    }
}


package com.zhenbang.otw.ui.screen // Ensure this package is correct

// --- Ensure ALL necessary imports are present ---
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.zhenbang.otw.data.model.NewsArticle
import com.zhenbang.otw.ui.theme.OnTheWayTheme
import com.zhenbang.otw.ui.viewmodel.ViewModelNews
import com.zhenbang.otw.util.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(
    modifier: Modifier = Modifier,
    viewModelNews: ViewModelNews = viewModel() // Ensure ViewModelNews exists and is imported
) {
    var selectedCategory by remember { mutableStateOf("technology") }
    val categories = listOf("business", "technology", "health", "science", "sports") // Example

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("News Reader") },
                actions = {
                    IconButton(onClick = {
                        Log.d("NewsScreen", "Refresh clicked")
                        viewModelNews.fetchNews(category = selectedCategory) // Pass current selection
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh News")
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // Category Selection Row
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
                            viewModelNews.fetchNews(category = selectedCategory) // Re fetch on selection
                        },
                        colors = if (selectedCategory == category) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors()
                    ) { Text(category) }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

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
    modifier: Modifier = Modifier,
    newsViewModel: ViewModelNews // Accept ViewModel
) {
    val newsState by newsViewModel.newsState.collectAsState() // Collect state

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp), // Add bottom padding
        contentAlignment = Alignment.Center
    ) {
        // --- CORRECTED when statement ---
        when (val state = newsState) {
            // Use the type UiState.Idle, not the variable newsState.Idle
            is UiState.Idle -> { // <-- FIX HERE
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
                        // Ensure NewsArticle has a unique ID for the key
                        items(state.data.take(5), key = { it.articleId }) { article ->
                            NewsItemCard(article = article) // Call the Card composable
                        }
                    }
                }
            }
        }
        // ---------------------------------
    }
}


// Displays a single News Article Card
@Composable
fun NewsItemCard(article: NewsArticle) { // Ensure NewsArticle is imported
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
            }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) { // Fill card width
            AsyncImage(
                model = article.imageUrl, // Directly use the image URL as the model
                contentDescription = article.title ?: "News Image",
                modifier = Modifier
                    .height(100.dp)
                    .fillMaxWidth(),
                contentScale = ContentScale.Crop
                // Optional: Add placeholder/error
                // placeholder = painterResource(R.drawable.placeholder),
                // error = painterResource(R.drawable.error_image),
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

@Preview(showBackground = true)
@Composable
fun NewsScreenPreview() {
    OnTheWayTheme {
        NewsScreen()
    }
}
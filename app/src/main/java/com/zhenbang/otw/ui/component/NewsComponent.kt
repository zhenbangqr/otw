package com.zhenbang.otw.ui.component // Ensure this package is correct

// --- Ensure ALL necessary imports are present ---
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.zhenbang.otw.data.model.NewsArticle
import com.zhenbang.otw.ui.theme.OnTheWayTheme
import com.zhenbang.otw.ui.viewmodel.NewsViewModel
import com.zhenbang.otw.util.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsSection(
    modifier: Modifier = Modifier,
    newsViewModel: NewsViewModel // Get ViewModel instance
) {
    val newsState by newsViewModel.newsState.collectAsState()
    // State for category selection moved inside NewsSection
    var selectedCategory by remember { mutableStateOf("Technology") }
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
                            newsViewModel.fetchNews(category = selectedCategory) // Refetch on selection
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
//        NewsScreen()
    }
}
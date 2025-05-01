package com.zhenbang.otw.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import android.util.Log // Import Log for logging
import com.zhenbang.otw.data.model.NewsArticle
import com.zhenbang.otw.data.model.NewsResponse
import com.zhenbang.otw.data.remote.NewsInstance
import com.zhenbang.otw.util.UiState

class NewsViewModel : ViewModel() {

    // State holds list of articles for the Success case
    private val _newsState = MutableStateFlow<UiState<List<NewsArticle>>>(UiState.Idle)
    val newsState: StateFlow<UiState<List<NewsArticle>>> = _newsState

    // Trigger initial fetch (optional)
    init {
        fetchNews() // Fetch with default parameters on init
    }

    fun fetchNews(
        query: String? = null,
        country: String? = "my", // Default country
        category: String? = "technology", // Default category
        language: String? = "zh", // Default language
    ) {
        // Optional: prevent refetch if already loading, or allow it
        // if (_newsState.value is UiState.Loading) return

        _newsState.value = UiState.Loading
        viewModelScope.launch {
            try {
                Log.d("ViewModelNews", "Fetching news...")

                // Call the GET method from NewsRetrofitInstance
                val response: NewsResponse = NewsInstance.api.getNews(
                    // apiKey is handled by default value in interface
                    query = query,
                    country = country,
                    category = category,
                    language = language,
                )

                // Extract results, filter nulls if necessary
                val validArticles = response.results?.filter {
                    !it.imageUrl.isNullOrBlank() && !it.link.isNullOrBlank()
                } ?: emptyList()

                _newsState.value = UiState.Success(validArticles)
                Log.d("ViewModelNews", "News fetched successfully: ${validArticles.size} articles")

            } catch (e: IOException) {
                Log.e("ViewModelNews", "Network error fetching news: ${e.message}", e)
                _newsState.value = UiState.Error("Network error fetching news.")
            } catch (e: HttpException) {
                Log.e("ViewModelNews", "HTTP error ${e.code()} fetching news: ${e.message()}", e)
                _newsState.value = UiState.Error("API error ${e.code()} fetching news.")
            } catch (e: Exception) {
                Log.e("ViewModelNews", "Unexpected error fetching news: ${e.message}", e)
                _newsState.value = UiState.Error("Error fetching news.")
            }
        }
    }
}
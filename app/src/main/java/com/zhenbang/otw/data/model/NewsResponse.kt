package com.zhenbang.otw.data.model

import com.google.gson.annotations.SerializedName

// Renamed from NewsApiResponse to match user's naming
data class NewsResponse(
    val status: String,
    val totalResults: Int,
    // 'results' contains the list of articles
    val results: List<NewsArticle>? // Make list nullable
)

// Represents a single article
data class NewsArticle(
    @SerializedName("article_id")
    val articleId: String,
    val title: String?,
    val link: String?, // URL to the article
    @SerializedName("image_url")
    val imageUrl: String?, // URL of the image
    @SerializedName("source_name")
    val sourceName: String?
    // Only include fields you need
)
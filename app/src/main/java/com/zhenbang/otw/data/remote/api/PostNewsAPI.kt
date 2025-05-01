package com.zhenbang.otw.data.remote.api

import com.zhenbang.otw.data.model.ResponseNews
import retrofit2.http.GET // Use GET for fetching news usually
import retrofit2.http.Query // Needed for API key/parameters

// Consider renaming interface to NewsApiService for clarity
interface PostNewsAPI {

    @GET("latest") // The endpoint path from the example URL
    suspend fun getNews(
        // Authentication via Query Parameter
        @Query("apikey") apiKey: String = "pub_79892d3ed590f8ee9d6b1eb4938add4a381d0",

        // Other Query Parameters based on API docs and your needs
        @Query("q") query: String? = null,
        @Query("country") country: String? = null,
        @Query("category") category: String? = null,
        @Query("language") language: String? = null,
        @Query("domain") domain: String? = null // 0 or 1? Check docs
        // Add other query parameters as needed
    ): ResponseNews // Return your response data class
}
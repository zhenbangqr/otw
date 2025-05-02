package com.zhenbang.otw.data.remote.api

import com.zhenbang.otw.data.model.WeatherResponse
import retrofit2.http.GET // Use GET for fetching news usually
import retrofit2.http.Query // Needed for API key/parameters

// Consider renaming interface to NewsApiService for clarity
interface WeatherApi {

    @GET("current.json") // The endpoint path from the example URL
    suspend fun getCurrentWeather(
        // Authentication via Query Parameter
        @Query("key") apiKey: String = "21063b36c669467ba0712509251204",
        // Other Query Parameters based on API docs and your needs
        @Query("q") location: String? = "Kuala Lumpur",
        // Add other query parameters as needed
    ): WeatherResponse // Return your response data class
}
package com.zhenbang.otw.data.model // Adjust package as needed

import com.google.gson.annotations.SerializedName

// Top-level response object matching the JSON structure
data class WeatherResponse(
    val location: Location?, // Matches "location": { ... }
    val current: CurrentWeather? // Matches "current": { ... }
)

// Represents the 'location' object in the JSON
data class Location(
    val name: String?,
    val region: String?,
    val country: String?,
    val lat: Double?,
    val lon: Double?,
    @SerializedName("tz_id")
    val tzId: String?,
    @SerializedName("localtime_epoch")
    val localtimeEpoch: Long?,
    val localtime: String?
)

// Represents the 'current' weather object in the JSON
data class CurrentWeather(
    @SerializedName("last_updated")
    val lastUpdated: String?,
    @SerializedName("temp_c")
    val tempCelsius: Float?,
    @SerializedName("is_day")
    val isDay: Int?,
    // Matches "condition": { ... } (single object)
    val condition: Condition?,
    val humidity: Int?,
    val cloud: Int?
    // Add other fields from 'current' if needed
)

// Represents the 'condition' object (nested within 'current')
// This class definition is likely correct based on your version
data class Condition(
    val text: String?, // Make nullable for safety
    val icon: String?, // Make nullable for safety
    val code: Int?     // Make nullable for safety
)

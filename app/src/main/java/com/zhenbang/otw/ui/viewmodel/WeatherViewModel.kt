package com.zhenbang.otw.ui.viewmodel // Adjust package as needed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
// Use the corrected data model from the 'weather_data_models' artifact
// Use your common UiState definition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import android.util.Log
import com.zhenbang.otw.data.model.WeatherResponse
import com.zhenbang.otw.data.remote.WeatherInstance
import com.zhenbang.otw.util.UiState

class WeatherViewModel : ViewModel() {

    // State holds the full API response for the Success case
    // Use the correct Response type from the 'weather_data_models' artifact
    private val _weatherState = MutableStateFlow<UiState<WeatherResponse>>(UiState.Idle)
    val weatherState: StateFlow<UiState<WeatherResponse>> = _weatherState

    // --- ADD init block for automatic fetch ---
    init {
        Log.d("ViewModelWeather", "ViewModel initialized. Fetching default weather.")
        fetchWeather("Kuala Lumpur") // Fetch default location on init
    }
    // -----------------------------------------

    fun fetchWeather(location: String) {
        if (location.isBlank()) {
            // Optionally handle blank input differently if triggered from button
            // _weatherState.value = UiState.Error("Please enter a location.")
            Log.w("ViewModelWeather", "fetchWeather called with blank location.")
            // Decide if you want to show an error or just ignore blank input from button
            return
        }
        // Optional: Prevent refetch if already loading
        // if (_weatherState.value is UiState.Loading) return

        _weatherState.value = UiState.Loading
        viewModelScope.launch {
            try {
                Log.d("ViewModelWeather", "Fetching weather for: $location")

                // Call the GET method from WeatherRetrofitInstance
                // Ensure WeatherApiService returns WeatherApiResponse
                val response = WeatherInstance.api.getCurrentWeather(
                    location = location
                    // apiKey is handled by default value in interface using BuildConfig
                )

                // Check if response or key data is null
                if (response.current == null || response.location == null) {
                    _weatherState.value = UiState.Error("Received incomplete weather data.")
                    Log.w("ViewModelWeather", "Incomplete data received: $response")
                } else {
                    _weatherState.value = UiState.Success(response)
                    Log.d("ViewModelWeather", "Weather fetched successfully for ${response.location?.name}")
                }

            } catch (e: IOException) {
                Log.e("ViewModelWeather", "Network error fetching weather: ${e.message}", e)
                _weatherState.value = UiState.Error("Network error fetching weather.")
            } catch (e: HttpException) {
                Log.e("ViewModelWeather", "HTTP error ${e.code()} fetching weather: ${e.message()}", e)
                _weatherState.value = UiState.Error("API error ${e.code()} fetching weather.")
            } catch (e: Exception) {
                Log.e("ViewModelWeather", "Unexpected error fetching weather: ${e.message}", e)
                _weatherState.value = UiState.Error("Error fetching weather.")
            }
        }
    }
}

package com.zhenbang.otw.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import android.util.Log // Import Log for logging
import com.zhenbang.otw.data.model.RequestMessage
import com.zhenbang.otw.data.model.ZpResponse
import com.zhenbang.otw.data.model.ZpRequest
import com.zhenbang.otw.data.remote.ZpInstance
import com.zhenbang.otw.util.UiState

// --- The ViewModel Class Definition ---
class ZpViewModel : ViewModel() { // Your class must inherit from ViewModel

    // --- 1. State Holder ---
    // Use StateFlow to hold the state that the UI will observe.
    // Initialize it with a default state (e.g., Loading or an initial empty state).
    private val _apiDataState =
        MutableStateFlow<UiState<ZpResponse>>(UiState.Idle) // <-- INITIAL STATE
    val apiDataState: StateFlow<UiState<ZpResponse>> = _apiDataState

    // --- 2. Function to Trigger the API Call ---
    // Place the API call logic inside a function.
    fun fetchDataFromApi(promptContent: String) {
        // Set state to Loading before starting the network request
        _apiDataState.value = UiState.Loading

        // Launch the coroutine within the viewModelScope
        viewModelScope.launch {
            try {
                // Construct the request payload using the correct class name
                val request = ZpRequest(
                    model = "glm-4-flash", // Replace with your actual model code/constant
                    messages = listOf(
                        RequestMessage(role = "user", content = promptContent)
                    )
                )

                // Make the API call using your Retrofit instance and service
                val response: ZpResponse = ZpInstance.api.postApiData(request)

                // Update state to Success with the received data
                _apiDataState.value = UiState.Success(response)
                Log.d("ViewModelZPAPI", "API Call Success: ${response.id}") // Example logging

            } catch (e: IOException) { // Handle network errors
                Log.e("ViewModelZPAPI", "Network error: ${e.message}", e)
                // Update state to Error with a specific message
                _apiDataState.value = UiState.Error("Network error: Could not connect to server.")

            } catch (e: HttpException) { // Handle HTTP errors (like 404, 500)
                Log.e("ViewModelZPAPI", "HTTP error ${e.code()}: ${e.message()}", e)
                // Update state to Error with details
                _apiDataState.value =
                    UiState.Error("API error ${e.code()}: Check request or server.")

            } catch (e: Exception) { // Handle any other unexpected errors
                Log.e("ViewModelZPAPI", "Unexpected error: ${e.message}", e)
                // Update state to Error with a generic message
                _apiDataState.value = UiState.Error("An unexpected error occurred.")
            }
        }
    }
}
package com.zhenbang.otw.ui.screen // Adjust package as needed

// --- Necessary Imports ---
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage // For displaying images from URL
import android.util.Log // For logging
import androidx.compose.ui.tooling.preview.Preview
import com.zhenbang.otw.data.model.ResponseWeatherAPI
import com.zhenbang.otw.ui.theme.OnTheWayTheme
import com.zhenbang.otw.ui.viewmodel.ViewModelWeather
import com.zhenbang.otw.util.UiState

@Composable
fun ScreenWeather(
    modifier: Modifier = Modifier,
    viewModelWeather: ViewModelWeather = viewModel()
) {
    // Ensure ViewModel's state uses UiState<WeatherApiResponse>
    val weatherState by viewModelWeather.weatherState.collectAsState()
    var locationInput by remember { mutableStateOf("") } // Start empty

    // --- REMOVED LaunchedEffect - Fetch now happens in ViewModel init ---

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Weather Info", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        // Location Input Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = locationInput,
                onValueChange = { locationInput = it },
                label = { Text("Enter Location (e.g., City)") },
                placeholder = { Text("e.g., London") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    Log.d("ScreenWeather", "Get button clicked for: $locationInput")
                    viewModelWeather.fetchWeather(locationInput)
                },
                enabled = locationInput.isNotBlank()
            ) {
                Text("Get")
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        // Weather Display Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            when (val state = weatherState) {
                is UiState.Idle -> {
                    Text("Fetching default weather...") // Updated Idle message
                }
                is UiState.Loading -> {
                    CircularProgressIndicator()
                }
                is UiState.Error -> {
                    Text(
                        "Error: ${state.message}",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                is UiState.Success -> {
                    // Pass the WeatherApiResponse data to WeatherDetails
                    WeatherDetails(data = state.data)
                }
            }
        }
    }
}

// Composable to display the actual weather details - RE-VERIFIED DATA ACCESS
@Composable
fun WeatherDetails(data: ResponseWeatherAPI) { // Parameter must be WeatherApiResponse
    // --- Safely access data based on the CORRECT WeatherApiResponse structure ---
    val locationName = data.location?.name
    val country = data.location?.country
    val currentTemp = data.current?.tempCelsius // Access tempCelsius
    val conditionText = data.current?.condition?.text
    val conditionIconUrl = data.current?.condition?.icon // Access icon URL
    val humidity = data.current?.humidity
    val cloud = data.current?.cloud
    val lastUpdated = data.current?.lastUpdated

    // Optional: More robust check if critical data is missing
    if (locationName == null || currentTemp == null || conditionText == null || conditionIconUrl == null) {
        Log.w("WeatherDetails", "Essential weather data missing in response: $data")
        Text("Weather data unavailable or incomplete.")
        return // Exit early if essential data is missing
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(16.dp)
    ) {
        // Display Location Name and Country (handle nulls)
        Text(
            text = "${locationName}, ${country ?: ""}", // Display location name and country
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Prepare Icon URL (handle "//" prefix)
        val finalIconUrl = conditionIconUrl.let { // Use 'let' on the already extracted nullable URL
            if (it.startsWith("//")) "https:$it" else it
        }

        AsyncImage(
            model = finalIconUrl, // Use the prepared URL
            contentDescription = conditionText, // Use condition text for description
            modifier = Modifier.size(80.dp)
            // Optional: Add placeholder/error for AsyncImage
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Display Condition Text
        Text(
            text = conditionText,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Display Temperature
        Text(
            text = "%.1f°C".format(currentTemp), // Format the temperature
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Display Humidity (handle null)
        Text(
            text = "Humidity: ${humidity ?: "--"}%",
            style = MaterialTheme.typography.bodyMedium
        )
        // Display Cloud Cover (handle null)
        Text(
            text = "Cloud Cover: ${cloud ?: "--"}%",
            style = MaterialTheme.typography.bodyMedium
        )
        // Display Last Updated (handle null)
        Text(
            text = "Last Updated: ${lastUpdated ?: "--"}",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Preview(showBackground = true)
@Composable
fun WeatherScreenPreview() {
    OnTheWayTheme {
        ScreenWeather()
    }
}
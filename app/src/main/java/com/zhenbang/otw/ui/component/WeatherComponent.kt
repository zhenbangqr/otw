package com.zhenbang.otw.ui.component // Adjust package as needed

// --- Necessary Imports ---
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.zhenbang.otw.data.model.WeatherResponse
import com.zhenbang.otw.ui.theme.OnTheWayTheme
import com.zhenbang.otw.ui.viewmodel.WeatherViewModel
import com.zhenbang.otw.util.UiState

// Helper composable for Weather Section
@Composable
fun WeatherSection(
    modifier: Modifier = Modifier,
    weatherViewModel: WeatherViewModel
) {
    val weatherState by weatherViewModel.weatherState.collectAsState()

    // Use Card similar to NewsSection, apply width constraint here
    Card(
        modifier = modifier
            .fillMaxWidth(), // Occupy 100% of available parent width
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column { // Use Column to stack Title and Content Box
            // Section Title
            Text(
                "Current Weather :", // Added title
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)
            )

            // Content Box (Loading/Error/Success)
            Box(
                modifier = Modifier
                    .fillMaxWidth() // Fill width of the Card
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp), // Padding inside the card below title
                contentAlignment = Alignment.Center
            ) {
                when (val state = weatherState) {
                    is UiState.Idle, is UiState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.size(30.dp))
                    }
                    is UiState.Error -> {
                        Text(
                            "Weather unavailable:\n${state.message}", // Added newline for potentially longer messages
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall // Smaller text for error in small box
                        )
                    }
                    is UiState.Success -> {
                        // WeatherDetails will determine its own size within the Box
                        WeatherDetails(data = state.data)
                    }
                }
            }
        }
    }
}

// Composable to display the actual weather details - RE-VERIFIED DATA ACCESS
@Composable
fun WeatherDetails(data: WeatherResponse) {
    // --- Safely access data based on the CORRECT WeatherApiResponse structure ---
    val locationName = data.location?.name
    val country = data.location?.country
    val currentTemp = data.current?.tempCelsius // Access tempCelsius
    val conditionText = data.current?.condition?.text
    val conditionIconUrl = data.current?.condition?.icon // Access icon URL

    // Optional: More robust check if critical data is missing
    if (locationName == null || currentTemp == null || conditionText == null || conditionIconUrl == null) {
        Log.w("WeatherDetails", "Essential weather data missing in response: $data")
        Text("Weather data unavailable or incomplete.", textAlign = TextAlign.Center)
        return // Exit early if essential data is missing
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier // Removed padding, let parent handle padding
    ) {
        Text(
            text = "${locationName}, ${country ?: ""}",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        val finalIconUrl = conditionIconUrl.let {
            if (it.startsWith("//")) "https:$it" else it
        }
        AsyncImage(
            model = finalIconUrl,
            contentDescription = conditionText,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = conditionText,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "%.1f°C".format(currentTemp),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Preview(showBackground = true)
@Composable
fun WeatherScreenPreview() {
    OnTheWayTheme {
//        ScreenWeather()
    }
}
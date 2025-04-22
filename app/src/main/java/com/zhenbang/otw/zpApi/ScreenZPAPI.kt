package com.zhenbang.otw.zpApi

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ScreenZPAPI(
    modifier: Modifier = Modifier,
    viewModelZPAPI: ViewModelZPAPI = viewModel() // Get instance of MainViewModel
) {
    // 1. Observe the API state from the ViewModel
    val apiState by viewModelZPAPI.apiDataState.collectAsState()

    // 2. State for the user's input text field
    var inputText by remember { mutableStateOf("") }

    // 3. Determine if the API call is currently loading
    val isLoading = apiState is UiState.Loading

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp), // Add padding around the column content
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Ask 智谱AI", style = MaterialTheme.typography.headlineSmall)

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Input Text Field
        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            label = { Text("Enter your question") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = false, // Allow multiple lines if needed
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 5. Button to Send Request
        Button(
            onClick = {
                // Call the ViewModel function when button is clicked
                if (inputText.isNotBlank()) {
                    viewModelZPAPI.fetchDataFromApi(inputText)
                }
                inputText = "" // Clear input after sending
            },
            // Disable button if loading or input is empty
            enabled = !isLoading && inputText.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Show "Sending..." text when loading
            Text(if (isLoading) "Sending..." else "Send Request")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("Response:", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        // 6. Display Area for Loading/Error/Success
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f), // Take remaining vertical space
            contentAlignment = Alignment.TopCenter
        ) {
            when (val state = apiState) {
                is UiState.Idle -> { // <-- ADD THIS BRANCH
                    Text("Enter a question and press Send.") // Display initial prompt
                }
                is UiState.Loading -> {
                    // Consider simplifying this branch (see below)
                    CircularProgressIndicator() // Show indicator when loading
                }
                is UiState.Success -> {
                    // Access the message content via the choices list
                    // Assuming always want the first choice's message
                    val responseContent = state.data.choices.firstOrNull()?.message?.content
                    Text(responseContent ?: "No text content received.") // Display it
                    // Add a log here too for debugging if needed
                    Log.d("UI_Debug", "Displaying content: '$responseContent'")
                }
                is UiState.Error -> {
                    Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
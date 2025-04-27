package com.zhenbang.otw.enterSelfDetails // Adjust package if needed

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
// --- Use the renamed ViewModel ---
// ---------------------------------
import android.widget.Toast // For showing errors or success

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnterSelfDetailsScreen(
    // --- Use the renamed ViewModel ---
    selfDetailsViewModel: EnterSelfDetailsViewModel = viewModel(),
    // ---------------------------------
    onDetailsSaved: () -> Unit, // Callback to navigate after saving
    onLogout: () -> Unit // <<< --- ADD Logout Callback Parameter --- <<<
) {
    val uiState by selfDetailsViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Effect to navigate when save is successful
    LaunchedEffect(uiState.isSaveSuccess) {
        if (uiState.isSaveSuccess) {
            Toast.makeText(context, "Details Saved!", Toast.LENGTH_SHORT).show()
            onDetailsSaved()
            selfDetailsViewModel.resetSaveSuccessFlag() // Reset flag after navigation
        }
    }

    // Effect to show error messages
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            // Optionally clear error in VM after showing
            // selfDetailsViewModel.clearError() // Add this method to VM if needed
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Complete Your Profile") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()) // Make column scrollable
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
            // Removed fixed spacing, let Column handle it or use Spacers explicitly
        ) {
            Text(
                "Please enter your details",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Display Name Field (Example - Make it required)
            OutlinedTextField(
                value = uiState.displayName,
                onValueChange = { selfDetailsViewModel.updateField("displayName", it) },
                label = { Text("Display Name *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                readOnly = uiState.isLoading,
                isError = uiState.errorMessage?.contains("Display Name", ignoreCase = true) == true
            )
            Spacer(modifier = Modifier.height(12.dp)) // Add spacing

            // Phone Number Field
            OutlinedTextField(
                value = uiState.phoneNumber,
                onValueChange = { selfDetailsViewModel.updateField("phoneNumber", it) },
                label = { Text("Phone Number") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                readOnly = uiState.isLoading,
                isError = uiState.errorMessage?.contains("phone number", ignoreCase = true) == true
            )
            Spacer(modifier = Modifier.height(12.dp)) // Add spacing

            // Save Button
            Button(
                onClick = { selfDetailsViewModel.saveDetails() },
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Save Details")
                }
            }

            // Display general error messages if not related to specific fields
            if (uiState.errorMessage != null &&
                !uiState.errorMessage!!.contains("Display Name", ignoreCase = true) &&
                !uiState.errorMessage!!.contains("phone number", ignoreCase = true)) {
                Text(
                    text = uiState.errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f)) // Pushes logout button to bottom

            // --- Logout Button ---
            TextButton(
                onClick = onLogout, // Call the passed lambda
                modifier = Modifier.padding(top = 16.dp) // Add some spacing above
            ) {
                Text("Log Out")
            }
            // -------------------

        } // End Column
    } // End Scaffold
}

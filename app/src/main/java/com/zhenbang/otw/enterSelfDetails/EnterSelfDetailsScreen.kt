package com.zhenbang.otw.enterSelfDetails // Match the directory structure

import androidx.compose.foundation.BorderStroke // *** Import BorderStroke ***
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor // *** Import SolidColor ***
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhenbang.otw.enterSelfDetails.EnterSelfDetailsViewModel // Use correct ViewModel name
import com.zhenbang.otw.enterSelfDetails.SelfDetailsUiState // Keep UiState name or rename if needed
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.* // Import Date, Calendar, Locale, TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnterSelfDetailsScreen(
    selfDetailsViewModel: EnterSelfDetailsViewModel = viewModel(),
    onDetailsSaved: () -> Unit, // Callback to navigate after saving
    onLogout: () -> Unit
) {
    val uiState by selfDetailsViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // --- Date Picker State ---
    // Ensure rememberDatePickerState is correctly imported and used
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = uiState.birthdateMillis,
        yearRange = (Calendar.getInstance().get(Calendar.YEAR) - 100)..(Calendar.getInstance().get(Calendar.YEAR))
    )
    // *** Wrap derivedStateOf in remember ***
    val confirmEnabled by remember {
        derivedStateOf { datePickerState.selectedDateMillis != null }
    }
    // ************************************

    // --- Effects ---
    LaunchedEffect(uiState.isSaveSuccess) {
        if (uiState.isSaveSuccess) {
            Toast.makeText(context, "Details Saved!", Toast.LENGTH_SHORT).show()
            onDetailsSaved()
            selfDetailsViewModel.resetSaveSuccessFlag()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            selfDetailsViewModel.clearError() // Clear error after showing
        }
    }

    // --- Date Formatting ---
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) } // Changed format
    val selectedDateString = remember(uiState.birthdateMillis) {
        uiState.birthdateMillis?.let { dateFormatter.format(Date(it)) } ?: "Select Date"
    }

    // --- Main UI ---
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
                .verticalScroll(rememberScrollState())
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Please enter your details",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Display Name Field (Required)
            OutlinedTextField(
                value = uiState.displayName,
                onValueChange = { selfDetailsViewModel.updateTextField("displayName", it) },
                label = { Text("Display Name *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                readOnly = uiState.isLoading,
                isError = uiState.errorMessage?.contains("Display Name", ignoreCase = true) == true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Phone Number Field (Required)
            OutlinedTextField(
                value = uiState.phoneNumber,
                onValueChange = { selfDetailsViewModel.updateTextField("phoneNumber", it) },
                label = { Text("Phone Number *") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                readOnly = uiState.isLoading,
                isError = uiState.errorMessage?.contains("phone number", ignoreCase = true) == true
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Birthdate Selection (Required)
            val isBirthdateError = uiState.errorMessage?.contains("birthdate", ignoreCase = true) == true
            OutlinedButton(
                onClick = { selfDetailsViewModel.showDatePicker(true) },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
                // *** 3. Corrected Border Definition ***
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isBirthdateError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline // Use appropriate colors
                )
                // ************************************
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Birthdate *", color = if(isBirthdateError) MaterialTheme.colorScheme.error else LocalContentColor.current )
                    Text(
                        selectedDateString,
                        color = if (uiState.birthdateMillis == null && !isBirthdateError) LocalContentColor.current.copy(alpha = 0.6f) else LocalContentColor.current
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Bio Field (Optional)
            OutlinedTextField(
                value = uiState.bio,
                onValueChange = { selfDetailsViewModel.updateTextField("bio", it) },
                label = { Text("Bio (Optional)") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                readOnly = uiState.isLoading,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                maxLines = 4
            )
            Spacer(modifier = Modifier.height(24.dp))

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

            // Display general error messages if needed
            uiState.errorMessage?.let { error ->
                if (!error.contains("Display Name", ignoreCase = true) &&
                    !error.contains("phone number", ignoreCase = true) &&
                    !error.contains("birthdate", ignoreCase = true)) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Logout Button
            TextButton(
                onClick = onLogout,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("Log Out")
            }

        } // End Column
    } // End Scaffold

    // --- Date Picker Dialog ---
    if (uiState.showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { selfDetailsViewModel.showDatePicker(false) },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Pass selected date (in UTC milliseconds) to ViewModel
                        // The state holds the value, just need to update birthdate in VM
                        selfDetailsViewModel.updateBirthdate(datePickerState.selectedDateMillis)
                    },
                    // Use the remembered derived state here
                    enabled = confirmEnabled // Use the value of the remembered state
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { selfDetailsViewModel.showDatePicker(false) }
                ) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
    // --------------------------
}

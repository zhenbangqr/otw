package com.zhenbang.otw.ui.screen

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhenbang.otw.ui.viewmodel.EnterSelfDetailsViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnterSelfDetailsScreen(
    selfDetailsViewModel: EnterSelfDetailsViewModel = viewModel(),
    onDetailsSaved: () -> Unit,
    onLogout: () -> Unit
) {
    val uiState by selfDetailsViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = uiState.birthdateMillis,
        yearRange = (Calendar.getInstance().get(Calendar.YEAR) - 100)..(Calendar.getInstance()
            .get(Calendar.YEAR))
    )

    val confirmEnabled by remember {
        derivedStateOf { datePickerState.selectedDateMillis != null }
    }

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
            selfDetailsViewModel.clearError()
        }
    }

    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val selectedDateString = remember(uiState.birthdateMillis) {
        uiState.birthdateMillis?.let { dateFormatter.format(Date(it)) } ?: "Select Date"
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

            val isBirthdateError =
                uiState.errorMessage?.contains("birthdate", ignoreCase = true) == true
            OutlinedButton(
                onClick = { selfDetailsViewModel.showDatePicker(true) },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isBirthdateError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Birthdate *",
                        color = if (isBirthdateError) MaterialTheme.colorScheme.error else LocalContentColor.current
                    )
                    Text(
                        selectedDateString,
                        color = if (uiState.birthdateMillis == null && !isBirthdateError) LocalContentColor.current.copy(
                            alpha = 0.6f
                        ) else LocalContentColor.current
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = uiState.bio,
                onValueChange = { selfDetailsViewModel.updateTextField("bio", it) },
                label = { Text("Bio (Optional)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp),
                readOnly = uiState.isLoading,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                maxLines = 4
            )
            Spacer(modifier = Modifier.height(24.dp))

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

            uiState.errorMessage?.let { error ->
                if (!error.contains("Display Name", ignoreCase = true) &&
                    !error.contains("phone number", ignoreCase = true) &&
                    !error.contains("birthdate", ignoreCase = true)
                ) {
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

            TextButton(
                onClick = onLogout,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("Log Out")
            }

        }
    }

    if (uiState.showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { selfDetailsViewModel.showDatePicker(false) },
            confirmButton = {
                TextButton(
                    onClick = {
                        selfDetailsViewModel.updateBirthdate(datePickerState.selectedDateMillis)
                    },
                    enabled = confirmEnabled
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
}
package com.zhenbang.otw // Ensure package matches

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle // Import for state collection
import androidx.lifecycle.viewmodel.compose.viewModel // Import to get ViewModel instance
import com.zhenbang.otw.auth.RegisterUiState // Import your UI state
import com.zhenbang.otw.auth.RegisterViewModel // Import your ViewModel
import kotlinx.coroutines.delay

// Import Patterns for email validation
import android.util.Patterns

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    // Get instance of ViewModel - typically done here or passed from NavHost
    registerViewModel: RegisterViewModel = viewModel(),
    onNavigateToLogin: () -> Unit
) {
    // --- State Variables for UI Input ---
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var verificationCode by rememberSaveable { mutableStateOf("") }

    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var confirmPasswordVisible by rememberSaveable { mutableStateOf(false) }

    // --- State Variables for UI Validation Errors ---
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmPasswordError by remember { mutableStateOf<String?>(null) }
    var verificationCodeError by remember { mutableStateOf<String?>(null) }
    // General error state, primarily driven by ViewModel now
    var generalError by remember { mutableStateOf<String?>(null) }

    // --- Observe State from ViewModel ---
    val uiState by registerViewModel.uiState.collectAsStateWithLifecycle()

    // --- Derive UI properties from state ---
    val isLoading = uiState is RegisterUiState.SendingCode || uiState is RegisterUiState.Registering

    // --- Verification Code Timer State ---
    var isTimerRunning by rememberSaveable { mutableStateOf(false) }
    var remainingTime by rememberSaveable { mutableStateOf(0) }
    var requestCodeSentAtLeastOnce by rememberSaveable { mutableStateOf(false) }

    // --- Timer Logic ---
    LaunchedEffect(key1 = isTimerRunning) {
        if (isTimerRunning) {
            while (remainingTime > 0) {
                delay(1000) // Wait for 1 second
                remainingTime--
            }
            isTimerRunning = false // Timer finished
        }
    }

    // --- React to ViewModel State Changes (Side Effects) ---
    LaunchedEffect(uiState) {
        when (val state = uiState) { // Use 'state' variable for smart casting
            is RegisterUiState.Error -> {
                generalError = state.message
                delay(5000)
                registerViewModel.clearErrorState()
            }
            is RegisterUiState.RegistrationSuccess -> {
                // Registration was successful (backend verified code and created user)
                println("Registration Successful! Navigating to Login...")
                // TODO: Maybe show a success message (Toast/Snackbar) before navigating
                onNavigateToLogin() // Navigate back to login screen
                // Reset ViewModel state if needed, e.g., registerViewModel.resetState()
            }
            is RegisterUiState.CodeSent -> {
                println("UI Noticed Code Sent State (Optional: Show Snackbar/Toast)")
                // Usually just return to Idle handled in ViewModel is enough
            }
            is RegisterUiState.Idle -> {
                // Clear general error when returning to Idle from another state
                // Check previous state if needed, but often safe to just clear
                generalError = null
            }
            else -> {
                // Handle SendingCode, Registering (usually by showing loading indicators)
                // Reset error when moving into a loading state
                generalError = null
            }
        }
    }


    // --- Client-Side Validation Function ---
    // This checks formats and blanks, but NOT if the verification code is correct
    fun validateFields(): Boolean {
        // Reset local UI errors first
        emailError = null
        passwordError = null
        confirmPasswordError = null
        verificationCodeError = null
        // generalError = null // General error is now mainly set by ViewModel state effect

        var isValid = true

        if (email.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailError = "Enter a valid email address"
            isValid = false
        }

        val passwordValidationMessages = mutableListOf<String>()
        if (password.length < 8) { passwordValidationMessages.add("at least 8 characters") }
        if (!password.any { it.isDigit() }) { passwordValidationMessages.add("at least 1 number") }
        if (!password.any { it.isLowerCase() }) { passwordValidationMessages.add("at least 1 lowercase letter") }
        if (!password.any { it.isUpperCase() }) { passwordValidationMessages.add("at least 1 uppercase letter") }
        if (!password.any { !it.isLetterOrDigit() }) { passwordValidationMessages.add("at least 1 symbol") }

        if (passwordValidationMessages.isNotEmpty()) {
            passwordError = "Password must contain: ${passwordValidationMessages.joinToString(", ")}"
            isValid = false
        }

        if (passwordError == null && password != confirmPassword) {
            confirmPasswordError = "Passwords do not match"
            isValid = false
        } else if (passwordError != null && confirmPassword.isNotBlank() && password != confirmPassword) {
            confirmPasswordError = "Passwords do not match"
            isValid = false
        }

        if (verificationCode.isBlank()) {
            verificationCodeError = "Enter the verification code"
            isValid = false
        }
        // Optional: Add length check for code field if needed
        // if (verificationCode.length != 6 && !verificationCode.isBlank()){...}

        return isValid
    }

    // --- UI Definition ---
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Create Account") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text("Enter your details", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(16.dp))

            // General Error Display (Now driven by ViewModel state mostly)
            generalError?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Email Field
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; emailError = null; generalError = null }, // Clear local errors on change
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                isError = emailError != null || (uiState is RegisterUiState.Error && (uiState as RegisterUiState.Error).message.contains("email", ignoreCase = true)), // Show error based on local validation OR specific ViewModel error
                supportingText = { emailError?.let { Text(it) } },
                readOnly = isLoading // Disable input during loading
            )

            // Password Field
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; passwordError = null; generalError = null },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    IconButton(onClick = { passwordVisible = !passwordVisible }, enabled = !isLoading) { // Disable icon button too
                        Icon(imageVector = image, if (passwordVisible) "Hide password" else "Show password")
                    }
                },
                isError = passwordError != null,
                supportingText = { passwordError?.let { Text(it) } },
                readOnly = isLoading // Disable input during loading
            )

            // Confirm Password Field
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; confirmPasswordError = null; generalError = null },
                label = { Text("Confirm Password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    val image = if (confirmPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }, enabled = !isLoading) { // Disable icon button too
                        Icon(imageVector = image, if (confirmPasswordVisible) "Hide password" else "Show password")
                    }
                },
                isError = confirmPasswordError != null,
                supportingText = { confirmPasswordError?.let { Text(it) } },
                readOnly = isLoading // Disable input during loading
            )

            // Verification Code Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Verification Code Input
                OutlinedTextField(
                    value = verificationCode,
                    onValueChange = {
                        // Allow only digits and limit length (e.g., 6)
                        val filtered = it.filter { char -> char.isDigit() }
                        if (filtered.length <= 6) { // Example limit
                            verificationCode = filtered
                        }
                        verificationCodeError = null
                        generalError = null
                    },
                    label = { Text("Code") }, // Shorter label
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    isError = verificationCodeError != null || (uiState is RegisterUiState.Error && (uiState as RegisterUiState.Error).message.contains("code", ignoreCase = true)), // Show error based on local validation OR specific ViewModel error,
                    supportingText = { verificationCodeError?.let { Text(it) } },
                    readOnly = isLoading // Disable input during loading
                )

                // Send/Resend Code Button
                Button(
                    onClick = {
                        // Clear errors, call ViewModel, start UI timer
                        generalError = null
                        emailError = null // Clear email error specifically if user tries to resend
                        registerViewModel.sendVerificationCode(email)
                        requestCodeSentAtLeastOnce = true
                        remainingTime = 60
                        isTimerRunning = true
                    },
                    enabled = !isTimerRunning && email.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email).matches() && !isLoading, // Check if not loading
                    modifier = Modifier.wrapContentWidth()
                ) {
                    if (uiState is RegisterUiState.SendingCode) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary // Or appropriate color
                        )
                    } else {
                        Text(
                            if (isTimerRunning) { "Resend (${remainingTime}s)" }
                            else { if (requestCodeSentAtLeastOnce) "Resend Code" else "Send Code" }
                        )
                    }
                }
            } // End Row

            Spacer(modifier = Modifier.height(16.dp))

            // Register Button
            Button(
                onClick = {
                    // Clear error, validate locally, then call ViewModel
                    generalError = null
                    if (validateFields()) {
                        registerViewModel.registerUser(
                            email = email,
                            password = password,
                            code = verificationCode
                        )
                    }
                },
                enabled = !isLoading, // Disable if loading
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState is RegisterUiState.Registering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Register")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Link to Login Screen
            TextButton(onClick = onNavigateToLogin, enabled = !isLoading) { // Disable during load
                Text("Already have an account? Login")
            }
        }
    }
}
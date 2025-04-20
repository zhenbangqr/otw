package com.zhenbang.otw

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
import kotlinx.coroutines.delay

// TODO: You'll likely want a dedicated RegisterViewModel for API calls
// import com.zhenbang.otw.auth.RegisterViewModel
// import androidx.lifecycle.viewmodel.compose.viewModel


/**
 * Composable function for the User Registration Screen.
 *
 * Includes fields for email, password, password confirmation, and a verification code
 * with a timed resend functionality.
 *
 * // TODO: Pass ViewModel instance for actual logic
 * // @param registerViewModel The ViewModel instance managing registration logic.
 * @param onNavigateToLogin Callback function to navigate back to the Login screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    // registerViewModel: RegisterViewModel = viewModel(), // Uncomment when ViewModel is ready
    onNavigateToLogin: () -> Unit
) {
    // --- State Variables ---
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var verificationCode by rememberSaveable { mutableStateOf("") }

    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var confirmPasswordVisible by rememberSaveable { mutableStateOf(false) }

    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmPasswordError by remember { mutableStateOf<String?>(null) }
    var verificationCodeError by remember { mutableStateOf<String?>(null) }
    var generalError by remember { mutableStateOf<String?>(null) } // For general registration errors

    // Verification Code Timer State
    var isTimerRunning by rememberSaveable { mutableStateOf(false) }
    var remainingTime by rememberSaveable { mutableStateOf(0) }
    var requestCodeSentAtLeastOnce by rememberSaveable { mutableStateOf(false) } // To change "Send" to "Resend"

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

    // --- Helper Functions ---
    fun validateFields(): Boolean {
        // Reset errors
        emailError = null
        passwordError = null
        confirmPasswordError = null
        verificationCodeError = null
        generalError = null
        var isValid = true

        // Basic Validations (Add more specific checks as needed)
        if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailError = "Enter a valid email address"
            isValid = false
        }
        if (password.length < 8) { // Example: Minimum 8 characters
            passwordError = "Password must be at least 8 characters"
            isValid = false
        }
        if (password != confirmPassword) {
            confirmPasswordError = "Passwords do not match"
            isValid = false
        }
        if (verificationCode.isBlank()) { // Example: Check if not blank
            verificationCodeError = "Enter the verification code"
            isValid = false
        }
        // Add length check if code has fixed length, e.g. 6 digits
        // if (verificationCode.length != 6) { ... }


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
                .padding(horizontal = 24.dp, vertical = 16.dp) // Consistent padding
                .imePadding(), // Adjusts padding when keyboard appears
            verticalArrangement = Arrangement.spacedBy(8.dp), // Spacing between elements
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text("Enter your details", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(16.dp))

            // General Error Display
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
                onValueChange = { email = it; emailError = null; generalError = null },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                isError = emailError != null,
                supportingText = { emailError?.let { Text(it) } }
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
                    val description = if (passwordVisible) "Hide password" else "Show password"
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(imageVector = image, description)
                    }
                },
                isError = passwordError != null,
                supportingText = { passwordError?.let { Text(it) } }
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
                    val description = if (confirmPasswordVisible) "Hide password" else "Show password"
                    IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                        Icon(imageVector = image, description)
                    }
                },
                isError = confirmPasswordError != null,
                supportingText = { confirmPasswordError?.let { Text(it) } }
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
                    onValueChange = { verificationCode = it.filter { char -> char.isDigit() } ; verificationCodeError = null; generalError = null }, // Allow only digits
                    label = { Text("Verification Code") },
                    modifier = Modifier.weight(1f), // Take available space
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), // Use number keyboard
                    singleLine = true,
                    isError = verificationCodeError != null,
                    supportingText = { verificationCodeError?.let { Text(it) } }
                )

                // Send/Resend Code Button
                Button(
                    onClick = {
                        // TODO: Call ViewModel to send code request to the entered email
                        println("Send/Resend Code clicked for email: $email")
                        // Example: registerViewModel.sendVerificationCode(email)
                        // --- Start Timer ---
                        requestCodeSentAtLeastOnce = true // Mark as sent
                        remainingTime = 60 // Reset timer duration
                        isTimerRunning = true // Start the countdown
                    },
                    enabled = !isTimerRunning && email.isNotBlank() && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches(), // Enable only if timer not running and email is valid
                    modifier = Modifier.wrapContentWidth() // Adjust width to content
                ) {
                    Text(
                        if (isTimerRunning) {
                            "Resend in ${remainingTime}s"
                        } else {
                            if (requestCodeSentAtLeastOnce) "Resend Code" else "Send Code"
                        }
                    )
                }
            }
            // Add supporting text for verification code field if error exists but doesn't fit in the outlined field's slot
            if (verificationCodeError != null && verificationCode.length > 0) { // Check length condition if needed
                Text(
                    text = verificationCodeError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp) // Align with text field approx
                )
            }


            Spacer(modifier = Modifier.height(16.dp)) // Space before register button

            // Register Button
            Button(
                onClick = {
                    if (validateFields()) {
                        // TODO: Call ViewModel to perform registration
                        println("Register clicked: Email=$email, Code=$verificationCode")
                        // Example: registerViewModel.registerUser(email, password, verificationCode) { success, error ->
                        //    if (!success) { generalError = error ?: "Registration failed" }
                        //    else { /* Handle success, maybe navigate */ }
                        // }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Register")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Link to Login Screen
            TextButton(onClick = onNavigateToLogin) {
                Text("Already have an account? Login")
            }
        }
    }
}

// You would typically preview this within your main Activity or a dedicated preview file
// @Preview(showBackground = true)
// @Composable
// fun RegisterScreenPreview() {
//     YourAppTheme { // Replace with your actual theme
//          RegisterScreen(onNavigateToLogin = {})
//     }
// }
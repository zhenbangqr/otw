package com.zhenbang.otw.ui.screen // Adjust package if needed

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
import androidx.compose.ui.platform.LocalContext // Import context for showing Toast/Snackbar
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
// Removed kotlinx.coroutines.delay as it's not used here anymore
import android.util.Patterns
import android.widget.Toast // Import Toast for simple feedback
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.zhenbang.otw.data.state.RegisterUiState
import com.zhenbang.otw.ui.viewmodel.RegisterViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    registerViewModel: RegisterViewModel = viewModel(),
    onNavigateToLogin: () -> Unit,
    onNavigateToVerify: (email: String) -> Unit
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }

    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var confirmPasswordVisible by rememberSaveable { mutableStateOf(false) }

    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmPasswordError by remember { mutableStateOf<String?>(null) }
    var generalError by remember { mutableStateOf<String?>(null) }

    val uiState by registerViewModel.uiState.collectAsStateWithLifecycle()

    val isLoading = uiState is RegisterUiState.Registering

    val context = LocalContext.current

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is RegisterUiState.Error -> {
                generalError = state.message
            }
            is RegisterUiState.AccountCreatedPendingVerification -> {
                println("Account Created! Navigating to Verification Screen...")
                Toast.makeText(context, "Account created! Check your email for verification link.", Toast.LENGTH_LONG).show()
                onNavigateToVerify(email)
                registerViewModel.resetState()
            }
            is RegisterUiState.Idle -> {
                generalError = null
            }
            is RegisterUiState.Registering -> {
                generalError = null
            }
        }
    }

    fun validateFields(): Boolean {
        emailError = null
        passwordError = null
        confirmPasswordError = null
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
        } else if (confirmPassword.isBlank()) {
            confirmPasswordError = "Please confirm your password"
            isValid = false
        }
        return isValid
    }

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
                .verticalScroll(rememberScrollState())
                .imePadding(),
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
                isError = emailError != null || (uiState is RegisterUiState.Error && (uiState as RegisterUiState.Error).message.contains("email", ignoreCase = true)),
                supportingText = { emailError?.let { Text(it) } },
                readOnly = isLoading
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Password Field
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; passwordError = null; confirmPasswordError = null ; generalError = null },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    IconButton(onClick = { passwordVisible = !passwordVisible }, enabled = !isLoading) {
                        Icon(imageVector = image, if (passwordVisible) "Hide password" else "Show password")
                    }
                },
                isError = passwordError != null,
                supportingText = { passwordError?.let { Text(it, modifier=Modifier.fillMaxWidth()) } },
                readOnly = isLoading
            )
            Spacer(modifier = Modifier.height(12.dp))

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
                    IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }, enabled = !isLoading) {
                        Icon(imageVector = image, if (confirmPasswordVisible) "Hide password" else "Show password")
                    }
                },
                isError = confirmPasswordError != null,
                supportingText = { confirmPasswordError?.let { Text(it) } },
                readOnly = isLoading
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    generalError = null
                    if (validateFields()) {
                        registerViewModel.registerUser(
                            email = email,
                            password = password
                        )
                    }
                },
                enabled = !isLoading,
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

            Spacer(modifier = Modifier.height(16.dp))

            // Link to Login Screen
            TextButton(onClick = onNavigateToLogin, enabled = !isLoading) {
                Text("Already have an account? Login")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
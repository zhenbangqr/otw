package com.zhenbang.otw // Adjust package if needed

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhenbang.otw.auth.AuthViewModel // Adjust import if needed
import kotlinx.coroutines.flow.collectLatest // Import for collectLatest

/**
 * Composable function for the Login Screen UI.
 * Provides options for email/password login and Google OAuth login.
 * Observes the authentication state from AuthViewModel for Google login status.
 *
 * @param authViewModel The ViewModel instance managing the Google authentication logic.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    authViewModel: AuthViewModel = viewModel(),
    onNavigateToRegister: () -> Unit //
)  {
    val googleAuthState by remember { derivedStateOf { authViewModel.userAuthState } }

    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    // --- ActivityResult Launchers ---
    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { intent -> authViewModel.handleAuthorizationResponse(intent) }
                ?: authViewModel.handleAuthorizationResponse(Intent()) // Handle null data case
        } else {
            authViewModel.handleAuthorizationResponse(result.data ?: Intent()) // Handle cancellation/failure
        }
        println("Google Auth completed with result code: ${result.resultCode}")
    }

    // Launcher for the Google End Session Intent (from logout)
    val endSessionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        println("Google End session flow completed with result code: ${result.resultCode}")
    }

    // --- Effects ---
    // Listen for Google End Session events from ViewModel
    LaunchedEffect(key1 = Unit) {
        authViewModel.endSessionEvent.collectLatest { intent ->
            println("LoginScreen: Received end session event, launching intent.")
            try {
                endSessionLauncher.launch(intent)
            } catch (e: Exception) {
                println("Error launching end session intent: ${e.message}")
            }
        }
    }

    // --- UI Definition ---
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("On The Way Login") }) // Updated title
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp, vertical = 16.dp), // Adjusted padding
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Check Google authorization state
            if (googleAuthState.isAuthorized) {
                Text("Status: Logged In (Google)", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Access Token:", style = MaterialTheme.typography.bodyMedium)
                Text(googleAuthState.accessToken?.take(20)?.let { "$it..." } ?: "N/A", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text("ID Token:", style = MaterialTheme.typography.bodyMedium)
                Text(googleAuthState.idToken?.take(20)?.let { "$it..." } ?: "N/A", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = {
                    authViewModel.performActionWithFreshTokens { accessToken, _, error ->
                        if (error != null) {
                            println("Failed to get fresh token for API call: ${error.message}")
                        } else {
                            println("Attempting API call with fresh token: ${accessToken?.take(10)}...")
                            // TODO: Make your actual API call here using the accessToken
                        }
                    }
                }) { Text("Call Protected API") }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { authViewModel.logout() }) { Text("Log Out from Google") }

            } else {
                // --- Logged Out UI State (Email/Password + Google) ---

                Text("Login or Register", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(24.dp))

                // Display Google Auth error if one exists
                googleAuthState.error?.let {
                    Text(
                        text = "Google Login Error: $it",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
                // TODO: Add display for email/password login errors if needed

                // Email Field
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Password Field
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
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
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Confirm (Email/Password Login) Button
                Button(
                    onClick = {
                        // TODO: Implement actual Email/Password Login Logic here
                        // Usually involves calling your backend API
                        println("Confirm Clicked: Email=$email, Password=$password")
                        // Example: authViewModel.loginWithEmailPassword(email, password)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Confirm")
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Register Account Text (Clickable)
                TextButton(
                    onClick = {
                        println("Register Account Clicked!")
                        onNavigateToRegister()
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Register Account")
                }


                Spacer(modifier = Modifier.height(24.dp))
                Text("OR", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(24.dp))

                // Google Login Button
                Button(
                    onClick = {
                        // Clear any previous Google auth errors before retrying
                        // Note: You might need a specific function in ViewModel to clear only the error state
                        // authViewModel.clearErrorState()
                        val authRequest = authViewModel.buildAuthorizationRequest()
                        val authIntent = authViewModel.prepareAuthIntent(authRequest)
                        authLauncher.launch(authIntent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Consider adding a Google icon here too
                    Text("Log In with Google")
                }
            }
        }
    }
}

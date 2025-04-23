package com.zhenbang.otw // Adjust package if needed

import android.app.Activity
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhenbang.otw.auth.AuthViewModel
import com.zhenbang.otw.auth.LoginViewModel
import com.zhenbang.otw.auth.LoginUiState
import com.zhenbang.otw.auth.UserAuthState
import kotlinx.coroutines.flow.collectLatest


/**
 * Composable function for the Login Screen UI.
 * Provides options for email/password login and Google OAuth login.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    // Inject both ViewModels
    authViewModel: AuthViewModel = viewModel(),
    loginViewModel: LoginViewModel = viewModel(),
    onNavigateToRegister: () -> Unit,
    onLoginSuccess: () -> Unit, // Navigate to main app after ANY successful login
    onNavigateToVerify: (email: String) -> Unit // Navigate to verification screen if needed
) {
    // --- State from ViewModels ---
    val googleAuthState by authViewModel.userAuthState.collectAsStateWithLifecycle()
    val loginState by loginViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // --- Input State ---
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    // --- Derived State ---
    val isLoading = googleAuthState.isLoading || // Use isLoading from UserAuthState
            loginState is LoginUiState.Loading    // Email/Password login in progress

    // --- ActivityResult Launchers (Mostly for AppAuth) ---
    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.let { intent ->
            authViewModel.handleAuthorizationResponse(intent)
        } ?: run {
            // Handle cancellation or error where data is null
            if (result.resultCode != Activity.RESULT_OK) {
                Toast.makeText(context, "Google Sign-In cancelled or failed.", Toast.LENGTH_SHORT).show()
                // You might want to reset any specific Google loading state here if needed
            }
            // Pass empty intent if needed by viewmodel logic, or handle error directly
            authViewModel.handleAuthorizationResponse(Intent()) // Or add specific error handling
        }
        println("Google Auth Activity completed with result code: ${result.resultCode}")
    }

    val endSessionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        println("Google End session flow completed with result code: ${result.resultCode}")
        // After Google logout completes, ensure local state is also cleared (ViewModel should handle this)
    }

    // --- Effects ---

    // Listen for Google End Session events
    LaunchedEffect(key1 = Unit) {
        authViewModel.endSessionEvent.collectLatest { intent ->
            println("LoginScreen: Received end session event, launching intent.")
            try {
                endSessionLauncher.launch(intent)
            } catch (e: Exception) {
                println("Error launching end session intent: ${e.message}")
                // Show error to user?
            }
        }
    }

    // React to Google Auth State changes
    LaunchedEffect(googleAuthState) {
        if (googleAuthState.isAuthorized && googleAuthState.idToken != null) {
            println("Google Sign-In successful via AppAuth!")
            // TODO: Optional: Trigger Firebase Linking here if desired
            // authViewModel.linkFirebaseAccount(googleAuthState.idToken) // Need to implement this in AuthViewModel
            Toast.makeText(context, "Google Sign-In Success!", Toast.LENGTH_SHORT).show()
            onLoginSuccess() // Navigate to main app
        }
        // Error handled via generalError display now
    }

    // React to Email/Password Login State changes
    LaunchedEffect(loginState) {
        when (val state = loginState) {
            is LoginUiState.LoginSuccess -> {
                Toast.makeText(context, "Login Successful!", Toast.LENGTH_SHORT).show()
                onLoginSuccess() // Navigate to main app
                loginViewModel.resetState() // Reset state after handling
            }
            is LoginUiState.VerificationNeeded -> {
                Toast.makeText(context, "Please verify your email.", Toast.LENGTH_SHORT).show()
                onNavigateToVerify(state.email) // Navigate to verification screen
                loginViewModel.resetState() // Reset state after handling
            }
            // Loading handled by isLoading derived state
            // Error handled by generalError display
            // Idle: No action needed
            else -> {}
        }
    }


    // --- UI Definition ---
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("On The Way Login") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .imePadding(), // Handles keyboard overlap
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Display Login/Register Title when logged out
            if (!googleAuthState.isAuthorized /* && loginState !is LoginSuccess */ ) { // Add check for email login state if needed
                Text("Login or Register", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(24.dp))
            }

            // --- Error Display Area ---
            // Combine errors from both ViewModels if needed, or show separately
            val googleError = googleAuthState.error
            val loginError = if (loginState is LoginUiState.Error) (loginState as LoginUiState.Error).message else null
            val displayError = loginError ?: googleError // Prioritize login error message

            displayError?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // --- Logged IN Info (Example - Can be removed once navigation works) ---
            if (googleAuthState.isAuthorized /* || loginState is LoginSuccess */) {
                Text("Status: Logged In", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))
                // Maybe show user email? (Needs fetching for Google)
                // Button(onClick = { /* Call API example */ }) { Text("Call API") }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    if (googleAuthState.isAuthorized) authViewModel.logout()
                    // else loginViewModel.logout() // Add logout to LoginViewModel too
                }) { Text("Log Out") }

            } else {
                // --- Logged OUT UI ---

                // Email Field
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; loginViewModel.clearErrorState() /* Clear error on typing */ },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    enabled = !isLoading, // Disable when loading
                    isError = loginState is LoginUiState.Error // Show error state
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Password Field
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; loginViewModel.clearErrorState() },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                        val description = if (passwordVisible) "Hide password" else "Show password"
                        IconButton(onClick = { passwordVisible = !passwordVisible }, enabled = !isLoading) { // Disable icon too
                            Icon(imageVector = image, description)
                        }
                    },
                    enabled = !isLoading, // Disable when loading
                    isError = loginState is LoginUiState.Error // Show error state
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Login with Email Button
                Button(
                    onClick = {
                        loginViewModel.signInUser(email, password)
                    },
                    enabled = !isLoading, // Disable when loading
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (loginState is LoginUiState.Loading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    } else {
                        Text("Login with Email")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Register Account Text (Clickable)
                TextButton(
                    onClick = onNavigateToRegister,
                    enabled = !isLoading, // Disable when loading
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
                        authViewModel.startAuthorization()
                        // Consider clearing specific Google error state if needed before retry
                        // authViewModel.clearGoogleError() // Implement if needed
                        val authRequest = authViewModel.buildAuthorizationRequest()
                        val authIntent = authViewModel.prepareAuthIntent(authRequest)
                        authLauncher.launch(authIntent)
                    },
                    enabled = !isLoading, // Disable when loading
                    modifier = Modifier.fillMaxWidth()
                    // Add Google branding if desired
                ) {
                    // Show loading state specific to Google login if possible
                    // For now, the general isLoading check disables the button
                    if (googleAuthState.isLoading) { // Check the isLoading field from UserAuthState
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    } else {
                        Text("Log In with Google")
                    }
                }
            } // End Logged Out UI
        } // End Column
    } // End Scaffold
}
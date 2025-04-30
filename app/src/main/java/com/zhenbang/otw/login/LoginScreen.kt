package com.zhenbang.otw.login // Adjust package if needed

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState // Import rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll // Import verticalScroll
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
// Use correct import if LoginViewModel is in auth package
import com.zhenbang.otw.login.LoginViewModel
import com.zhenbang.otw.login.LoginUiState


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    authViewModel: AuthViewModel = viewModel(),
    loginViewModel: LoginViewModel = viewModel(),
    onNavigateToRegister: () -> Unit,
    onLoginSuccess: () -> Unit,
    onNavigateToVerify: (email: String) -> Unit
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
    val isLoading = loginState is LoginUiState.Loading || googleAuthState.isLoading

    // --- ActivityResult Launcher for AppAuth Authorization ---
    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.let { intent ->
            authViewModel.handleAuthorizationResponse(intent)
        } ?: run {
            if (result.resultCode != Activity.RESULT_OK) {
                Toast.makeText(context, "Google Sign-In cancelled or failed.", Toast.LENGTH_SHORT).show()
                loginViewModel.resetState()
            }
            authViewModel.handleAuthorizationResponse(Intent())
        }
        println("Google Auth Activity completed with result code: ${result.resultCode}")
    }

    // React to Google Auth State changes (from AuthViewModel)
    LaunchedEffect(googleAuthState) {
        if (googleAuthState.isAuthorized && googleAuthState.idToken != null) {
            println("Google Sign-In successful via AppAuth! Triggering Firebase link...")
            Toast.makeText(context, "Google Sign-In Success! Finalizing...", Toast.LENGTH_SHORT).show()
            loginViewModel.handleGoogleSignInSuccess(googleAuthState.idToken!!)
        }
        googleAuthState.error?.let {
            if (loginState !is LoginUiState.Error) {
                Toast.makeText(context, "Google Sign-In Error: $it", Toast.LENGTH_LONG).show()
            }
        }
    }

    // React to LoginViewModel State changes (Handles both Email/Pass and Google linking results)
    LaunchedEffect(loginState) {
        when (val state = loginState) {
            is LoginUiState.LoginVerifiedSuccess -> {
                onLoginSuccess()
                loginViewModel.resetState()
            }
            is LoginUiState.VerificationNeeded -> {
                Toast.makeText(context, "Please verify your email.", Toast.LENGTH_SHORT).show()
                onNavigateToVerify(state.email)
                loginViewModel.resetState()
            }
            is LoginUiState.Error -> {
                println("LoginScreen observed LoginViewModel Error: ${state.message}")
            }
            else -> {}
        }
    }


    // --- UI Definition ---
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("On The Way Login") })
        }
    ) { paddingValues ->
        // *** Make Column scrollable and adjust arrangement ***
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp, vertical = 16.dp) // Keep padding
                .verticalScroll(rememberScrollState()) // <<<--- ADD SCROLLING
                .imePadding(),
            // verticalArrangement = Arrangement.Center, // <<<--- REMOVE OR CHANGE THIS
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically), // <<<--- Example: Add spacing, keep vertical centering
            // OR Arrangement.spacedBy(8.dp) // Just add spacing, align top
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // --- Add Spacer at the top if not using CenterVertically arrangement ---
            // if(verticalArrangement != Arrangement.Center) {
            //    Spacer(modifier = Modifier.height(32.dp)) // Add space at the top
            // }

            Text("Login or Register", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(24.dp)) // Keep spacing

            // --- Error Display Area ---
            val displayError = if (loginState is LoginUiState.Error) {
                (loginState as LoginUiState.Error).message
            } else {
                null
            }

            // Use AnimatedVisibility or similar for smoother error appearance/disappearance
            if (displayError != null) {
                Text(
                    text = displayError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 8.dp) // Reduced bottom padding
                )
            }

            // Email Field
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; loginViewModel.clearErrorState() },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                enabled = !isLoading,
                isError = loginState is LoginUiState.Error
            )
            // Spacer removed, using Arrangement.spacedBy

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
                    IconButton(onClick = { passwordVisible = !passwordVisible }, enabled = !isLoading) {
                        Icon(imageVector = image, description)
                    }
                },
                enabled = !isLoading,
                isError = loginState is LoginUiState.Error
            )
            // Spacer removed

            // Login with Email Button
            Button(
                onClick = {
                    loginViewModel.signInUser(email, password)
                },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (loginState is LoginUiState.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                } else {
                    Text("Login with Email")
                }
            }
            // Spacer removed

            // Register Account Text Button
            TextButton(
                onClick = onNavigateToRegister,
                enabled = !isLoading,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Register Account")
            }

            Spacer(modifier = Modifier.height(16.dp)) // Adjusted spacing
            Text("OR", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(16.dp)) // Adjusted spacing

            // Google Login Button
            Button(
                onClick = {
                    authViewModel.startAuthorization()
                    val authRequest = authViewModel.buildAuthorizationRequest()
                    val authIntent = authViewModel.prepareAuthIntent(authRequest)
                    try {
                        authLauncher.launch(authIntent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Could not launch Google Sign-In.", Toast.LENGTH_SHORT).show()
                        authViewModel.logout()
                        loginViewModel.resetState()
                        println("Error launching auth intent: ${e.message}")
                    }
                },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                } else {
                    Text("Log In with Google")
                }
            }

            // --- Add Spacer at the bottom if not using CenterVertically arrangement ---
            // if(verticalArrangement != Arrangement.Center) {
            //    Spacer(modifier = Modifier.height(32.dp)) // Add space at the bottom
            // }

        } // End Column
    } // End Scaffold
}

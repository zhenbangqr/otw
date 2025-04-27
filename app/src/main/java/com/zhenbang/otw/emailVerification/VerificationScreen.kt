package com.zhenbang.otw.emailVerification // Adjust package if needed

// *** Ensure these imports are present and correct ***
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
//-----------------------------------------------------

// Removed duplicate package declaration


// Define UI States specific to this screen (Can be moved to its own file: VerificationUiState.kt)
// sealed class VerificationUiState { // Keep this defined, preferably in its own file
//     object Idle : VerificationUiState()
//     object Resending : VerificationUiState()
//     object Checking : VerificationUiState()
//     data class Error(val message: String) : VerificationUiState()
// }


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerificationScreen(
    // Pass email for display, or get from ViewModel if needed
    email: String?, // Make email nullable or handle appropriately if unavailable
    // *** CHANGED: Use VerificationViewModel ***
    verificationViewModel: VerificationViewModel = viewModel(),
    onNavigateToLogin: () -> Unit,
    onNavigateBack: () -> Unit // Optional: Go back
) {
    val context = LocalContext.current
    // *** CHANGED: Observe state from verificationViewModel ***
    val uiState by verificationViewModel.uiState.collectAsStateWithLifecycle() // Observe state
    // *** CHANGED: Observe verification status from verificationViewModel ***
    val isVerifiedStatus = verificationViewModel.isVerified

    // Resend Button Timer State
    var isResendTimerRunning by rememberSaveable { mutableStateOf(false) }
    var resendRemainingTime by rememberSaveable { mutableStateOf(0) }

    // Resend Timer Logic (remains the same)
    LaunchedEffect(key1 = isResendTimerRunning) {
        if (isResendTimerRunning) {
            while (resendRemainingTime > 0) {
                delay(1000)
                resendRemainingTime--
            }
            isResendTimerRunning = false
        }
    }

    // Handle ViewModel State Effects (Show messages, navigate on success)
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is VerificationUiState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                // *** CHANGED: Use verificationViewModel ***
                verificationViewModel.clearVerificationError() // Clear state after showing
            }
            // Add handling for other states if needed (e.g., show confirmation on resend)
            else -> {}
        }
    }

    // Handle Verification Check Result (from ViewModel)
    // *** CHANGED: Observe verificationViewModel.isVerified ***
    LaunchedEffect(isVerifiedStatus) { // Renamed variable for clarity
        if (isVerifiedStatus == true) {
            Toast.makeText(context, "Email verified successfully!", Toast.LENGTH_SHORT).show()
            onNavigateToLogin() // Navigate to login now they are verified
            // *** CHANGED: Use verificationViewModel ***
            verificationViewModel.resetVerificationStatus() // Reset flag
        } else if (isVerifiedStatus == false) {
            // Only show toast if the check was explicitly triggered and failed
            if(uiState is VerificationUiState.Checking){ // Check the uiState flow
                Toast.makeText(context, "Email not verified yet.", Toast.LENGTH_SHORT).show()
            }
            // Reset verification status flag after handling
            // *** CHANGED: Use verificationViewModel ***
            verificationViewModel.resetVerificationStatus()
            // Reset UI state only if it wasn't an error (handled in the other effect)
            if(uiState !is VerificationUiState.Error) {
                verificationViewModel.resetVerificationStateToIdle()
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Verify Your Email") }) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Verification Link Sent!",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "We've sent a verification link to ${email ?: "your email address"}. Please click the link in the email to complete your registration.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))

            // Determine general loading state for enabling/disabling buttons
            val isLoading = uiState is VerificationUiState.Checking || uiState is VerificationUiState.Resending

            // Resend Verification Button
            Button(
                onClick = {
                    // *** CHANGED: Use verificationViewModel ***
                    verificationViewModel.resendVerificationLink()
                    resendRemainingTime = 60 // Start 60s timer
                    isResendTimerRunning = true
                    // Consider showing Toast from LaunchedEffect based on state instead of here
                    // Toast.makeText(context, "Resending verification email...", Toast.LENGTH_SHORT).show()
                },
                enabled = !isResendTimerRunning && !isLoading, // Disable during timer and loading states
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState is VerificationUiState.Resending) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(if (isResendTimerRunning) "Resend Link (${resendRemainingTime}s)" else "Resend Verification Link")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Button to check if verified / continue
            OutlinedButton(
                onClick = {
                    // *** CHANGED: Use verificationViewModel ***
                    verificationViewModel.checkVerificationStatus() // Trigger check in ViewModel
                },
                enabled = !isLoading, // Disable during loading states
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState is VerificationUiState.Checking) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Text("I've Verified / Continue")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            // Optional: Button to go back
            TextButton(onClick = onNavigateBack, enabled = !isLoading) { // Disable during loading
                Text("Go Back")
            }
        }
    }
}
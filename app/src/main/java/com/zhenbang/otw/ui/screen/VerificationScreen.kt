package com.zhenbang.otw.ui.screen

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
import com.zhenbang.otw.data.state.VerificationUiState
import com.zhenbang.otw.ui.viewmodel.VerificationViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerificationScreen(
    email: String?,
    verificationViewModel: VerificationViewModel = viewModel(),
    onNavigateToLogin: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by verificationViewModel.uiState.collectAsStateWithLifecycle()
    val isVerifiedStatus = verificationViewModel.isVerified

    var isResendTimerRunning by rememberSaveable { mutableStateOf(false) }
    var resendRemainingTime by rememberSaveable { mutableStateOf(0) }

    LaunchedEffect(key1 = isResendTimerRunning) {
        if (isResendTimerRunning) {
            while (resendRemainingTime > 0) {
                delay(1000)
                resendRemainingTime--
            }
            isResendTimerRunning = false
        }
    }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is VerificationUiState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                verificationViewModel.clearVerificationError()
            }

            else -> {}
        }
    }

    LaunchedEffect(isVerifiedStatus) {
        if (isVerifiedStatus == true) {
            Toast.makeText(context, "Email verified successfully!", Toast.LENGTH_SHORT).show()
            onNavigateToLogin()
            verificationViewModel.resetVerificationStatus()
        } else if (isVerifiedStatus == false) {
            if (uiState is VerificationUiState.Checking) {
                Toast.makeText(context, "Email not verified yet.", Toast.LENGTH_SHORT).show()
            }
            verificationViewModel.resetVerificationStatus()
            if (uiState !is VerificationUiState.Error) {
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

            val isLoading =
                uiState is VerificationUiState.Checking || uiState is VerificationUiState.Resending

            Button(
                onClick = {
                    verificationViewModel.resendVerificationLink()
                    resendRemainingTime = 60
                    isResendTimerRunning = true
                },
                enabled = !isResendTimerRunning && !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState is VerificationUiState.Resending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(if (isResendTimerRunning) "Resend Link (${resendRemainingTime}s)" else "Resend Verification Link")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = {
                    verificationViewModel.checkVerificationStatus()
                },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState is VerificationUiState.Checking) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Text("I've Verified / Continue")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onNavigateBack, enabled = !isLoading) {
                Text("Go Back")
            }
        }
    }
}
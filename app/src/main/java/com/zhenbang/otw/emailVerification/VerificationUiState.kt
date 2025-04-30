package com.zhenbang.otw.emailVerification

// Represents the different states the Verification UI can be in
sealed class VerificationUiState {
    object Idle : VerificationUiState()       // Waiting for user action
    object Resending : VerificationUiState()  // Resend link request in progress
    object Checking : VerificationUiState()   // Checking verification status in progress
    data class Error(val message: String) : VerificationUiState() // An error occurred
}
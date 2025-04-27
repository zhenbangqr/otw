package com.zhenbang.otw.login

sealed class LoginUiState {
    object Idle : LoginUiState()                                // Waiting for user input
    object Loading : LoginUiState()                             // Sign-in or verification check in progress
    object LoginVerifiedSuccess : LoginUiState()                        // Sign-in successful AND email verified
    data class VerificationNeeded(val email: String) : LoginUiState() // Sign-in successful BUT email not verified
    data class Error(val message: String) : LoginUiState()      // An error occurred during login or verification check
}
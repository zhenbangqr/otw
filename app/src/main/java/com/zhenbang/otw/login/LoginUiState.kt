package com.zhenbang.otw.login

// Ensure LoginUiState is defined correctly
sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    object LoginVerifiedSuccess : LoginUiState() // State for verified success
    data class VerificationNeeded(val email: String) : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}
package com.zhenbang.otw.login

sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    object LoginVerifiedSuccess : LoginUiState()
    data class VerificationNeeded(val email: String) : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}
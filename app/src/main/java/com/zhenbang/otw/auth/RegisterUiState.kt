package com.zhenbang.otw.auth

// Represents the different states the Registration UI can be in
sealed class RegisterUiState {
    object Idle : RegisterUiState()
    object Registering : RegisterUiState()
    object AccountCreatedPendingVerification : RegisterUiState()
    data class Error(val message: String) : RegisterUiState()
}
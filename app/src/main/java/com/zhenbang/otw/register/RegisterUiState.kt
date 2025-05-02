package com.zhenbang.otw.register

sealed class RegisterUiState {
    object Idle : RegisterUiState()
    object Registering : RegisterUiState()
    object AccountCreatedPendingVerification : RegisterUiState()
    data class Error(val message: String) : RegisterUiState()
}
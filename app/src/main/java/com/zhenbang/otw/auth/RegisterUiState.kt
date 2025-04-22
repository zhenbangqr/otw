package com.zhenbang.otw.auth

// Represents the different states the Registration UI can be in
sealed class RegisterUiState {
    object Idle : RegisterUiState() // Initial state or after a successful operation resets
    object SendingCode : RegisterUiState() // Waiting for the code to be sent
    object CodeSent : RegisterUiState() // Code successfully requested (optional state)
    object Registering : RegisterUiState() // Waiting for registration attempt to complete
    object RegistrationSuccess : RegisterUiState() // Registration completed successfully
    data class Error(val message: String) : RegisterUiState() // An error occurred
}
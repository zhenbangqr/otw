package com.zhenbang.otw.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
// import com.your_app.data.AuthRepository // Import your actual repository
// import com.your_app.data.model.ApiError // Import custom error types if you have them
import kotlinx.coroutines.delay // Keep for simulation, replace later
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.lang.IllegalArgumentException // Example Exception for simulation
import kotlin.random.Random // Import Random

// --- Assume you have a Repository for network calls ---
interface AuthRepository {
    // Define suspend functions for your API calls
    suspend fun sendVerificationCode(email: String): Result<Unit> // Using kotlin.Result for simplicity
    suspend fun registerUser(email: String, pass: String, code: String): Result<Unit> // Or Result<UserInfo> etc.
}
// --- End Repository Assumption ---


class RegisterViewModel(application: Application) : AndroidViewModel(application) {

    // --- Fake Repository with Code Generation & Simulated Storage ---
    // TODO: Replace this with a real repository implementation talking to your backend!
    private val authRepository: AuthRepository = object : AuthRepository {

        // Simulate temporary storage for verification codes (Email -> Code)
        private val simulatedCodeStorage = mutableMapOf<String, String>()

        override suspend fun sendVerificationCode(email: String): Result<Unit> {
            delay(1500) // Simulate network latency

            // Simulate potential failure based on email for testing
            if (email.contains("fail")) {
                println("FakeRepo: Simulated network error sending code to $email")
                return Result.failure(IOException("Simulated network error sending code"))
            }

            // 1. Generate Random 6-Digit Code
            val code = Random.nextInt(100000, 1000000).toString() // Generates number between 100000 and 999999

            // 2. Store the code (simulating backend storage)
            simulatedCodeStorage[email] = code

            // 3. Simulate Sending Email (Print to console for testing)
            println("-----------------------------------------------------")
            println("FakeRepo: SIMULATING SENDING EMAIL")
            println("To: $email")
            println("Subject: Your Verification Code")
            println("Body: Your verification code is: $code")
            println("-----------------------------------------------------")

            // Indicate success
            return Result.success(Unit)
        }

        override suspend fun registerUser(email: String, pass: String, code: String): Result<Unit> {
            delay(2000) // Simulate network latency
            println("FakeRepo: Attempting to register $email with code $code")

            // Retrieve the expected code from our simulated storage
            val expectedCode = simulatedCodeStorage[email]

            // Simulate potential failures
            return when {
                email.contains("exists") -> {
                    println("FakeRepo: Registration failed - Email '$email' already exists.")
                    Result.failure(IllegalStateException("Email already exists")) // Use a more specific custom exception in real app
                }
                expectedCode == null -> {
                    println("FakeRepo: Registration failed - No code was generated/sent for '$email'.")
                    Result.failure(IllegalArgumentException("Verification code not requested or expired.")) // Simulate code expiration or not found
                }
                code != expectedCode -> {
                    println("FakeRepo: Registration failed - Invalid code '$code' provided for '$email'. Expected '$expectedCode'.")
                    Result.failure(IllegalArgumentException("Invalid verification code"))
                }
                else -> {
                    println("FakeRepo: Registration SUCCESS for $email with code $code.")
                    // Optional: Remove code after successful registration in a real scenario
                    // simulatedCodeStorage.remove(email)
                    Result.success(Unit)
                }
            }
        }
    } // End fake repo

    // --- Existing ViewModel Logic (No changes needed below this line) ---

    private val _uiState = MutableStateFlow<RegisterUiState>(RegisterUiState.Idle)
    val uiState = _uiState.asStateFlow()

    fun sendVerificationCode(email: String) {
        if (_uiState.value == RegisterUiState.SendingCode || _uiState.value == RegisterUiState.Registering) return // Prevent concurrent actions
        if (!isValidEmailFormat(email)) { // Optional: Add basic format check before even trying
            _uiState.value = RegisterUiState.Error("Please enter a valid email address.")
            return
        }
        viewModelScope.launch {
            _uiState.value = RegisterUiState.SendingCode
            println("ViewModel: Requesting verification code for $email")
            try {
                val result = authRepository.sendVerificationCode(email)

                result.onSuccess {
                    println("ViewModel: Code send request simulated successfully for $email")
                    // Stay in Idle or briefly go to CodeSent (as you have it)
                    _uiState.value = RegisterUiState.CodeSent
                    delay(500) // Optional delay
                    _uiState.value = RegisterUiState.Idle // Return to Idle after brief success indication
                }.onFailure { exception ->
                    val errorMessage = when (exception) {
                        is IOException -> "Network error sending code. Please check connection."
                        // Add cases for custom exceptions your repo might throw
                        else -> "Could not send verification code: ${exception.message ?: "Unknown error"}"
                    }
                    println("ViewModel: Failed to send code - ${exception.message}")
                    _uiState.value = RegisterUiState.Error(errorMessage)
                }
            } catch (e: Exception) {
                println("ViewModel: Unexpected error calling sendVerificationCode - ${e.message}")
                _uiState.value = RegisterUiState.Error("An unexpected error occurred sending code.")
            }
        }
    }

    // Helper function for basic email format check (can be enhanced)
    private fun isValidEmailFormat(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    fun registerUser(email: String, password: String, code: String) {
        if (_uiState.value == RegisterUiState.Registering || _uiState.value == RegisterUiState.SendingCode) return // Prevent concurrent actions
        viewModelScope.launch {
            _uiState.value = RegisterUiState.Registering
            println("ViewModel: Attempting registration for $email with code $code")
            try {
                // NOTE: Send the plain password here over HTTPS. Backend MUST hash it.
                val result = authRepository.registerUser(email, password, code)

                result.onSuccess {
                    println("ViewModel: Registration successful for $email")
                    _uiState.value = RegisterUiState.RegistrationSuccess
                }.onFailure { exception ->
                    val errorMessage = when (exception) {
                        is IOException -> "Network error during registration. Please try again."
                        is IllegalArgumentException -> exception.message ?: "Invalid verification code or request." // Use message from exception
                        is IllegalStateException -> "Email address is already registered." // Example mapping
                        else -> "Registration Failed: ${exception.message ?: "Unknown error"}"
                    }
                    println("ViewModel: Registration failed - ${exception.message}")
                    _uiState.value = RegisterUiState.Error(errorMessage)
                }

            } catch (e: Exception) {
                println("ViewModel: Unexpected error calling registerUser - ${e.message}")
                _uiState.value = RegisterUiState.Error("An unexpected registration error occurred.")
            }
        }
    }

    fun clearErrorState() {
        if (_uiState.value is RegisterUiState.Error) {
            _uiState.value = RegisterUiState.Idle
        }
    }

    fun resetState() {
        _uiState.value = RegisterUiState.Idle
    }
}
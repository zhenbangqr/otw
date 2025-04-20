package com.zhenbang.otw.auth

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import net.openid.appauth.*
import kotlin.coroutines.suspendCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONException

// Data class to hold the state of user authentication.
data class UserAuthState(
    val isAuthorized: Boolean = false,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val idToken: String? = null,
    val error: String? = null,
    val needsTokenExchange: Boolean = false,
    val authCode: String? = null,
    val tokenResponse: TokenResponse? = null
)

// ViewModel responsible for handling the OAuth 2.0 authentication flow.
class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val prefsFile = "auth_prefs_secure" // Use a unique name
    private val authStateKey = "authStateJson"

    private val _endSessionEvent = MutableSharedFlow<Intent>()
    val endSessionEvent = _endSessionEvent.asSharedFlow()

    // Helper property to get EncryptedSharedPreferences instance safely
    private val sharedPreferences by lazy {
        // Create the master key using the recommended builder pattern
        val masterKey = MasterKey.Builder(getApplication<Application>().applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM) // Specify the desired scheme
            .build()

        // Create EncryptedSharedPreferences using the MasterKey object
        EncryptedSharedPreferences.create(
            getApplication<Application>().applicationContext,
            prefsFile,
            masterKey, // Pass the MasterKey object directly
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }


    private val authService: AuthorizationService = AuthorizationService(application)
    private var authState: AuthState = restoreAuthState() ?: AuthState()

    var userAuthState by mutableStateOf(UserAuthState())
        private set

    // --- Initialization ---
    init {
        // When ViewModel is created, update the UI state based on restored authState
        val currentState = authState // Use the potentially restored state
        if (currentState.isAuthorized) {
            userAuthState = userAuthState.copy(
                isAuthorized = true,
                accessToken = currentState.accessToken,
                refreshToken = currentState.refreshToken,
                idToken = currentState.idToken,
                // Reset transient fields
                error = null,
                needsTokenExchange = false,
                authCode = null,
                tokenResponse = null
            )
            println("AuthViewModel: Restored authorized state.")
        } else {
            println("AuthViewModel: No authorized state restored.")
        }
    }

    // --- Configuration Section (Filled with your details) ---

    // Your Client ID from Google Cloud Console
    private val clientId = "980567999909-hpcb46hv5dfvvh3gk1g34u1s2hli1har.apps.googleusercontent.com"

    // Your redirect URI (using correct :/ format and matching scheme)
    private val redirectUri = Uri.parse("com.zhenbang.otw:/oauth2redirect")

    // Standard Google OAuth scopes
    private val scope = "openid profile email"

    // Google's OAuth 2.0 endpoints
    private val serviceConfig = AuthorizationServiceConfiguration(
        Uri.parse("https://accounts.google.com/o/oauth2/v2/auth"), // Google Auth Endpoint
        Uri.parse("https://oauth2.googleapis.com/token")          // Google Token Endpoint
    )
    // --- End Configuration Section ---

    // Builds the authorization request
    fun buildAuthorizationRequest(): AuthorizationRequest {
        val builder = AuthorizationRequest.Builder(
            serviceConfig,
            clientId,
            ResponseTypeValues.CODE,
            redirectUri
        )
        // AppAuth handles PKCE automatically (S256)
        return builder
            .setScope(scope)
            .build()
    }

    // Prepares the intent to launch the Custom Tab
    fun prepareAuthIntent(authRequest: AuthorizationRequest): Intent {
        val customTabsIntent = CustomTabsIntent.Builder().build()
        return authService.getAuthorizationRequestIntent(authRequest, customTabsIntent)
    }

    // Handles the response from the Custom Tab
    fun handleAuthorizationResponse(intent: Intent) {
        val resp = AuthorizationResponse.fromIntent(intent)
        val ex = AuthorizationException.fromIntent(intent)

        authState.update(resp, ex)

        when {
            resp != null -> {
                // Authorization successful, code received
                userAuthState = userAuthState.copy(
                    isAuthorized = false, error = null, needsTokenExchange = true, authCode = resp.authorizationCode
                )
                exchangeCodeForToken(resp) // Trigger token exchange
            }
            ex != null -> {
                // Authorization failed
                userAuthState = userAuthState.copy(
                    isAuthorized = false, error = "Authorization Failed: ${ex.errorDescription ?: ex.error ?: "Unknown error"} [Code: ${ex.code}]", needsTokenExchange = false
                )
            }
            else -> {
                // Authorization cancelled
                userAuthState = userAuthState.copy(isAuthorized = false, error = "Authorization cancelled or failed unexpectedly.", needsTokenExchange = false)
            }
        }
    }

    // Example function to perform action with fresh tokens (handles refresh)
    fun performActionWithFreshTokens(action: (accessToken: String?, idToken: String?, ex: AuthorizationException?) -> Unit) {
        val currentState = authState


        // Perform action, potentially refreshing tokens
        currentState.performActionWithFreshTokens(authService) { accessToken, idToken, ex ->
            viewModelScope.launch {
                // Get latest persisted state AFTER action to see if refresh happened
                val latestPersistedAuthState = restoreAuthState() ?: AuthState()

                if (ex != null) {
                    // Refresh failed
                    println("Token refresh failed: ${ex.message}")
                    clearAuthState() // Clear invalid persisted state
                    authState = AuthState() // Clear in-memory state
                    updateUserAuthStateFromAuthState("Token refresh failed: ${ex.message}. Please log in again.") // Update UI
                    action(null, null, ex)
                } else {
                    // Tokens are fresh (or were refreshed)
                    // Compare new token values with the state *before* the action started
                    if (currentState.accessToken != accessToken || currentState.idToken != idToken) {
                        println("Tokens were refreshed. Persisting new state.")
                        // Persist the CURRENT in-memory authState, assuming the library updated it.
                        storeAuthState(authState)
                        updateUserAuthStateFromAuthState() // Update UI state from the current authState
                    } else {
                        println("Tokens were fresh, no refresh needed or state unchanged.")
                    }
                    action(accessToken, idToken, null) // Execute action
                }
            }
        }
    }

    // Clears authentication state for logout
    fun logout() {
        val currentState = authState

        authState = AuthState()
        clearAuthState()
        updateUserAuthStateFromAuthState()

        tryEndSession(currentState)
    }

    private fun exchangeCodeForToken(authResponse: AuthorizationResponse) {
        viewModelScope.launch {
            try {
                println("exchangeCodeForToken: Creating token request...")
                val tokenRequest = authResponse.createTokenExchangeRequest()

                println("exchangeCodeForToken: Performing token request...")
                val tokenResponse = performTokenRequest(tokenRequest)
                println("exchangeCodeForToken: Token request successful.")

                println("exchangeCodeForToken: Updating authState in memory...")
                authState.update(tokenResponse, null) // Update state in memory
                println("exchangeCodeForToken: In-memory authState updated.")

                println("exchangeCodeForToken: Calling storeAuthState...")
                storeAuthState(authState) // Persist the updated state

                println("exchangeCodeForToken: Updating UI state...")
                updateUserAuthStateFromAuthState() // Update UI state
                println("Token exchange successful.") // Final success log

            } catch (e: AuthorizationException) {
                println("exchangeCodeForToken: Caught AuthorizationException - ${e.message}")
                authState.update(null as TokenResponse?, e) // Update in-memory state with error
                updateUserAuthStateFromAuthState("Token Exchange Failed: ${e.errorDescription ?: e.error ?: "Unknown error"} [Code: ${e.code}]")

            } catch (e: Exception) {
                println("exchangeCodeForToken: Caught generic Exception - ${e.message}")
                updateUserAuthStateFromAuthState("Token Exchange Failed: ${e.message}")
            }
        }
    }

    private suspend fun performTokenRequest(tokenRequest: TokenRequest): TokenResponse =
        suspendCoroutine { continuation ->
            authService.performTokenRequest(tokenRequest) { response, ex ->
                when {
                    response != null -> continuation.resume(response)
                    ex != null -> continuation.resumeWithException(ex)
                    else -> continuation.resumeWithException(IllegalStateException("No response or exception received"))
                }
            }
        }

    // Updates the observable UI state based on the current in-memory AuthState
    private fun updateUserAuthStateFromAuthState(errorMsg: String? = null) {
        val currentAuthState = authState // Use the current state
        userAuthState = UserAuthState(
            isAuthorized = currentAuthState.isAuthorized,
            accessToken = currentAuthState.accessToken,
            refreshToken = currentAuthState.refreshToken,
            idToken = currentAuthState.idToken,
            error = errorMsg // Use specific error message or null if authorized
        )
        println("Updated userAuthState: isAuthorized=${userAuthState.isAuthorized}, error='${userAuthState.error}'")
    }

    private fun tryEndSession(stateToUseForHint: AuthState) {
        val endSessionEndpoint = serviceConfig.endSessionEndpoint
        if (endSessionEndpoint != null && stateToUseForHint.idToken != null) {
            val endSessionRequest = EndSessionRequest.Builder(serviceConfig)
                .setIdTokenHint(stateToUseForHint.idToken) // Use the ID token from the state before logout
                .setPostLogoutRedirectUri(redirectUri)     // Where Google redirects after logout
                .build()

            val endSessionIntent = authService.getEndSessionRequestIntent(endSessionRequest)
            println("End session intent created, attempting to emit event.")
            viewModelScope.launch {
                _endSessionEvent.emit(endSessionIntent)
            }
        } else {
            println("End session endpoint not configured or ID token missing.")
        }
    }

    // --- Persistence Functions ---

    private fun storeAuthState(state: AuthState) {
        try {
            val jsonString = state.jsonSerializeString()
            if (jsonString != null) {
                sharedPreferences.edit()
                    .putString(authStateKey, jsonString)
                    .apply()
                println("Stored AuthState.") // Log success
            } else {
                println("Error storing AuthState: JSON serialization returned null.")
            }
        } catch (e: Exception) {
            println("Error saving auth state: ${e.message}")
        }
    }

    private fun restoreAuthState(): AuthState? {
        val jsonString = sharedPreferences.getString(authStateKey, null)
        return if (jsonString != null) {
            try {
                println("Restoring AuthState from JSON.")
                AuthState.jsonDeserialize(jsonString)
            } catch (e: JSONException) {
                println("Error deserializing auth state: ${e.message}")
                clearAuthState()
                null
            } catch (e: Exception) {
                println("Unexpected error restoring auth state: ${e.message}")
                null
            }
        } else {
            println("No saved AuthState found.")
            null
        }
    }

    private fun clearAuthState() {
        try {
            sharedPreferences.edit().remove(authStateKey).apply()
            println("Cleared persisted AuthState.")
        } catch (e: Exception) {
            println("Error clearing auth state: ${e.message}")
        }
    }

    // Dispose service on ViewModel clear
    override fun onCleared() {
        super.onCleared()
        authService.dispose()
    }
}

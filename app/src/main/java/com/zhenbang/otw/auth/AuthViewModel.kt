package com.zhenbang.otw.auth

import android.app.Application
import android.content.Intent // Keep import for handleAuthorizationResponse
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.openid.appauth.*
import org.json.JSONException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import net.openid.appauth.AuthorizationServiceConfiguration

// --- REMOVED Flow imports if not needed elsewhere ---
// import kotlinx.coroutines.flow.MutableSharedFlow
// import kotlinx.coroutines.flow.asSharedFlow


/**
 * ViewModel responsible for handling the OAuth 2.0 authentication flow using AppAuth,
 * specifically configured for Google Sign-In.
 * (Updated to remove browser end session attempt - Recommended Fix)
 */
class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "AuthViewModel"
    private val prefsFile = "auth_prefs_secure"
    private val authStateKey = "authStateJson"

    // --- REMOVED: No longer emitting end session event ---
    // private val _endSessionEvent = MutableSharedFlow<Intent>()
    // val endSessionEvent = _endSessionEvent.asSharedFlow()

    private val sharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(getApplication())
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                getApplication(),
                prefsFile,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error creating EncryptedSharedPreferences", e)
            throw RuntimeException("Failed to create secure preferences", e)
        }
    }

    private val authService: AuthorizationService = AuthorizationService(application)
    private lateinit var authState: AuthState

    private val _userAuthState = MutableStateFlow(UserAuthState())
    val userAuthState = _userAuthState.asStateFlow()

    init {
        authState = restoreAuthState() ?: AuthState()
        val currentState = authState
        if (currentState.isAuthorized && currentState.idToken != null) {
            Log.d(TAG, "Init: Restored authorized state.")
            _userAuthState.value = UserAuthState(
                isAuthorized = true,
                idToken = currentState.idToken,
                isLoading = false,
                error = null,
                userInfo = parseIdToken(currentState.idToken)
            )
        } else {
            Log.d(TAG, "Init: No authorized state restored or ID Token missing.")
            _userAuthState.value = UserAuthState(isAuthorized = false, isLoading = false)
        }
    }

    // --- Configuration Section ---
    // !!! REPLACE with your actual Client ID and Redirect URI !!!
    private val clientId = "472466047132-0sa7cqoh4gdq2eq7lhvpl1g93553ihuv.apps.googleusercontent.com"
    private val redirectUri = Uri.parse("com.zhenbang.otw:/oauth2redirect")
    private val scope = "openid profile email"

    // Google's endpoints (Removed endSessionEndpoint)
    private val serviceConfig = AuthorizationServiceConfiguration(
        Uri.parse("https://accounts.google.com/o/oauth2/v2/auth"), // authorizationEndpoint
        Uri.parse("https://oauth2.googleapis.com/token"),          // tokenEndpoint
        null, // registrationEndpoint (Optional)
        null  // *** Set endSessionEndpoint to null ***
    )

    // ... (buildAuthorizationRequest, prepareAuthIntent, startAuthorization, handleAuthorizationResponse, exchangeCodeForToken, performTokenRequest remain the same) ...

    /** Builds the AppAuth AuthorizationRequest */
    fun buildAuthorizationRequest(): AuthorizationRequest {
        val builder = AuthorizationRequest.Builder(
            serviceConfig,
            clientId,
            ResponseTypeValues.CODE,
            redirectUri
        )
        return builder
            .setScope(scope)
            .build()
    }

    /** Prepares the Intent to launch the Custom Tab for authorization */
    fun prepareAuthIntent(authRequest: AuthorizationRequest): Intent {
        val customTabsIntent = CustomTabsIntent.Builder().build()
        return authService.getAuthorizationRequestIntent(authRequest, customTabsIntent)
    }

    /** Sets the loading state before launching the authorization intent */
    fun startAuthorization() {
        _userAuthState.value = UserAuthState(isLoading = true, error = null)
        Log.d(TAG, "Starting authorization, isLoading=true")
    }

    /** Handles the redirect intent back from the Custom Tab */
    fun handleAuthorizationResponse(intent: Intent) {
        Log.d(TAG, "Handling authorization response.")
        val resp = AuthorizationResponse.fromIntent(intent)
        val ex = AuthorizationException.fromIntent(intent)

        authState.update(resp, ex)
        if (resp != null || ex != null) {
            storeAuthState(authState)
        }

        when {
            resp != null -> {
                _userAuthState.value = _userAuthState.value.copy(isLoading = true, error = null)
                Log.d(TAG, "Auth response received, proceeding to token exchange.")
                exchangeCodeForToken(resp)
            }
            ex != null -> {
                Log.e(TAG, "Auth response error: ${ex.error} - ${ex.errorDescription}", ex)
                _userAuthState.value = UserAuthState(
                    isLoading = false,
                    isAuthorized = false,
                    error = "Authorization Failed: ${ex.errorDescription ?: ex.error ?: "Unknown error"} [${ex.code}]"
                )
            }
            else -> {
                Log.w(TAG, "Auth cancelled by user or unknown issue.")
                _userAuthState.value = UserAuthState(
                    isLoading = false,
                    isAuthorized = false,
                    error = "Authorization cancelled."
                )
            }
        }
    }

    /** Exchanges the authorization code for tokens */
    private fun exchangeCodeForToken(authResponse: AuthorizationResponse) {
        viewModelScope.launch {
            if (!_userAuthState.value.isLoading) {
                _userAuthState.value = _userAuthState.value.copy(isLoading = true, error = null)
            }
            try {
                Log.d(TAG, "Performing token request...")
                val tokenRequest = authResponse.createTokenExchangeRequest()
                val tokenResponse = performTokenRequest(tokenRequest)
                Log.d(TAG, "Token request successful.")

                authState.update(tokenResponse, null)
                Log.d(TAG, "In-memory authState updated with tokens.")
                storeAuthState(authState)

                Log.d(TAG, "Updating UI state on token exchange success...")
                _userAuthState.value = UserAuthState(
                    isAuthorized = true,
                    isLoading = false,
                    idToken = authState.idToken,
                    error = null,
                    userInfo = parseIdToken(authState.idToken)
                )
                Log.d(TAG, "Token exchange successful. isAuthorized=true")

                // TODO: Optional: Trigger Firebase Linking here
                // linkFirebaseAccount(authState.idToken)

            } catch (e: AuthorizationException) {
                Log.e(TAG, "Token exchange failed: ${e.error} - ${e.errorDescription}", e)
                authState.update(null as TokenResponse?, e) // Update internal state with error
                storeAuthState(authState) // Store state even with error
                _userAuthState.value = UserAuthState(
                    isLoading = false,
                    isAuthorized = false,
                    error = "Token Exchange Failed: ${e.errorDescription ?: e.error ?: "Unknown error"} [${e.code}]"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Token exchange failed with generic exception", e)
                _userAuthState.value = UserAuthState(
                    isLoading = false,
                    isAuthorized = false,
                    error = "Token Exchange Failed: ${e.message}"
                )
            }
        }
    }

    /** Wraps the AppAuth token request callback in a suspend function */
    private suspend fun performTokenRequest(tokenRequest: TokenRequest): TokenResponse =
        suspendCoroutine { continuation ->
            Log.d(TAG,"Performing token request coroutine...")
            authService.performTokenRequest(tokenRequest /*, clientAuth */) { response, ex ->
                when {
                    response != null -> {
                        Log.d(TAG,"Token request coroutine success.")
                        continuation.resume(response)
                    }
                    ex != null -> {
                        Log.e(TAG,"Token request coroutine failed.", ex)
                        continuation.resumeWithException(ex)
                    }
                    else -> {
                        Log.e(TAG,"Token request coroutine failed: No response or exception.")
                        continuation.resumeWithException(IllegalStateException("No response or exception received"))
                    }
                }
            }
        }

    /** Performs an action ensuring tokens are fresh, refreshing if necessary */
    fun performActionWithFreshTokens(action: (accessToken: String?, idToken: String?, ex: AuthorizationException?) -> Unit) {
        Log.d(TAG, "Performing action with fresh tokens...")
        val currentAuthState = authState
        currentAuthState.performActionWithFreshTokens(authService /*, clientAuth (if needed for refresh) */) { accessToken, idToken, ex ->
            viewModelScope.launch { // Ensure UI updates happen on main thread
                if (ex != null) {
                    Log.e(TAG, "Token refresh failed during performAction", ex)
                    clearAuthState() // Clear invalid persisted state
                    authState = AuthState() // Clear in-memory state
                    _userAuthState.value = UserAuthState(isLoading = false, error = "Session expired: ${ex.message}. Please log in again.")
                    action(null, null, ex)
                } else {
                    Log.d(TAG, "performActionWithFreshTokens: Tokens are fresh.")
                    val needsPersist = authState.accessToken != accessToken || authState.idToken != idToken
                    if (needsPersist) {
                        Log.d(TAG, "performActionWithFreshTokens: Tokens were refreshed. Persisting.")
                        storeAuthState(authState) // Persist the updated AuthState
                    }
                    if (_userAuthState.value.isLoading){
                        _userAuthState.value = _userAuthState.value.copy(isLoading = false)
                    }
                    action(accessToken, idToken, null) // Execute the action with fresh tokens
                }
            }
        }
    }


    /** Initiates the logout process by clearing local state */
    fun logout() {
        Log.d(TAG, "logout called.")
        // Clear local AppAuth state immediately
        authState = AuthState()
        clearAuthState()
        _userAuthState.value = UserAuthState(isLoading = false) // Reset UI state
        Log.d(TAG, "Local AppAuth state cleared for logout.")
        // --- REMOVED: No browser end session attempt ---
        Log.d(TAG, "Skipping browser end session attempt as Google's endpoint is non-standard.")
        // The onLogout lambda in AppNavigation handles Firebase sign-out.
    }

    // --- REMOVED or COMMENTED OUT: No longer attempting OIDC End Session ---
    /*
    private fun tryEndSession(stateToUseForHint: AuthState) {
        // ... (Original function body - no longer called) ...
        Log.w(TAG, "tryEndSession is not used as Google's end session endpoint is non-standard.")
    }
    */

    // --- Persistence Functions (Unchanged) ---
    private fun storeAuthState(state: AuthState) {
        viewModelScope.launch {
            try {
                val jsonString = state.jsonSerializeString()
                if (jsonString != null) {
                    sharedPreferences.edit()
                        .putString(authStateKey, jsonString)
                        .apply()
                    Log.d(TAG, "Stored AuthState successfully.")
                } else {
                    Log.w(TAG, "Error storing AuthState: JSON serialization returned null.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving auth state", e)
            }
        }
    }

    private fun restoreAuthState(): AuthState? {
        return try {
            val jsonString = sharedPreferences.getString(authStateKey, null)
            if (jsonString != null) {
                Log.d(TAG, "Restoring AuthState from JSON.")
                AuthState.jsonDeserialize(jsonString)
            } else {
                Log.d(TAG, "No saved AuthState found.")
                null
            }
        } catch (e: JSONException) {
            Log.e(TAG, "Error deserializing auth state", e)
            clearAuthState()
            null
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error restoring auth state", e)
            null
        }
    }

    private fun clearAuthState() {
        Log.d(TAG, "Clearing persisted AuthState.")
        try {
            sharedPreferences.edit().remove(authStateKey).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing auth state", e)
        }
    }

    // --- User Info Parsing (Unchanged) ---
    // WARNING: Basic parsing only. Use a JWT library for production.
    private fun parseIdToken(idToken: String?): GoogleUserInfo? {
        if (idToken == null) return null
        return try {
            val parts = idToken.split(".")
            if (parts.size < 2) return null
            val payload = String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE), Charsets.UTF_8)
            val email = payload.substringAfter("\"email\":\"","").substringBefore("\"","")
            val name = payload.substringAfter("\"name\":\"","").substringBefore("\"","")
            val picture = payload.substringAfter("\"picture\":\"","").substringBefore("\"","")
            Log.d(TAG, "Parsed ID Token (basic): email=$email, name=$name")
            GoogleUserInfo(
                email = if (email.isNotEmpty()) email else null,
                displayName = if (name.isNotEmpty()) name else null,
                pictureUrl = if (picture.isNotEmpty()) picture else null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing ID token (basic parsing)", e)
            null
        }
    }


    // Dispose service on ViewModel clear
    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "AuthViewModel cleared, disposing authService.")
        authService.dispose()
    }
}

// Ensure UserAuthState.kt and GoogleUserInfo are defined correctly
/* Example:
package com.zhenbang.otw.auth

data class GoogleUserInfo(
    val email: String?,
    val displayName: String?,
    val pictureUrl: String?
)

data class UserAuthState(
    val isAuthorized: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val userInfo: GoogleUserInfo? = null,
    val idToken: String? = null // Keep ID token if needed elsewhere
)
*/

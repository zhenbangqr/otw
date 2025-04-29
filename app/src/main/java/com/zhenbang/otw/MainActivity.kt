package com.zhenbang.otw

import android.content.Intent // Import Intent
import android.os.Bundle
import android.util.Log // *** ADD THIS IMPORT ***
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels // Import for by viewModels()
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.zhenbang.otw.auth.AuthViewModel // Import your AppAuth ViewModel
import com.zhenbang.otw.departments.DepartmentNavigation
import com.zhenbang.otw.ui.theme.OnTheWayTheme
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
// Make sure your LoginScreen composable is imported if used in Preview
// import com.zhenbang.otw.login.LoginScreen

class MainActivity : ComponentActivity() {

    // Get an instance of the AuthViewModel scoped to this Activity
    // Requires dependency: implementation("androidx.activity:activity-ktx:LATEST_VERSION")
    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check the intent that started the activity ONCE on creation
        // Use the current intent associated with the activity instance
        handleRedirectIntent(intent) // Pass the activity's current intent

        setContent {
            OnTheWayTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    //DepartmentNavigation()
                    AppNavigation()
                }
            }
        }
    }

    // Handle intents delivered if the Activity is already running (e.g., launchMode="singleTask")
    // *** ENSURE SIGNATURE MATCHES FRAMEWORK ***
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Update the activity's intent to the new one
        // No null check needed as 'intent' is now non-nullable
        setIntent(intent)
        // Handle the redirect intent
        // No null check needed as 'intent' is now non-nullable
        handleRedirectIntent(intent)
    }

    /**
     * Checks if the intent contains an AppAuth authorization response
     * and passes it to the AuthViewModel.
     */
    private fun handleRedirectIntent(intent: Intent?) { // Accept nullable Intent
        // Need to check nullability of intent itself first
        if (intent == null) {
            Log.d("MainActivity", "handleRedirectIntent called with null intent.")
            return
        }

        // Check if this intent is the result of an authorization flow
        // Check intent data and action
        if (intent.data != null && intent.action == Intent.ACTION_VIEW) {
            // Check specifically for parameters used by AppAuth response URIs
            val resp = AuthorizationResponse.fromIntent(intent)
            val ex = AuthorizationException.fromIntent(intent)

            if (resp != null || ex != null) {
                Log.d("MainActivity", "Received AppAuth redirect intent, handling response.")
                // Pass the non-null intent to the ViewModel
                authViewModel.handleAuthorizationResponse(intent)

                // *** REMOVED intent.data = null ***
                // To prevent reprocessing, rely on singleTask launchMode and handling only once here
                // or implement a flag in ViewModel/Activity if necessary.
                // Setting intent's data to null here is not the standard way.

            } else {
                Log.d("MainActivity", "Intent received but not an AppAuth response.")
                // Intent not for AppAuth, ignore or handle differently if needed
            }
        } else {
            Log.d("MainActivity", "Intent action is not VIEW or data is null.")
        }
    }
}


@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    OnTheWayTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            // Preview needs adjustments as LoginScreen now expects more parameters
            // Provide dummy callbacks for preview
            // Make sure LoginScreen is imported if you uncomment this
            /*
            LoginScreen(
                onNavigateToRegister = {},
                onLoginSuccess = {},
                onNavigateToVerify = {}
            )
            */
            // Or just show a simple placeholder for preview
            androidx.compose.material3.Text("Preview Requires LoginScreen Setup")
        }
    }
}
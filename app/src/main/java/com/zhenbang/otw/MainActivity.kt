package com.zhenbang.otw

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.zhenbang.otw.auth.AuthViewModel
import com.zhenbang.otw.ui.theme.OnTheWayTheme
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse

class MainActivity : ComponentActivity() {
    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleRedirectIntent(intent)
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


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleRedirectIntent(intent)
    }

    /**
     * Checks if the intent contains an AppAuth authorization response
     * and passes it to the AuthViewModel.
     */
    private fun handleRedirectIntent(intent: Intent?) {
        if (intent == null) {
            Log.d("MainActivity", "handleRedirectIntent called with null intent.")
            return
        }
        if (intent.data != null && intent.action == Intent.ACTION_VIEW) {
            val resp = AuthorizationResponse.fromIntent(intent)
            val ex = AuthorizationException.fromIntent(intent)

            if (resp != null || ex != null) {
                Log.d("MainActivity", "Received AppAuth redirect intent, handling response.")
                authViewModel.handleAuthorizationResponse(intent)
            } else {
                Log.d("MainActivity", "Intent received but not an AppAuth response.")
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
            androidx.compose.material3.Text("Preview Requires LoginScreen Setup")
        }
    }
}
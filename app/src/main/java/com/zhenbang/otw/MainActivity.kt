package com.zhenbang.otw

// --- All existing imports ---
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.zhenbang.otw.ui.theme.OnTheWayTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging // Import FirebaseMessaging

// --- Assume LiveLocationScreen and MessagingScreen composables are defined/imported ---

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OnTheWayTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val auth: FirebaseAuth = remember { FirebaseAuth.getInstance() }
                    var isLoading by remember { mutableStateOf(true) }
                    var authError by remember { mutableStateOf<String?>(null) }
                    var firebaseUser by remember { mutableStateOf(auth.currentUser) }
                    var isNameSet by remember { mutableStateOf(firebaseUser?.displayName?.isNotEmpty() == true) }
                    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current

                    // --- Auth State Listener Effect (runs once) ---
                    LaunchedEffect(key1 = lifecycleOwner) { // Use lifecycleOwner or Unit as key
                        val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                            // ... (Listener logic as before) ...
                            val previousUser = firebaseUser
                            val newUser = firebaseAuth.currentUser
                            firebaseUser = newUser
                            Log.d("AuthCheck", "Auth state changed. User: ${newUser?.uid}, Name: ${newUser?.displayName}")
                            isNameSet = newUser?.displayName?.isNotEmpty() == true
                            if (previousUser != null && newUser == null) { Log.w("AuthCheck", "User logged out."); isLoading = false; authError = null }
                            // Ensure loading stops once listener confirms a user state (either null or logged in)
                            if (isLoading && (newUser != null || previousUser != null)) {
                                isLoading = false
                                Log.d("AuthCheck","Listener confirmed user state change, setting isLoading = false")
                            }
                        }
                        auth.addAuthStateListener(authStateListener)

                        // Initial check / Anonymous sign-in (only if needed)
                        if (auth.currentUser == null) { // Check auth directly here too
                            Log.d("AuthCheck", "No user on start. Attempting anonymous sign-in...")
                            isLoading = true
                            try {
                                val authResult = auth.signInAnonymously().await()
                                Log.i("AuthCheck", "Anonymous sign-in successful trigger. User ID: ${authResult.user?.uid}")
                                // Listener will update firebaseUser state and isLoading
                            } catch (e: Exception) {
                                Log.e("AuthCheck", "Anonymous sign-in failed", e)
                                authError = "Failed to start session: ${e.message ?: "Unknown error"}"
                                isLoading = false // Stop loading on error
                            }
                        } else { // User already logged in
                            Log.d("AuthCheck", "User ${auth.currentUser?.uid} already logged in. Name: ${auth.currentUser?.displayName}")
                            // State already set via remember initializers
                            isLoading = false
                        }

                        // Listener cleanup
                        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.DESTROYED) {
                            Log.d("AuthCheck", "Removing auth state listener.")
                            auth.removeAuthStateListener(authStateListener)
                        }
                    } // End of Auth LaunchedEffect

                    // --- NEW: Effect for Fetching/Saving FCM Token (runs when user logs in) ---
                    LaunchedEffect(firebaseUser) { // Keyed to firebaseUser
                        if (firebaseUser != null) { // Only run if user is logged in
                            try {
                                val token = FirebaseMessaging.getInstance().token.await()
                                Log.d("FCM Token", "Obtained token for user ${firebaseUser?.uid}: $token")
                                // Save token to Firestore using the helper function
                                saveTokenToFirestore(firebaseUser?.uid, token)
                            } catch (e: Exception) {
                                Log.e("FCM Token", "Fetching/Saving FCM token failed for user ${firebaseUser?.uid}", e)
                            }
                        }
                    } // End of FCM Token LaunchedEffect


                    // --- Conditional UI Display Logic ---
                    when {
                        isLoading -> { // Loading state
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) { CircularProgressIndicator() }
                        }
                        authError != null -> { // Error state
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(16.dp)) { Text("Error: $authError") }
                        }
                        firebaseUser != null -> {
                            if (!isNameSet) { // Name not set, show EnterNameScreen
                                EnterNameScreen(firebaseUser = firebaseUser!!, onNameSaved = {})
                            } else { // User ready, Name is Set -> SETUP NAVIGATION
                                // NavHost manages the screens
                                val navController = rememberNavController()
                                NavHost(navController = navController, startDestination = Routes.LIVE_LOCATION) {
                                    composable(Routes.LIVE_LOCATION) {
                                        LiveLocationScreen(navController = navController)
                                    }
                                    composable(
                                        route = Routes.MESSAGING,
                                        arguments = listOf(navArgument("userId") { type = NavType.StringType })
                                    ) { backStackEntry ->
                                        val userId = backStackEntry.arguments?.getString("userId")
                                        if (userId != null) {
                                            MessagingScreen(navController = navController, userIdToChatWith = userId)
                                        } else { Text("Error: User ID not found for chat.") }
                                    }
                                }
                            }
                        }
                        else -> { // Fallback (e.g., after logout)
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) { Text("Not logged in.") }
                        }
                    } // --- End Conditional UI Display ---
                } // End Surface
            } // End Theme
        } // End setContent
    } // End onCreate
} // End Activity

// --- Helper function MOVED outside MainActivity class or setContent ---
suspend fun saveTokenToFirestore(userId: String?, token: String?) {
    if (userId != null && token != null) {
        try {
            val userDocRef = Firebase.firestore.collection("users").document(userId)
            userDocRef.set(mapOf("fcmToken" to token), SetOptions.merge()).await()
            Log.d("FCM Token", "Token saved/updated in Firestore for user $userId")
        } catch (e: Exception) {
            Log.e("FCM Token", "Error saving token to Firestore for user $userId", e)
        }
    } else {
        Log.w("FCM Token", "Cannot save token - userId or token is null.")
    }
}

// --- EnterNameScreen composable (remains the same) ---
@Composable
fun EnterNameScreen(firebaseUser: FirebaseUser, onNameSaved: () -> Unit) {
    // ... (Implementation remains the same) ...
    var name by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val db = Firebase.firestore

    Box( modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center ) {
        Column( horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center ) {
            Text("Welcome!", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Please enter your name to continue:")
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField( value = name, onValueChange = { name = it; if (saveError != null) saveError = null }, label = { Text("Your Name") }, singleLine = true, isError = saveError != null, modifier = Modifier.fillMaxWidth(0.8f) )
            Spacer(modifier = Modifier.height(8.dp))
            if (saveError != null) { Text( text = "Error: $saveError", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall ); Spacer(modifier = Modifier.height(8.dp)) }
            Button( onClick = {
                val trimmedName = name.trim()
                if (trimmedName.isBlank()) { saveError = "Name cannot be empty."; return@Button }
                isSaving = true; saveError = null
                scope.launch {
                    try {
                        val profileUpdates = UserProfileChangeRequest.Builder().setDisplayName(trimmedName).build()
                        firebaseUser.updateProfile(profileUpdates).await()
                        Log.i("NameUpdate", "Auth profile updated for ${firebaseUser.uid}")
                        // Make sure the user document exists before trying to merge token later
                        val userProfileData = hashMapOf("uid" to firebaseUser.uid, "name" to trimmedName)
                        db.collection("users").document(firebaseUser.uid).set(userProfileData, SetOptions.merge()).await() // Use merge here too
                        Log.i("NameUpdate", "Firestore profile saved for ${firebaseUser.uid}")
                        isSaving = false
                    } catch (e: Exception) { Log.e("NameUpdate", "Failed update for ${firebaseUser.uid}", e); saveError = e.localizedMessage ?: "Failed to save name."; isSaving = false }
                }
            }, enabled = !isSaving ) {
                if (isSaving) { CircularProgressIndicator( modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = LocalContentColor.current ); Spacer(modifier = Modifier.width(8.dp)); Text("Saving...") } else { Text("Continue") }
            }
        }
    }
}


// --- Ensure LiveLocationScreen and MessagingScreen composables are defined/imported ---
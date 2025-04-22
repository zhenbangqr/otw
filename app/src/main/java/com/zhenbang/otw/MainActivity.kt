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
import com.zhenbang.otw.ui.theme.OnTheWayTheme // Your app theme
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
// --- Add Navigation Imports ---
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument

// --- Assume LiveLocationScreen and MessagingScreen composables are defined/imported ---
// Definition for Routes

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

                    LaunchedEffect(key1 = Unit) {
                        // Auth state listener logic
                        val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                            val previousUser = firebaseUser
                            val newUser = firebaseAuth.currentUser
                            firebaseUser = newUser
                            Log.d("AuthCheck", "Auth state changed. Current user: ${newUser?.uid}, Name: ${newUser?.displayName}")
                            isNameSet = newUser?.displayName?.isNotEmpty() == true
                            if (previousUser != null && newUser == null) { Log.w("AuthCheck", "User logged out."); isLoading = false; authError = null }
                            if (newUser != null && isLoading) { isLoading = false; Log.d("AuthCheck","Listener confirmed user, setting isLoading = false") }
                        }
                        auth.addAuthStateListener(authStateListener)
                        // Initial check / Anonymous sign-in
                        if (firebaseUser == null) {
                            Log.d("AuthCheck", "No user logged in on start. Attempting anonymous sign-in...")
                            isLoading = true
                            try {
                                val authResult = auth.signInAnonymously().await()
                                Log.i("AuthCheck", "Anonymous sign-in successful trigger. User ID: ${authResult.user?.uid}")
                            } catch (e: Exception) {
                                Log.e("AuthCheck", "Anonymous sign-in failed", e)
                                authError = "Failed to start session: ${e.message ?: "Unknown error"}"
                                isLoading = false
                            }
                        } else {
                            Log.d("AuthCheck", "User ${firebaseUser?.uid} already logged in. Name: ${firebaseUser?.displayName}")
                            isNameSet = firebaseUser?.displayName?.isNotEmpty() == true
                            isLoading = false
                        }
                        // Listener cleanup
                        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.DESTROYED) { Log.d("AuthCheck", "Removing auth state listener."); auth.removeAuthStateListener(authStateListener) }
                    } // End of LaunchedEffect

                    // Conditional UI Display Logic
                    when {
                        isLoading -> { // Loading state
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) { CircularProgressIndicator() }
                        }
                        authError != null -> { // Error state
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(16.dp)) { Text("Error: $authError") }
                        }
                        firebaseUser != null -> {
                            if (!isNameSet) { // Name not set, show EnterNameScreen
                                Log.d("UI Display", "User ${firebaseUser?.uid} needs name. Showing EnterNameScreen.")
                                EnterNameScreen(
                                    firebaseUser = firebaseUser!!,
                                    onNameSaved = {} // Listener handles the switch
                                )
                            } else { // User ready, Name is Set -> SETUP NAVIGATION
                                Log.d("UI Display", "User ${firebaseUser?.uid} '${firebaseUser?.displayName}' ready. Setting up NavHost.")

                                // NavHost manages the screens
                                val navController = rememberNavController()
                                NavHost(navController = navController, startDestination = Routes.LIVE_LOCATION) {

                                    // Destination 1: Live Location Screen
                                    composable(Routes.LIVE_LOCATION) {
                                        LiveLocationScreen(navController = navController) // Pass NavController
                                    }

                                    // Destination 2: Messaging Screen
                                    composable(
                                        route = Routes.MESSAGING, // "messaging/{userId}"
                                        arguments = listOf(navArgument("userId") { type = NavType.StringType })
                                    ) { backStackEntry ->
                                        val userId = backStackEntry.arguments?.getString("userId")
                                        if (userId != null) {
                                            MessagingScreen(navController = navController, userIdToChatWith = userId)
                                        } else {
                                            Text("Error: User ID not found for chat.") // Handle error
                                        }
                                    }
                                }
                            }
                        }
                        else -> { // Fallback (e.g., after logout)
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) { Text("Not logged in.") }
                        }
                    } // End Conditional UI Display
                }
            }
        }
    }
}

// EnterNameScreen composable (remains the same as you provided)
@Composable
fun EnterNameScreen(firebaseUser: FirebaseUser, onNameSaved: () -> Unit) {
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
                        val userProfileData = hashMapOf("uid" to firebaseUser.uid, "name" to trimmedName)
                        db.collection("users").document(firebaseUser.uid).set(userProfileData).await()
                        Log.i("NameUpdate", "Firestore profile saved for ${firebaseUser.uid}")
                        isSaving = false
                        // onNameSaved() // Callback not strictly needed as listener handles UI switch
                    } catch (e: Exception) { Log.e("NameUpdate", "Failed update for ${firebaseUser.uid}", e); saveError = e.localizedMessage ?: "Failed to save name."; isSaving = false }
                }
            }, enabled = !isSaving ) {
                if (isSaving) { CircularProgressIndicator( modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = LocalContentColor.current ); Spacer(modifier = Modifier.width(8.dp)); Text("Saving...") } else { Text("Continue") }
            }
        }
    }
}
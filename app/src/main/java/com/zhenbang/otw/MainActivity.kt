package com.zhenbang.otw

// --- All existing imports ---
import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
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
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging // Import FirebaseMessaging
import com.zhenbang.otw.ui.theme.OnTheWayTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await


// --- Assume LiveLocationScreen, MessagingScreen, and UserListScreen composables are defined/imported ---
// --- Assume Routes object is defined/imported ---

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

                    // --- Auth State Listener Effect --- (Unchanged)
                    LaunchedEffect(key1 = lifecycleOwner) {
                        val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                            val previousUser = firebaseUser; val newUser = firebaseAuth.currentUser
                            firebaseUser = newUser; Log.d("AuthCheck", "Auth state changed. User: ${newUser?.uid}, Name: ${newUser?.displayName}")
                            isNameSet = newUser?.displayName?.isNotEmpty() == true
                            if (previousUser != null && newUser == null) { Log.w("AuthCheck", "User logged out."); isLoading = false; authError = null }
                            if (isLoading && (newUser != null || previousUser != null)) { isLoading = false; Log.d("AuthCheck","Listener confirmed state, isLoading = false") }
                        }
                        auth.addAuthStateListener(authStateListener)
                        if (auth.currentUser == null) {
                            Log.d("AuthCheck", "No user on start. Attempting anonymous sign-in..."); isLoading = true
                            try { val authResult = auth.signInAnonymously().await(); Log.i("AuthCheck", "Anon sign-in success: ${authResult.user?.uid}") }
                            catch (e: Exception) { Log.e("AuthCheck", "Anon sign-in failed", e); authError = "Failed to start session: ${e.message ?: "Unknown error"}"; isLoading = false }
                        } else { Log.d("AuthCheck", "User ${auth.currentUser?.uid} already logged in."); isLoading = false }
                        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.DESTROYED) { Log.d("AuthCheck", "Removing auth state listener."); auth.removeAuthStateListener(authStateListener) }
                    }

                    // --- FCM Token Effect --- (Unchanged)
                    LaunchedEffect(firebaseUser) {
                        if (firebaseUser != null) {
                            try { val token = FirebaseMessaging.getInstance().token.await(); Log.d("FCM Token", "Token for user ${firebaseUser?.uid}: $token"); saveTokenToFirestore(firebaseUser?.uid, token) }
                            catch (e: Exception) { Log.e("FCM Token", "Fetching/Saving FCM token failed for user ${firebaseUser?.uid}", e) }
                        }
                    }


                    // --- Conditional UI Display Logic ---
                    when {
                        isLoading -> { Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) { CircularProgressIndicator() } }
                        authError != null -> { Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(16.dp)) { Text("Error: $authError") } }
                        firebaseUser != null -> {
                            if (!isNameSet) {
                                EnterNameScreen(firebaseUser = firebaseUser!!, onNameSaved = { /* State should update via listener */ })
                            } else {
                                // --- Navigation Setup ---
                                val navController = rememberNavController()
                                NavHost(
                                    navController = navController,
                                    // **MODIFIED:** Start destination is now the User List
                                    startDestination = Routes.CHAT_HISTORY
                                ) {
                                    composable(Routes.CHAT_HISTORY) {
                                        ChatHistoryScreen(navController = navController)
                                    }
                                    // **NEW:** Add composable route for UserListScreen
                                    composable(Routes.USER_LIST) {
                                        UserListScreen(navController = navController)
                                    }
                                    // Existing route for LiveLocationScreen
                                    composable(Routes.LIVE_LOCATION) {
                                        LiveLocationScreen(navController = navController)
                                    }
                                    // Existing route for MessagingScreen
                                    composable(
                                        route = Routes.MESSAGING, // Use constant from Routes
                                        arguments = listOf(navArgument("userId") { type = NavType.StringType })
                                    ) { backStackEntry ->
                                        val userId = backStackEntry.arguments?.getString("userId")
                                        if (userId != null) {
                                            MessagingScreen(navController = navController, userIdToChatWith = userId)
                                        } else {
                                            // Handle error: User ID missing from arguments
                                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Text("Error: User ID missing for chat.")
                                            }
                                            Log.e("Navigation", "User ID argument missing for messaging route")
                                        }
                                    }
                                    // Add other destinations here if needed
                                } // --- End NavHost ---
                            }
                        }
                        else -> { Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) { Text("Not logged in.") } }
                    } // --- End Conditional UI Display ---
                } // End Surface
            } // End Theme
        } // End setContent
    } // End onCreate
} // End Activity

// --- Helper function saveTokenToFirestore --- (Unchanged)
suspend fun saveTokenToFirestore(userId: String?, token: String?) { /* ... */ if (userId != null && token != null) { try { val userDocRef = Firebase.firestore.collection("users").document(userId); userDocRef.set(mapOf("fcmToken" to token), SetOptions.merge()).await(); Log.d("FCM Token", "Token saved/updated for user $userId") } catch (e: Exception) { Log.e("FCM Token", "Error saving token for user $userId", e) } } else { Log.w("FCM Token", "Cannot save token - userId or token is null.") } }

// --- EnterNameScreen composable --- (Unchanged)
@Composable
fun EnterNameScreen(firebaseUser: FirebaseUser, onNameSaved: () -> Unit) { /* ... implementation unchanged ... */ var name by remember { mutableStateOf("") }; var isSaving by remember { mutableStateOf(false) }; var saveError by remember { mutableStateOf<String?>(null) }; val scope = rememberCoroutineScope(); val db = Firebase.firestore; Box( modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center ) { Column( horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center ) { Text("Welcome!", style = MaterialTheme.typography.headlineMedium); Spacer(modifier = Modifier.height(8.dp)); Text("Please enter your name to continue:"); Spacer(modifier = Modifier.height(16.dp)); OutlinedTextField( value = name, onValueChange = { name = it; if (saveError != null) saveError = null }, label = { Text("Your Name") }, singleLine = true, isError = saveError != null, modifier = Modifier.fillMaxWidth(0.8f) ); Spacer(modifier = Modifier.height(8.dp)); if (saveError != null) { Text( text = "Error: $saveError", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall ); Spacer(modifier = Modifier.height(8.dp)) }; Button( onClick = { val trimmedName = name.trim(); if (trimmedName.isBlank()) { saveError = "Name cannot be empty."; return@Button }; isSaving = true; saveError = null; scope.launch { try { val profileUpdates = UserProfileChangeRequest.Builder().setDisplayName(trimmedName).build(); firebaseUser.updateProfile(profileUpdates).await(); Log.i("NameUpdate", "Auth profile updated for ${firebaseUser.uid}"); val userProfileData = hashMapOf("uid" to firebaseUser.uid, "name" to trimmedName); db.collection("users").document(firebaseUser.uid).set(userProfileData, SetOptions.merge()).await(); Log.i("NameUpdate", "Firestore profile saved for ${firebaseUser.uid}"); isSaving = false; /* onNameSaved() - Listener handles state change */ } catch (e: Exception) { Log.e("NameUpdate", "Failed update for ${firebaseUser.uid}", e); saveError = e.localizedMessage ?: "Failed to save name."; isSaving = false } } }, enabled = !isSaving ) { if (isSaving) { CircularProgressIndicator( modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = LocalContentColor.current ); Spacer(modifier = Modifier.width(8.dp)); Text("Saving...") } else { Text("Continue") } } } } }

// --- Ensure LiveLocationScreen, MessagingScreen, UserListScreen composables are defined/imported ---
// --- Ensure Routes object is defined/imported ---
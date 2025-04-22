package com.zhenbang.otw

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
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.ktx.firestore // Import Firestore
import com.google.firebase.ktx.Firebase             // Import Firebase
import com.zhenbang.otw.ui.theme.OnTheWayTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.zhenbang.otw.LiveLocationScreen // Ensure this imports your MAP screen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OnTheWayTheme { // Apply your app's theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // --- Firebase Auth Setup and State ---
                    val auth: FirebaseAuth = remember { FirebaseAuth.getInstance() }
                    var isLoading by remember { mutableStateOf(true) } // Tracks initial loading/auth check
                    var authError by remember { mutableStateOf<String?>(null) } // Stores any auth errors
                    var firebaseUser by remember { mutableStateOf(auth.currentUser) } // Holds the current Firebase user
                    // Tracks if the current user has a display name set (via Auth profile)
                    var isNameSet by remember { mutableStateOf(firebaseUser?.displayName?.isNotEmpty() == true) }

                    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current

                    // --- LaunchedEffect for Auth Handling (Runs once on composition) ---
                    LaunchedEffect(key1 = Unit) {
                        // Listener for Authentication State Changes
                        val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                            val previousUser = firebaseUser
                            val newUser = firebaseAuth.currentUser
                            firebaseUser = newUser // Update user state first
                            Log.d("AuthCheck", "Auth state changed. Current user: ${newUser?.uid}, Name: ${newUser?.displayName}")

                            // Update isNameSet based on the *new* user state's Auth profile
                            isNameSet = newUser?.displayName?.isNotEmpty() == true

                            // Handle logout scenario
                            if (previousUser != null && newUser == null) {
                                Log.w("AuthCheck", "User logged out.")
                                isLoading = false // Stop loading if user logs out
                                authError = null // Clear any previous errors
                            }

                            // If the user just got updated (e.g., after sign-in or name update),
                            // ensure loading state is correct.
                            if (newUser != null && isLoading) {
                                isLoading = false // Stop loading once we have a user confirmed by the listener
                                Log.d("AuthCheck","Listener confirmed user, setting isLoading = false")
                            }
                        }
                        auth.addAuthStateListener(authStateListener)

                        // Initial Check and Anonymous Sign-in Attempt
                        if (firebaseUser == null) {
                            Log.d("AuthCheck", "No user logged in on start. Attempting anonymous sign-in...")
                            isLoading = true // Ensure loading is true while attempting sign in
                            try {
                                val authResult = auth.signInAnonymously().await()
                                Log.i("AuthCheck", "Anonymous sign-in successful trigger. User ID: ${authResult.user?.uid}")
                                // Listener will handle setting isLoading = false
                            } catch (e: FirebaseAuthException) {
                                Log.e("AuthCheck", "Anonymous sign-in failed", e)
                                authError = "Failed to start session: ${e.message}"
                                isLoading = false // Stop loading on error
                            } catch (e: Exception) {
                                Log.e("AuthCheck", "Anonymous sign-in failed with general exception", e)
                                authError = "An unexpected error occurred during startup."
                                isLoading = false // Stop loading on error
                            }
                        } else {
                            // User was already logged in
                            Log.d("AuthCheck", "User ${firebaseUser?.uid} already logged in on start. Name: ${firebaseUser?.displayName}")
                            isNameSet = firebaseUser?.displayName?.isNotEmpty() == true
                            isLoading = false
                        }

                        // Cleanup: Remove listener when the composable's lifecycle is destroyed
                        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.DESTROYED) {
                            Log.d("AuthCheck", "Lifecycle destroyed. Removing auth state listener.")
                            auth.removeAuthStateListener(authStateListener)
                        }
                    } // End of LaunchedEffect

                    // --- Conditional UI Display Logic ---
                    when {
                        isLoading -> {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                CircularProgressIndicator()
                                Log.d("UI Display", "Showing loading indicator...")
                            }
                        }
                        authError != null -> {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(16.dp)) {
                                Text("Error: $authError")
                                Log.e("UI Display", "Showing auth error: $authError")
                            }
                        }
                        firebaseUser != null -> {
                            // Show EnterNameScreen if Auth profile display name is not set
                            if (!isNameSet) {
                                Log.d("UI Display", "User ${firebaseUser?.uid} needs name. Showing EnterNameScreen.")
                                EnterNameScreen(
                                    firebaseUser = firebaseUser!!,
                                    // onNameSaved callback is not strictly needed as listener handles the switch
                                    onNameSaved = {} // Provide an empty lambda if required
                                )
                            }
                            // Otherwise, show the main map screen
                            else {
                                Log.d("UI Display", "User ${firebaseUser?.uid} '${firebaseUser?.displayName}' ready. Showing LiveLocationScreen.")
                                // This calls the imported LiveLocationScreen with the map
                                LiveLocationScreen()
                            }
                        }
                        // Fallback if user becomes null after initial checks (e.g., explicit sign out)
                        else -> {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text("Not logged in.")
                                Log.w("UI Display", "Reached null user state after loading/error check.")
                            }
                        }
                    }
                    // --- End Conditional UI Display ---
                }
            }
        }
    }
}

// --- Composable function for the Name Entry Screen ---
@Composable
fun EnterNameScreen(
    firebaseUser: FirebaseUser, // Pass the logged-in user object
    onNameSaved: () -> Unit     // Callback function (can be empty if listener handles transition)
) {
    var name by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) } // State to show loading indicator on button
    var saveError by remember { mutableStateOf<String?>(null) } // State to show saving errors
    val scope = rememberCoroutineScope() // Coroutine scope for the async operations
    val db = Firebase.firestore // Get Firestore instance

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Welcome!", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Please enter your name to continue:")
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    if (saveError != null) saveError = null
                },
                label = { Text("Your Name") },
                singleLine = true,
                isError = saveError != null,
                modifier = Modifier.fillMaxWidth(0.8f)
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (saveError != null) {
                Text(
                    text = "Error: $saveError",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Button(
                onClick = {
                    val trimmedName = name.trim()
                    if (trimmedName.isBlank()) {
                        saveError = "Name cannot be empty."
                        return@Button
                    }

                    isSaving = true
                    saveError = null

                    scope.launch {
                        try {
                            // 1. Update Firebase Auth Profile
                            val profileUpdates = UserProfileChangeRequest.Builder()
                                .setDisplayName(trimmedName)
                                .build()
                            firebaseUser.updateProfile(profileUpdates).await()
                            Log.i("NameUpdate", "Firebase Auth profile updated successfully for ${firebaseUser.uid} with name $trimmedName")

                            // 2. Save user info to Firestore
                            val userProfileData = hashMapOf(
                                "uid" to firebaseUser.uid,
                                "name" to trimmedName
                                // Add other fields if needed, e.g., timestamp
                                // "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                            )
                            db.collection("users").document(firebaseUser.uid)
                                .set(userProfileData) // Use .set(..., SetOptions.merge()) to avoid overwriting other fields if needed
                                .await() // Wait for Firestore write
                            Log.i("NameUpdate", "User profile saved to Firestore successfully for ${firebaseUser.uid}")


                            // Reset button state - AuthStateListener handles screen switch
                            isSaving = false

                            // onNameSaved() // Call callback if needed for other logic

                        } catch (e: Exception) {
                            Log.e("NameUpdate", "Failed to update profile for ${firebaseUser.uid}", e)
                            saveError = e.localizedMessage ?: "Failed to save name."
                            isSaving = false // Also reset on error
                        }
                    }
                },
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = LocalContentColor.current
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Saving...")
                } else {
                    Text("Continue")
                }
            }
        }
    }
}
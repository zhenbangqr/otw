package com.zhenbang.otw

import ProfileScreen
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle // Use this for StateFlow
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.google.firebase.auth.FirebaseAuth
import com.zhenbang.otw.auth.AuthViewModel
import com.zhenbang.otw.auth.LoginViewModel
import com.zhenbang.otw.auth.RegisterViewModel
import com.zhenbang.otw.auth.VerificationViewModel
import kotlinx.coroutines.flow.collectLatest

object AppDestinations {
    const val LOGIN_ROUTE = "login"
    const val REGISTER_ROUTE = "register"
    const val VERIFICATION_EMAIL_ARG = "email"
    const val VERIFICATION_ROUTE = "verification/{$VERIFICATION_EMAIL_ARG}"
    const val MAIN_APP_ROUTE = "main_app"
}

@Composable
fun AppNavigation() {
    val navController: NavHostController = rememberNavController()
    val firebaseAuth = FirebaseAuth.getInstance()

    // --- Get ViewModels ---
    val authViewModel: AuthViewModel = viewModel() // For Google/AppAuth state

    // --- Observe Auth States ---
    // 1. Firebase Auth State
    var firebaseUserLoggedIn by remember { mutableStateOf<Boolean?>(null) } // null = loading/unknown
    // 2. Google/AppAuth State
    val googleAuthState by authViewModel.userAuthState.collectAsStateWithLifecycle()

    // --- Combined Login State ---
    // User is logged in if either Firebase has a user OR AppAuth state is authorized.
    // It's null if Firebase state is still loading (null).
    val isUserLoggedIn: Boolean? = remember(firebaseUserLoggedIn, googleAuthState.isAuthorized) {
        when (firebaseUserLoggedIn) {
            null -> null // Firebase state unknown, so overall state is unknown
            true -> true // Firebase user exists, definitely logged in
            false -> googleAuthState.isAuthorized // No Firebase user, rely on Google state
        }
    }
    // Log the combined state for debugging
    LaunchedEffect(isUserLoggedIn) {
        Log.d("AppNavigation", "Combined Login State: $isUserLoggedIn (Firebase: $firebaseUserLoggedIn, Google: ${googleAuthState.isAuthorized})")
    }


//    // --- ActivityResultLauncher for End Session ---
//    val endSessionLauncher = rememberLauncherForActivityResult(
//        contract = ActivityResultContracts.StartActivityForResult()
//    ) { result ->
//        Log.d("AppNavigation", "End session flow completed with result code: ${result.resultCode}")
//    }

//    // --- Listener for End Session Event ---
//    LaunchedEffect(key1 = authViewModel) {
//        authViewModel.endSessionEvent.collectLatest { intent ->
//            Log.d("AppNavigation", "Received end session event, launching intent.")
//            try {
//                endSessionLauncher.launch(intent)
//            } catch (e: Exception) {
//                Log.e("AppNavigation", "Error launching end session intent: ${e.message}")
//            }
//        }
//    }

    // --- Firebase AuthStateListener ---
    DisposableEffect(key1 = firebaseAuth) {
        val authListener = FirebaseAuth.AuthStateListener { auth ->
            val user = auth.currentUser
            val newState = user != null
            // Only update if the state actually changes from the last known state
            if (newState != firebaseUserLoggedIn) {
                Log.d("AppNavigation", "FirebaseAuth state changed. User: ${user?.uid}, NewState: $newState")
                firebaseUserLoggedIn = newState
            }
        }
        Log.d("AppNavigation", "Adding AuthStateListener")
        firebaseAuth.addAuthStateListener(authListener)
        onDispose {
            Log.d("AppNavigation", "Removing AuthStateListener")
            firebaseAuth.removeAuthStateListener(authListener)
        }
    }

    // --- Determine Start Destination based on Combined State ---
    val startDestination: String? = when (isUserLoggedIn) {
        true -> AppDestinations.MAIN_APP_ROUTE
        false -> AppDestinations.LOGIN_ROUTE
        null -> null // Still loading / undetermined
    }
    Log.d("AppNavigation", "Determined startDestination: $startDestination (CombinedLoginState=$isUserLoggedIn)")


    // --- Render NavHost or Loading ---
    if (startDestination != null) {
        NavHost(
            navController = navController,
            startDestination = startDestination
        ) {
            // --- Login Screen ---
            composable(route = AppDestinations.LOGIN_ROUTE) {
                val loginViewModel: LoginViewModel = viewModel()
                LoginScreen(
                    authViewModel = authViewModel, // Pass AppAuth VM
                    loginViewModel = loginViewModel,
                    onNavigateToRegister = { navController.navigate(AppDestinations.REGISTER_ROUTE) },
                    onLoginSuccess = {
                        // This callback is used by BOTH login methods now
                        navController.navigate(AppDestinations.MAIN_APP_ROUTE) {
                            popUpTo(AppDestinations.LOGIN_ROUTE) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onNavigateToVerify = { email ->
                        val encodedEmail = Uri.encode(email)
                        navController.navigate("verification/$encodedEmail")
                    }
                )
            }

            // --- Register Screen ---
            composable(route = AppDestinations.REGISTER_ROUTE) {
                val registerViewModel: RegisterViewModel = viewModel()
                RegisterScreen(
                    registerViewModel = registerViewModel,
                    onNavigateToLogin = { navController.popBackStack() },
                    onNavigateToVerify = { email ->
                        val encodedEmail = Uri.encode(email)
                        navController.navigate("verification/$encodedEmail") {
                            popUpTo(AppDestinations.REGISTER_ROUTE) { inclusive = true }
                        }
                    }
                )
            }

            // --- Verification Screen ---
            composable(
                route = AppDestinations.VERIFICATION_ROUTE,
                arguments = listOf(navArgument(AppDestinations.VERIFICATION_EMAIL_ARG) { type = NavType.StringType })
            ) { backStackEntry ->
                val encodedEmail = backStackEntry.arguments?.getString(AppDestinations.VERIFICATION_EMAIL_ARG)
                val email = encodedEmail?.let { Uri.decode(it) }
                val verificationViewModel: VerificationViewModel = viewModel()
                VerificationScreen(
                    email = email,
                    verificationViewModel = verificationViewModel,
                    onNavigateToLogin = {
                        navController.navigate(AppDestinations.LOGIN_ROUTE) {
                            popUpTo(AppDestinations.LOGIN_ROUTE) { inclusive = true }
                        }
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // --- Main App Screen (Profile Screen) ---
            composable(route = AppDestinations.MAIN_APP_ROUTE) {
                ProfileScreen(
                    onLogout = {
                        Log.d("AppNavigation", "Logout requested from MainAppScreen")
                        // *** IMPORTANT: Clear BOTH states on logout ***
                        // 1. Clear AppAuth local state & trigger browser logout attempt
                        authViewModel.logout() // Emits endSessionEvent
                        // 2. Sign out from Firebase (triggers AuthStateListener)
                        FirebaseAuth.getInstance().signOut()
                        // 3. Navigation back to Login happens automatically via state change
                    }
                )
            }
        }
    } else {
        // --- Show Loading Screen ---
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
            Log.d("AppNavigation", "Showing loading indicator while combined auth state is null")
        }
    }
}

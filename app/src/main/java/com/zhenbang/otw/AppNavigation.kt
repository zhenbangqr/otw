package com.zhenbang.otw

import ProfileScreen // Assuming this is in the correct package or imported fully
import android.net.Uri
import android.util.Log
// --- REMOVED Unused ActivityResult imports ---
// import androidx.activity.compose.rememberLauncherForActivityResult
// import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser // Import FirebaseUser
import com.zhenbang.otw.auth.* // Import all from auth package (includes ViewModels)
// *** Corrected ViewModel Import ***
import com.zhenbang.otw.enterSelfDetails.EnterSelfDetailsViewModel // Use PascalCase
import com.zhenbang.otw.emailVerification.VerificationScreen
import com.zhenbang.otw.emailVerification.VerificationViewModel
import com.zhenbang.otw.enterSelfDetails.EnterSelfDetailsScreen
import com.zhenbang.otw.login.LoginScreen
// *********************************
import com.zhenbang.otw.login.LoginViewModel
import com.zhenbang.otw.profile.ProfileStatus
import com.zhenbang.otw.profile.ProfileViewModel
import com.zhenbang.otw.register.RegisterScreen
import com.zhenbang.otw.register.RegisterViewModel

// --- REMOVED Unused Flow import ---
// import kotlinx.coroutines.flow.collectLatest


object AppDestinations {
    const val LOGIN_ROUTE = "login"
    const val REGISTER_ROUTE = "register"
    const val VERIFICATION_EMAIL_ARG = "email"
    const val VERIFICATION_ROUTE = "verification/{$VERIFICATION_EMAIL_ARG}"
    const val ENTER_SELF_DETAILS_ROUTE = "enter_self_details" // Renamed route constant
    const val MAIN_APP_ROUTE = "main_app" // This is your main ProfileScreen route
    const val LOADING_ROUTE = "loading"
}

// Define possible auth states for clarity
private enum class ResolvedAuthState {
    LOADING,
    LOGGED_OUT,
    NEEDS_VERIFICATION,
    NEEDS_PROFILE_DETAILS,
    LOGGED_IN_GOOGLE, // User logged in via Google AND profile is complete/error
    LOGGED_IN_VERIFIED_EMAIL // User logged in via Firebase and verified AND profile is complete/error
}

@Composable
fun AppNavigation() {
    val navController: NavHostController = rememberNavController()
    val firebaseAuth = FirebaseAuth.getInstance()

    // --- Get ViewModels ---
    val authViewModel: AuthViewModel = viewModel()
    val profileViewModel: ProfileViewModel = viewModel()

    // --- Observe Auth States ---
    var firebaseUser by remember { mutableStateOf<FirebaseUser?>(firebaseAuth.currentUser) }
    var firebaseStateInitialized by remember { mutableStateOf(firebaseAuth.currentUser != null) }
    val googleAuthState by authViewModel.userAuthState.collectAsStateWithLifecycle()
    val profileState by profileViewModel.uiState.collectAsStateWithLifecycle()

    // --- Define the Logout Action ---
    val performLogout: () -> Unit = {
        Log.e("AppNavigation", ">>> performLogout called <<<")
        authViewModel.logout()
        FirebaseAuth.getInstance().signOut()
    }
    // -----------------------------

    // --- Listener for Firebase Auth Changes ---
    DisposableEffect(key1 = firebaseAuth) {
        val authListener = FirebaseAuth.AuthStateListener { auth ->
            val newUser = auth.currentUser
            Log.e("AppNavigation", ">>> AuthStateListener fired. New User: ${newUser?.uid} <<<")
            val userChanged = firebaseUser?.uid != newUser?.uid
            firebaseUser = newUser
            firebaseStateInitialized = true
            if (newUser != null && (userChanged || profileState.profileStatus == ProfileStatus.LOADING)) {
                Log.d("AppNavigation", "User changed or logged in OR profile loading, fetching profile for ${newUser.uid}")
                profileViewModel.fetchUserProfile(newUser.uid)
            } else if (newUser == null) {
                Log.d("AppNavigation", "User logged out.")
            }
        }
        Log.d("AppNavigation", "Adding AuthStateListener")
        firebaseAuth.addAuthStateListener(authListener)
        firebaseAuth.currentUser?.uid?.let {
            if (profileState.profileStatus == ProfileStatus.LOADING && profileState.userProfile == null) {
                Log.d("AppNavigation", "Initial fetch for already logged in user: $it")
                profileViewModel.fetchUserProfile(it)
            }
        }
        onDispose {
            Log.d("AppNavigation", "Removing AuthStateListener")
            firebaseAuth.removeAuthStateListener(authListener)
        }
    }

    // --- Determine Resolved Auth State ---
    val resolvedAuthState: ResolvedAuthState = remember(
        firebaseStateInitialized, firebaseUser, googleAuthState.isAuthorized, profileState.profileStatus
    ) {
        val currentUser = firebaseUser
        val isGoogleLoggedIn = googleAuthState.isAuthorized
        val isFirebaseVerified = currentUser?.isEmailVerified == true
        val profileStatus = profileState.profileStatus

        Log.e("AppNavigation", ">>> Calculating State: Initialized=$firebaseStateInitialized, User=${currentUser?.uid}, isFirebaseVerified=$isFirebaseVerified, GoogleAuth=${isGoogleLoggedIn}, ProfileStatus=$profileStatus <<<")

        when {
            isGoogleLoggedIn -> {
                when (profileStatus) {
                    ProfileStatus.LOADING -> ResolvedAuthState.LOADING
                    ProfileStatus.INCOMPLETE -> ResolvedAuthState.NEEDS_PROFILE_DETAILS
                    ProfileStatus.COMPLETE, ProfileStatus.ERROR -> ResolvedAuthState.LOGGED_IN_GOOGLE
                }
            }
            !firebaseStateInitialized -> ResolvedAuthState.LOADING
            currentUser != null && isFirebaseVerified -> {
                when (profileStatus) {
                    ProfileStatus.LOADING -> ResolvedAuthState.LOADING
                    ProfileStatus.INCOMPLETE -> ResolvedAuthState.NEEDS_PROFILE_DETAILS
                    ProfileStatus.COMPLETE, ProfileStatus.ERROR -> ResolvedAuthState.LOGGED_IN_VERIFIED_EMAIL
                }
            }
            currentUser != null && !isFirebaseVerified -> ResolvedAuthState.NEEDS_VERIFICATION
            else -> ResolvedAuthState.LOGGED_OUT
        }
    }
    // Log the resolved state whenever it changes
    LaunchedEffect(resolvedAuthState) {
        Log.e("AppNavigation", ">>> Resolved Auth State Updated To: $resolvedAuthState <<<")
    }


    // --- Commented out End Session Logic (Recommended) ---
    // ...

    // --- *** Stable Navigation Logic based on Resolved State *** ---
    LaunchedEffect(resolvedAuthState, navController) {
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        Log.e("AppNavigation", ">>> NAVIGATION EFFECT RUNNING. State: $resolvedAuthState. Current Route: $currentRoute <<<")

        when (resolvedAuthState) {
            ResolvedAuthState.LOADING -> {
                Log.i("AppNavigation", "NavEffect Decision: State is LOADING, doing nothing.")
            }
            ResolvedAuthState.LOGGED_IN_GOOGLE, ResolvedAuthState.LOGGED_IN_VERIFIED_EMAIL -> {
                Log.i("AppNavigation", "NavEffect Decision: State is LOGGED_IN (Verified/Google + Profile Complete/Error).")
                if (currentRoute != AppDestinations.MAIN_APP_ROUTE) {
                    Log.i("AppNavigation", "NavEffect Action: Navigating to MAIN_APP_ROUTE from $currentRoute.")
                    navController.navigate(AppDestinations.MAIN_APP_ROUTE) {
                        popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                        launchSingleTop = true
                    }
                } else {
                    Log.i("AppNavigation", "NavEffect Action: Already on MAIN_APP_ROUTE.")
                }
            }
            ResolvedAuthState.NEEDS_PROFILE_DETAILS -> {
                Log.i("AppNavigation", "NavEffect Decision: State is NEEDS_PROFILE_DETAILS.")
                if (currentRoute != AppDestinations.ENTER_SELF_DETAILS_ROUTE) {
                    Log.i("AppNavigation", "NavEffect Action: Navigating to ENTER_SELF_DETAILS_ROUTE from $currentRoute.")
                    navController.navigate(AppDestinations.ENTER_SELF_DETAILS_ROUTE) {
                        popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                        launchSingleTop = true
                    }
                } else {
                    Log.i("AppNavigation", "NavEffect Action: Already on ENTER_SELF_DETAILS_ROUTE.")
                }
            }
            ResolvedAuthState.NEEDS_VERIFICATION -> {
                Log.i("AppNavigation", "NavEffect Decision: State is NEEDS_VERIFICATION.")
                val expectedRoutePrefix = "verification/"
                if (currentRoute?.startsWith(expectedRoutePrefix) != true) {
                    val userEmail = firebaseUser?.email
                    if (userEmail != null) {
                        Log.i("AppNavigation", "NavEffect Action: Navigating to VERIFICATION_ROUTE for $userEmail from $currentRoute.")
                        val encodedEmail = Uri.encode(userEmail)
                        navController.navigate("verification/$encodedEmail") {
                            popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                            launchSingleTop = true
                        }
                    } else {
                        Log.e("AppNavigation", "NavEffect ERROR: Needs verification but email is null! Navigating to Login.")
                        navController.navigate(AppDestinations.LOGIN_ROUTE) {
                            popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                } else {
                    Log.i("AppNavigation", "NavEffect Action: Already on VERIFICATION_ROUTE.")
                }
            }
            ResolvedAuthState.LOGGED_OUT -> {
                Log.i("AppNavigation", "NavEffect Decision: State is LOGGED_OUT.")
                // Navigate if not already on Login or Register
                val shouldNavigateToLogin = currentRoute != AppDestinations.LOGIN_ROUTE &&
                        currentRoute != AppDestinations.REGISTER_ROUTE
                Log.e("AppNavigation", ">>> LOGGED_OUT Check: currentRoute=$currentRoute, shouldNavigateToLogin=$shouldNavigateToLogin <<<")

                if (shouldNavigateToLogin) {
                    Log.e("AppNavigation", ">>> NavEffect Action: Navigating to LOGIN_ROUTE from $currentRoute. <<<")
                    navController.navigate(AppDestinations.LOGIN_ROUTE) {
                        popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                        launchSingleTop = true
                    }
                } else if (currentRoute == null) {
                    Log.e("AppNavigation", ">>> NavEffect Action: Current route null, navigating to LOGIN_ROUTE <<<")
                    navController.navigate(AppDestinations.LOGIN_ROUTE) {
                        popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                        launchSingleTop = true
                    }
                } else {
                    Log.i("AppNavigation", "NavEffect Action: Already on LOGIN/REGISTER, staying put.")
                }
            }
        }
    }
    // --- *** End Stable Navigation Logic *** ---


    // --- Render NavHost ---
    NavHost( /* ... NavHost content remains the same ... */
        navController = navController,
        startDestination = AppDestinations.LOGIN_ROUTE // Start at Login
    ) {
        // --- Optional Loading Screen ---
        composable(route = AppDestinations.LOADING_ROUTE) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
                Log.d("AppNavigation", "Displaying Loading Route")
            }
        }

        // --- Login Screen ---
        composable(route = AppDestinations.LOGIN_ROUTE) {
            val loginViewModel: LoginViewModel = viewModel()
            LoginScreen(
                authViewModel = authViewModel,
                loginViewModel = loginViewModel,
                onNavigateToRegister = { navController.navigate(AppDestinations.REGISTER_ROUTE) },
                onLoginSuccess = {
                    Log.d(
                        "AppNavigation",
                        "LoginScreen reported success (onLoginSuccess called). Triggering profile fetch."
                    )
                    firebaseAuth.currentUser?.uid?.let { profileViewModel.fetchUserProfile(it) }
                },
                onNavigateToVerify = { email ->
                    val encodedEmail = Uri.encode(email)
                    Log.d(
                        "AppNavigation",
                        "Login needs verification, navigating to Verification for $email"
                    )
                    navController.navigate("verification/$encodedEmail") {
                        popUpTo(AppDestinations.LOGIN_ROUTE) { inclusive = true } // Pop login
                        launchSingleTop = true
                    }
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
                    Log.d(
                        "AppNavigation",
                        "Register successful, navigating explicitly to Verification for $email"
                    )
                    navController.navigate("verification/$encodedEmail") {
                        popUpTo(AppDestinations.REGISTER_ROUTE) { inclusive = true }
                        launchSingleTop = true
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
                    Log.d("AppNavigation", "Verification successful, navigating to Login")
                    navController.navigate(AppDestinations.LOGIN_ROUTE) {
                        popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNavigateBack = {
                    Log.d("AppNavigation", "Navigating back from Verification, signing out.")
                    FirebaseAuth.getInstance().signOut()
                    navController.navigate(AppDestinations.LOGIN_ROUTE) {
                        popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        // --- Enter Self Details Screen ---
        composable(route = AppDestinations.ENTER_SELF_DETAILS_ROUTE) {
            // *** Corrected ViewModel Instantiation ***
            val enterSelfDetailsViewModel: EnterSelfDetailsViewModel = viewModel()
            // ****************************************
            EnterSelfDetailsScreen(
                // *** Pass correct ViewModel instance ***
                selfDetailsViewModel = enterSelfDetailsViewModel,
                // *************************************
                onDetailsSaved = {
                    Log.d(
                        "AppNavigation",
                        "enterSelfDetailsScreen reported details saved. Triggering profile refetch."
                    )
                    firebaseAuth.currentUser?.uid?.let { profileViewModel.fetchUserProfile(it) }
                },
                // Pass the logout lambda
                onLogout = performLogout // Pass the lambda defined above
            )
        }
        // -------------------------

        // --- Main App Screen (Profile Screen) ---
        composable(route = AppDestinations.MAIN_APP_ROUTE) {
            ProfileScreen(
                // Pass the logout lambda
                onLogout = performLogout // Pass the lambda defined above
            )
        }
    }
}

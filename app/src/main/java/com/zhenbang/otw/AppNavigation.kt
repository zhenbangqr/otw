package com.zhenbang.otw

// Use correct import for the screen composable if it's PascalCase
import com.zhenbang.otw.enterSelfDetails.EnterSelfDetailsScreen // Use PascalCase
import android.net.Uri
import android.util.Log
// --- REMOVED Unused ActivityResult imports ---
// import androidx.activity.compose.rememberLauncherForActivityResult
// import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button // Import Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text // Import Text
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
import com.zhenbang.otw.enterSelfDetails.EnterSelfDetailsViewModel // Use PascalCase name
import com.zhenbang.otw.emailVerification.VerificationScreen
import com.zhenbang.otw.emailVerification.VerificationViewModel
import com.zhenbang.otw.login.LoginScreen
import com.zhenbang.otw.login.LoginViewModel
import com.zhenbang.otw.profile.ProfileStatus
import com.zhenbang.otw.profile.ProfileViewModel
import com.zhenbang.otw.register.RegisterScreen
import com.zhenbang.otw.register.RegisterViewModel
// --- Import MainPageScreen ---
import com.zhenbang.otw.mainPage.MainPageScreen // Import the new main page

// --- REMOVED Unused Flow import ---
// import kotlinx.coroutines.flow.collectLatest


object AppDestinations {
    const val LOGIN_ROUTE = "login"
    const val REGISTER_ROUTE = "register"
    const val VERIFICATION_EMAIL_ARG = "email"
    const val VERIFICATION_ROUTE = "verification/{$VERIFICATION_EMAIL_ARG}"
    const val ENTER_SELF_DETAILS_ROUTE = "enter_self_details"
    const val PROFILE_ROUTE = "profile" // Route for the actual profile details screen
    const val MAIN_PAGE_ROUTE = "main_page" // New main landing page after login/details
    const val WORKSPACE_ROUTE = "workspace" // Route for the future workspace screen
    const val LOADING_ROUTE = "loading"
}

// Define possible auth states for clarity
private enum class ResolvedAuthState {
    LOADING,
    LOGGED_OUT,
    NEEDS_VERIFICATION,
    NEEDS_PROFILE_DETAILS,
    READY_FOR_MAIN_PAGE_GOOGLE, // Google logged in AND profile complete/error
    READY_FOR_MAIN_PAGE_EMAIL // Email verified AND profile complete/error
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
        Log.d("AppNavigation", ">>> performLogout called <<<")
        authViewModel.logout()
        FirebaseAuth.getInstance().signOut()
    }
    // -----------------------------

    // --- Listener for Firebase Auth Changes ---
    DisposableEffect(key1 = firebaseAuth) {
        val authListener = FirebaseAuth.AuthStateListener { auth ->
            val newUser = auth.currentUser
            Log.d("AppNavigation", ">>> AuthStateListener fired. New User: ${newUser?.uid} <<<")
            val userChanged = firebaseUser?.uid != newUser?.uid
            firebaseUser = newUser
            firebaseStateInitialized = true
            // Fetch profile if user exists AND profile isn't already definitively COMPLETE or INCOMPLETE
            if (newUser != null && profileState.profileStatus != ProfileStatus.COMPLETE && profileState.profileStatus != ProfileStatus.INCOMPLETE) {
                Log.d("AppNavigation", "AuthStateListener: User exists and profile not settled. Fetching profile for ${newUser.uid}")
                profileViewModel.fetchUserProfile(newUser.uid)
            } else if (newUser == null) {
                Log.d("AppNavigation", "User logged out.")
            } else {
                Log.d("AppNavigation", "AuthStateListener: User exists but profile already loaded/settled. No fetch needed.")
            }
        }
        Log.d("AppNavigation", "Adding AuthStateListener")
        firebaseAuth.addAuthStateListener(authListener)
        // Initial fetch for already logged-in user, only if profile isn't already loaded/settled
        firebaseAuth.currentUser?.uid?.let {
            if (profileState.profileStatus != ProfileStatus.COMPLETE && profileState.profileStatus != ProfileStatus.INCOMPLETE) {
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

        Log.d("AppNavigation", ">>> Calculating State: Initialized=$firebaseStateInitialized, User=${currentUser?.uid}, isFirebaseVerified=$isFirebaseVerified, GoogleAuth=${isGoogleLoggedIn}, ProfileStatus=$profileStatus <<<")

        when {
            isGoogleLoggedIn || (currentUser != null && isFirebaseVerified) -> {
                // Inner when MUST be exhaustive for ProfileStatus
                when (profileStatus) {
                    ProfileStatus.LOADING -> ResolvedAuthState.LOADING
                    ProfileStatus.INCOMPLETE -> ResolvedAuthState.NEEDS_PROFILE_DETAILS
                    ProfileStatus.COMPLETE, ProfileStatus.ERROR -> {
                        if (isGoogleLoggedIn) ResolvedAuthState.READY_FOR_MAIN_PAGE_GOOGLE
                        else ResolvedAuthState.READY_FOR_MAIN_PAGE_EMAIL
                    }
                    // *** Add case for LOGGED_OUT profile status ***
                    ProfileStatus.LOGGED_OUT -> ResolvedAuthState.LOGGED_OUT // If profile says logged out, treat overall as logged out
                    // **********************************************
                }
            }
            !firebaseStateInitialized -> ResolvedAuthState.LOADING
            currentUser != null && !isFirebaseVerified -> ResolvedAuthState.NEEDS_VERIFICATION
            else -> ResolvedAuthState.LOGGED_OUT
        }
    }
    LaunchedEffect(resolvedAuthState) {
        Log.d("AppNavigation", ">>> Resolved Auth State Updated To: $resolvedAuthState <<<")
    }


    // --- Commented out End Session Logic (Recommended) ---
    // ...

    // --- *** Stable Navigation Logic based on Resolved State *** ---
    LaunchedEffect(resolvedAuthState, navController) {
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        Log.d("AppNavigation", ">>> NAVIGATION EFFECT RUNNING. State: $resolvedAuthState. Current Route: $currentRoute <<<")

        // Determine the target route based SOLELY on the resolved state
        val targetRoute: String? = when (resolvedAuthState) {
            ResolvedAuthState.LOADING -> null // Stay put
            ResolvedAuthState.READY_FOR_MAIN_PAGE_GOOGLE, ResolvedAuthState.READY_FOR_MAIN_PAGE_EMAIL -> AppDestinations.MAIN_PAGE_ROUTE
            ResolvedAuthState.NEEDS_PROFILE_DETAILS -> AppDestinations.ENTER_SELF_DETAILS_ROUTE
            ResolvedAuthState.NEEDS_VERIFICATION -> {
                val userEmail = firebaseUser?.email
                if (userEmail != null) "verification/${Uri.encode(userEmail)}" else AppDestinations.LOGIN_ROUTE
            }
            ResolvedAuthState.LOGGED_OUT -> AppDestinations.LOGIN_ROUTE
        }

        // Navigate only if the target is determined and different from the current route
        if (targetRoute != null && currentRoute != targetRoute) {
            val isVerificationTarget = targetRoute.startsWith("verification/")
            val isVerificationCurrent = currentRoute?.startsWith("verification/") == true

            if (isVerificationTarget && isVerificationCurrent) {
                Log.i("AppNavigation", "Already on verification route, not navigating again.")
            } else {
                val finalTargetRoute = if (targetRoute == AppDestinations.VERIFICATION_ROUTE) {
                    val userEmail = firebaseUser?.email
                    if (userEmail != null) "verification/${Uri.encode(userEmail)}" else AppDestinations.LOGIN_ROUTE
                } else {
                    targetRoute
                }

                Log.d("AppNavigation", ">>> Navigating from $currentRoute to $finalTargetRoute <<<")
                navController.navigate(finalTargetRoute) {
                    popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                    launchSingleTop = true
                }
            }
        } else if (targetRoute != null) {
            Log.i("AppNavigation", "Already on target route ($currentRoute) or no navigation needed for state $resolvedAuthState.")
        } else {
            Log.i("AppNavigation", "Navigation target is null (likely LOADING state).")
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
                    Log.d("AppNavigation", "LoginScreen reported success (onLoginSuccess called). Triggering profile fetch.")
                    val uid = firebaseAuth.currentUser?.uid
                    if (uid != null) {
                        profileViewModel.fetchUserProfile(uid)
                    } else {
                        Log.e("AppNavigation", "onLoginSuccess called but currentUser is null!")
                        performLogout()
                    }
                },
                onNavigateToVerify = { email ->
                    val encodedEmail = Uri.encode(email)
                    Log.d("AppNavigation", "Login needs verification, navigating to Verification for $email")
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
                    Log.d("AppNavigation", "Register successful, navigating explicitly to Verification for $email")
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
            val enterSelfDetailsViewModel: EnterSelfDetailsViewModel = viewModel()
            EnterSelfDetailsScreen( // Use PascalCase name
                selfDetailsViewModel = enterSelfDetailsViewModel,
                onDetailsSaved = {
                    Log.d("AppNavigation", "EnterSelfDetailsScreen reported details saved. Triggering profile refetch.")
                    firebaseAuth.currentUser?.uid?.let { profileViewModel.fetchUserProfile(it) }
                },
                onLogout = performLogout
            )
        }

        // --- Profile Screen ---
        composable(route = AppDestinations.PROFILE_ROUTE) {
            ProfileScreen(
                onLogout = performLogout
            )
        }

        // --- Workspace Screen (Placeholder) ---
        composable(route = AppDestinations.WORKSPACE_ROUTE) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Workspace Screen Placeholder")
                    Button(onClick = { navController.navigate(AppDestinations.MAIN_PAGE_ROUTE){ popUpTo(AppDestinations.MAIN_PAGE_ROUTE){inclusive = true} } }) {
                        Text("Back to Main Page")
                    }
                }
            }
        }

        // --- Main Page Screen ---
        composable(route = AppDestinations.MAIN_PAGE_ROUTE) {
            MainPageScreen(
                onNavigateToProfile = { navController.navigate(AppDestinations.PROFILE_ROUTE) },
                onNavigateToWorkspace = { navController.navigate(AppDestinations.WORKSPACE_ROUTE) },
                onLogout = performLogout
            )
        }
    }
}

package com.zhenbang.otw

// Android & Compose Imports
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.zhenbang.otw.auth.*
import com.zhenbang.otw.enterSelfDetails.EnterSelfDetailsScreen
import com.zhenbang.otw.enterSelfDetails.EnterSelfDetailsViewModel
import com.zhenbang.otw.emailVerification.VerificationScreen
import com.zhenbang.otw.emailVerification.VerificationViewModel
import com.zhenbang.otw.login.LoginScreen
import com.zhenbang.otw.login.LoginViewModel
import com.zhenbang.otw.profile.ProfileStatus
import com.zhenbang.otw.profile.ProfileViewModel
import com.zhenbang.otw.profile.ProfileScreen
import com.zhenbang.otw.register.RegisterScreen
import com.zhenbang.otw.register.RegisterViewModel
import com.zhenbang.otw.mainPage.MainPageScreen
import com.zhenbang.otw.departments.DepartmentDetailsScreen
import com.zhenbang.otw.departments.DepartmentViewModel
import com.zhenbang.otw.departments.Screen
import com.zhenbang.otw.tasks.AddEditTaskScreen
import com.zhenbang.otw.tasks.TaskDetailScreen
import com.zhenbang.otw.tasks.TaskViewModel
import com.zhenbang.otw.issues.AddEditIssueScreen
import com.zhenbang.otw.issues.IssueViewModel
import com.zhenbang.otw.profile.language.LanguageScreen
import com.zhenbang.otw.profile.manageAccount.ManageAccountScreen
import com.zhenbang.otw.profile.privacy.PrivacyScreen
import com.zhenbang.otw.ui.screen.HomeScreen
import com.zhenbang.otw.ui.viewmodel.LiveLocationViewModel
import com.zhenbang.otw.ui.viewmodel.NewsViewModel
import com.zhenbang.otw.ui.viewmodel.WeatherViewModel


// --- Unified Destinations ---
object AppDestinations {
    const val LOGIN_ROUTE = "login"
    const val REGISTER_ROUTE = "register"
    const val VERIFICATION_EMAIL_ARG = "email"
    const val VERIFICATION_ROUTE = "verification/{$VERIFICATION_EMAIL_ARG}"
    const val ENTER_SELF_DETAILS_ROUTE = "enter_self_details"
    const val MAIN_PAGE_ROUTE = "main_page"
    const val LOADING_ROUTE = "loading"
    const val PROFILE_ROUTE = "profile"
    const val LANGUAGE_SELECTION_ROUTE = "language_selection"
    const val MANAGE_ACCOUNT_ROUTE = "manage_account"
    const val PRIVACY_ROUTE = "privacy"

    // Destinations for Department Feature
    const val DEPARTMENT_LIST_ROUTE = "department_list"
    const val DEPARTMENT_ID_ARG = "departmentId"
    const val DEPARTMENT_NAME_ARG = "departmentName"
    const val TASK_ID_ARG = "taskId"
    const val ISSUE_ID_ARG = "issueId"
    const val DEPARTMENT_DETAILS_ROUTE = "department_details/{$DEPARTMENT_ID_ARG}/{$DEPARTMENT_NAME_ARG}"
    const val TASK_DETAIL_ROUTE = "task_details/{$TASK_ID_ARG}"
    const val ADD_EDIT_TASK_ROUTE = "add_edit_task/{$DEPARTMENT_ID_ARG}/{$TASK_ID_ARG}"
    const val ADD_EDIT_ISSUE_ROUTE = "add_edit_issue/{$DEPARTMENT_ID_ARG}/{$ISSUE_ID_ARG}"
}

private enum class ResolvedAuthState {
    LOADING,
    LOGGED_OUT,
    NEEDS_VERIFICATION,
    NEEDS_PROFILE_DETAILS,
    READY_FOR_MAIN_PAGE_GOOGLE,
    READY_FOR_MAIN_PAGE_EMAIL
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

    // --- Context for Department/Task/Issue ViewModel Factories ---
    val context = LocalContext.current

    // --- Define Logout Action ---
    val performLogout: () -> Unit = {
        Log.d("AppNavigation", ">>> performLogout called <<<")
        authViewModel.logout()
        FirebaseAuth.getInstance().signOut()
        navController.navigate(AppDestinations.LOGIN_ROUTE) {
            popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
            launchSingleTop = true
        }
    }

    // --- Listener for Firebase Auth Changes ---
    DisposableEffect(key1 = firebaseAuth) {
        val authListener = FirebaseAuth.AuthStateListener { auth ->
            val newUser = auth.currentUser
            Log.d("AppNavigation", ">>> AuthStateListener fired. New User: ${newUser?.uid} <<<")
            val userChanged = firebaseUser?.uid != newUser?.uid
            firebaseUser = newUser
            firebaseStateInitialized = true
            if (newUser != null && profileState.profileStatus != ProfileStatus.COMPLETE && profileState.profileStatus != ProfileStatus.INCOMPLETE) {
                Log.d("AppNavigation", "AuthStateListener: User exists and profile not settled. Fetching profile for ${newUser.uid}")
                profileViewModel.fetchUserProfile(newUser.uid)
            } else if (newUser == null) {
                Log.d("AppNavigation", "User logged out.")
                // Clear profile state on logout if needed
                // profileViewModel.clearProfile()
            } else {
                Log.d("AppNavigation", "AuthStateListener: User exists but profile already loaded/settled. No fetch needed.")
            }
        }
        Log.d("AppNavigation", "Adding AuthStateListener")
        firebaseAuth.addAuthStateListener(authListener)
        firebaseAuth.currentUser?.uid?.let {
            if (profileState.profileStatus == ProfileStatus.LOGGED_OUT) {
                Log.d("AppNavigation", "Initial fetch for already logged in user: $it")
                profileViewModel.fetchUserProfile(it)
            }
        }
        onDispose {
            Log.d("AppNavigation", "Removing AuthStateListener")
            firebaseAuth.removeAuthStateListener(authListener)
        }
    }

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
                when (profileStatus) {
                    ProfileStatus.LOADING -> ResolvedAuthState.LOADING // Still loading profile
                    ProfileStatus.INCOMPLETE -> ResolvedAuthState.NEEDS_PROFILE_DETAILS // Needs details filled
                    ProfileStatus.COMPLETE, ProfileStatus.ERROR -> { // Profile complete or error loading it
                        if (isGoogleLoggedIn) ResolvedAuthState.READY_FOR_MAIN_PAGE_GOOGLE
                        else ResolvedAuthState.READY_FOR_MAIN_PAGE_EMAIL
                    }
                    ProfileStatus.LOGGED_OUT -> ResolvedAuthState.LOGGED_OUT
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

    LaunchedEffect(resolvedAuthState, navController) {
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        Log.d("AppNavigation", ">>> NAVIGATION EFFECT RUNNING. State: $resolvedAuthState. Current Route: $currentRoute <<<")

        val mainAuthenticatedRoutes = listOf(
            AppDestinations.MAIN_PAGE_ROUTE,
            AppDestinations.PROFILE_ROUTE,
            AppDestinations.DEPARTMENT_LIST_ROUTE,
            AppDestinations.DEPARTMENT_DETAILS_ROUTE,
            AppDestinations.TASK_DETAIL_ROUTE,
            AppDestinations.ADD_EDIT_TASK_ROUTE,
            AppDestinations.ADD_EDIT_ISSUE_ROUTE,
            AppDestinations.LANGUAGE_SELECTION_ROUTE,
            AppDestinations.MANAGE_ACCOUNT_ROUTE,
            AppDestinations.PRIVACY_ROUTE
        )

        val targetRoute: String? = when (resolvedAuthState) {
            ResolvedAuthState.LOADING -> null
            ResolvedAuthState.READY_FOR_MAIN_PAGE_GOOGLE, ResolvedAuthState.READY_FOR_MAIN_PAGE_EMAIL -> AppDestinations.MAIN_PAGE_ROUTE
            ResolvedAuthState.NEEDS_PROFILE_DETAILS -> AppDestinations.ENTER_SELF_DETAILS_ROUTE
            ResolvedAuthState.NEEDS_VERIFICATION -> {
                val userEmail = firebaseUser?.email
                if (userEmail != null) "${AppDestinations.VERIFICATION_ROUTE.substringBefore('{')}${Uri.encode(userEmail)}"
                else AppDestinations.LOGIN_ROUTE
            }
            ResolvedAuthState.LOGGED_OUT -> AppDestinations.LOGIN_ROUTE
        }

        if (targetRoute != null) {
            if (currentRoute == AppDestinations.REGISTER_ROUTE && targetRoute == AppDestinations.LOGIN_ROUTE) {
                Log.i("AppNavigation", "Preventing navigation from Register to Login during state recalc.")
            }
            else {

                val isVerificationTarget = targetRoute.startsWith(AppDestinations.VERIFICATION_ROUTE.substringBefore('{'))
                val isVerificationCurrent = currentRoute?.startsWith(AppDestinations.VERIFICATION_ROUTE.substringBefore('{')) == true

                val shouldNavigate: Boolean = when {
                    // Don't navigate from authenticated routes back to main page if already there or deeper
                    currentRoute in mainAuthenticatedRoutes && targetRoute == AppDestinations.MAIN_PAGE_ROUTE -> false
                    // Don't navigate between verification screens for different emails if already on one (unless target is different)
                    isVerificationCurrent && isVerificationTarget -> currentRoute != targetRoute // Only navigate if exact route differs
                    // Default: navigate if target route is different from current route
                    else -> currentRoute != targetRoute
                }


                if (shouldNavigate) {
                    val finalTargetRoute = if (targetRoute.startsWith(AppDestinations.VERIFICATION_ROUTE.substringBefore('{'))) {
                        val userEmail = firebaseUser?.email
                        if (userEmail != null) "${AppDestinations.VERIFICATION_ROUTE.substringBefore('{')}${Uri.encode(userEmail)}"
                        else AppDestinations.LOGIN_ROUTE // Fallback if email somehow null
                    } else {
                        targetRoute
                    }


                    Log.d("AppNavigation", ">>> Navigating from $currentRoute to $finalTargetRoute <<<")
                    navController.navigate(finalTargetRoute) {
                        popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                        launchSingleTop = true
                    }
                } else {
                    Log.i("AppNavigation", "Already on target route ($currentRoute) or navigation blocked for state $resolvedAuthState.")
                }
            }

        } else {
            Log.i("AppNavigation", "Navigation target is null (likely LOADING state).")
        }
    }

    NavHost(
        navController = navController,
        startDestination = AppDestinations.LOADING_ROUTE
    ) {
        // --- Loading Screen ---
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
                profileViewModel = profileViewModel,
                onNavigateBack = { navController.popBackStack() },
                onLogout = performLogout,
                onNavigateToLanguageSettings = {
                    navController.navigate(AppDestinations.LANGUAGE_SELECTION_ROUTE)
                },
                onNavigateToManageAccount = {
                    navController.navigate(AppDestinations.MANAGE_ACCOUNT_ROUTE)
                },
                onNavigateToPrivacy = {
                    navController.navigate(AppDestinations.PRIVACY_ROUTE)
                }
            )
        }
        // --- Profile Screen -> Language Screen ---
        composable(route = AppDestinations.LANGUAGE_SELECTION_ROUTE) {
            LanguageScreen(navController = navController)
        }

        // --- Profile Screen -> Manage Account Screen ---
        composable(route = AppDestinations.MANAGE_ACCOUNT_ROUTE) {
            ManageAccountScreen(navController = navController, profileViewModel = profileViewModel)
        }

        composable(route = AppDestinations.PRIVACY_ROUTE) {
            PrivacyScreen(navController = navController)
        }

        // --- *** Replace MainPageScreen with HomeScreen *** ---
        composable(route = AppDestinations.MAIN_PAGE_ROUTE) {
            val newsViewModel: NewsViewModel = viewModel()
            val weatherViewModel: WeatherViewModel = viewModel()
            val departmentViewModel: DepartmentViewModel = viewModel(factory = DepartmentViewModel.Factory(context))
            val liveLocationViewModel: LiveLocationViewModel = viewModel() // Obtain LiveLocationViewModel
            // Call the new HomeScreen
            HomeScreen(
                navController = navController,
                profileViewModel = profileViewModel,
                newsViewModel = newsViewModel,
                weatherViewModel = weatherViewModel,
                departmentViewModel = departmentViewModel,
                liveLocationViewModel = liveLocationViewModel,
                onNavigateToProfile = { navController.navigate(AppDestinations.PROFILE_ROUTE) },
                onNavigateToNotifications = {
                    // TODO: Define Notifications route in AppDestinations and navigate
                    Log.d("AppNavigation", "Navigate to Notifications clicked (Not Implemented)")
                    // navController.navigate("notifications_route") // Example
                },
                onNavigateToDepartmentDetails = { deptId, deptName ->
                    navController.navigate(
                        Screen.DepartmentDetails.createRoute(deptId, deptName) // Use Screen object from departments package
                    )
                }
                // Note: Logout is not directly on HomeScreen, assumed handled via Profile/Settings
            )
        }

        // --- *** Department Feature Screens Integrated Here *** ---
        // Department Details Screen
        composable(
            route = AppDestinations.DEPARTMENT_DETAILS_ROUTE,
            arguments = listOf(
                navArgument(AppDestinations.DEPARTMENT_ID_ARG) { type = NavType.IntType },
                navArgument(AppDestinations.DEPARTMENT_NAME_ARG) { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val departmentId = backStackEntry.arguments?.getInt(AppDestinations.DEPARTMENT_ID_ARG) ?: 0
            val encodedName = backStackEntry.arguments?.getString(AppDestinations.DEPARTMENT_NAME_ARG) ?: ""
            val departmentName = Uri.decode(encodedName) // Decode the name

            // DepartmentViewModel, TaskViewModel, IssueViewModel are initialized inside DepartmentDetailsScreen
            DepartmentDetailsScreen(
                navController = navController,
                departmentId = departmentId,
                departmentName = departmentName,
            )
        }

        // Task Detail Screen
        composable(
            route = AppDestinations.TASK_DETAIL_ROUTE,
            arguments = listOf(navArgument(AppDestinations.TASK_ID_ARG) { type = NavType.IntType })
        ) { backStackEntry ->
            val taskId = backStackEntry.arguments?.getInt(AppDestinations.TASK_ID_ARG) ?: 0
            // Initialize TaskViewModel here using context
            val taskViewModel: TaskViewModel = viewModel(factory = TaskViewModel.Factory(context))
            TaskDetailScreen(
                navController = navController,
                taskViewModel = taskViewModel,
                taskId = taskId
            )
        }

        // Add/Edit Task Screen
        composable(
            route = AppDestinations.ADD_EDIT_TASK_ROUTE,
            arguments = listOf(
                navArgument(AppDestinations.DEPARTMENT_ID_ARG) { type = NavType.IntType },
                // Task ID is optional, defaults to -1 for "add" mode
                navArgument(AppDestinations.TASK_ID_ARG) { type = NavType.IntType; defaultValue = -1 }
            )
        ) { backStackEntry ->
            val departmentId = backStackEntry.arguments?.getInt(AppDestinations.DEPARTMENT_ID_ARG) ?: 0
            val taskId = backStackEntry.arguments?.getInt(AppDestinations.TASK_ID_ARG) ?: -1 // Should get default if not passed
            // Initialize TaskViewModel here using context
            val taskViewModel: TaskViewModel = viewModel(factory = TaskViewModel.Factory(context))
            AddEditTaskScreen(
                navController = navController,
                departmentId = departmentId,
                taskViewModel = taskViewModel,
                taskId = taskId
            )
        }

        // Add/Edit Issue Screen
        composable(
            route = AppDestinations.ADD_EDIT_ISSUE_ROUTE,
            arguments = listOf(
                navArgument(AppDestinations.DEPARTMENT_ID_ARG) { type = NavType.IntType },
                // Issue ID is optional, defaults to -1 for "add" mode
                navArgument(AppDestinations.ISSUE_ID_ARG) { type = NavType.IntType; defaultValue = -1 }
            )
        ) { backStackEntry ->
            val departmentId = backStackEntry.arguments?.getInt(AppDestinations.DEPARTMENT_ID_ARG) ?: 0
            val issueId = backStackEntry.arguments?.getInt(AppDestinations.ISSUE_ID_ARG) ?: -1 // Should get default if not passed
            // Initialize IssueViewModel here using context
            val issueViewModel: IssueViewModel = viewModel(factory = IssueViewModel.Factory(context))
            AddEditIssueScreen(
                navController = navController,
                departmentId = departmentId,
                issueViewModel = issueViewModel,
                issueId = issueId
            )
        }
    } // End NavHost
} // End AppNavigation
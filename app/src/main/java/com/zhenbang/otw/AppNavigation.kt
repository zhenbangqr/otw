package com.zhenbang.otw

import androidx.compose.runtime.Composable
import androidx.navigation.compose.*

object AppDestinations { // <--- Place it here
    const val LOGIN_ROUTE = "login"
    const val REGISTER_ROUTE = "register"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController() // Create the NavController

    NavHost(
        navController = navController,
        startDestination = AppDestinations.LOGIN_ROUTE // Start at the Login screen
    ) {
        // Define the Login Screen destination
        composable(route = AppDestinations.LOGIN_ROUTE) {
            LoginScreen(
                // Pass a lambda function to handle navigation *to* Register
                onNavigateToRegister = {
                    navController.navigate(AppDestinations.REGISTER_ROUTE)
                }
                // authViewModel is provided by default viewModel() within LoginScreen
            )
        }

        // Define the Register Screen destination
        composable(route = AppDestinations.REGISTER_ROUTE) {
            RegisterScreen(
                // Pass a lambda function to handle navigation *back* to Login
                onNavigateToLogin = {
                    navController.popBackStack() // Simple back navigation
                    // Or navigate specifically if needed, clearing the stack:
                    // navController.navigate(AppDestinations.LOGIN_ROUTE) {
                    //     popUpTo(AppDestinations.LOGIN_ROUTE) { inclusive = true }
                    // }
                }
                // registerViewModel would be provided here if needed
            )
        }

        // Add other destinations (e.g., your main app screen after login)
        // composable(route = "main_dashboard") { /* MainDashboardScreen(...) */ }
    }
}
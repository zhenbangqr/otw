package com.zhenbang.otw

import androidx.compose.runtime.Composable
import androidx.navigation.compose.*
import androidx.lifecycle.viewmodel.compose.viewModel

import com.zhenbang.otw.auth.RegisterViewModel

object AppDestinations {
    const val LOGIN_ROUTE = "login"
    const val REGISTER_ROUTE = "register"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = AppDestinations.LOGIN_ROUTE
    ) {
        // Define the Login Screen destination
        composable(route = AppDestinations.LOGIN_ROUTE) {
            LoginScreen(
                onNavigateToRegister = {
                    navController.navigate(AppDestinations.REGISTER_ROUTE)
                }
            )
        }

        composable(route = AppDestinations.REGISTER_ROUTE) {
            val registerViewModel: RegisterViewModel = viewModel()
            RegisterScreen(
                registerViewModel = registerViewModel,
                onNavigateToLogin = {
                    navController.popBackStack()
                }
            )
        }

        // Add other destinations (e.g., your main app screen after login)
        // composable(route = AppDestinations.MAIN_DASHBOARD_ROUTE) { /* ... */ }
    }
}
package com.zhenbang.otw.mainPage

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainPageScreen(
    onNavigateToProfile: () -> Unit,
    onNavigateToWorkspace: () -> Unit,
    onLogout: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Main Menu") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 32.dp), // Add some horizontal padding
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center // Center buttons vertically
        ) {
            Text(
                "Welcome!", // Simple welcome message
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 48.dp) // Space below title
            )

            // Button to go to Profile
            Button(
                onClick = onNavigateToProfile,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp) // Add vertical spacing
            ) {
                Text("Go to Profile")
            }

            // Button to go to Workspace
            Button(
                onClick = onNavigateToWorkspace,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp) // Add vertical spacing
            ) {
                Text("Go to Workspace")
            }

            Spacer(modifier = Modifier.height(32.dp)) // Space before logout

            // Logout Button
            Button(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp), // Add vertical spacing
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) // Use error color for logout
            ) {
                Text("Log Out")
            }
        }
    }
}
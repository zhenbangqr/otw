package com.zhenbang.otw

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhenbang.otw.auth.AuthViewModel

// In a new file or appropriate UI file
@Composable
fun ProfileScreen(onLogout: () -> Unit) { // Accept lambda parameter
    val authViewModel: AuthViewModel = viewModel() // For Google/AppAuth

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Main App Screen!", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                Log.d("LogoutButton", "Logout button clicked, calling viewModel.logout()...") // <-- ADD THIS
                authViewModel.logout() // Or call onLogout() lambda
            }) { Text("Logout") }
        }
    }
}
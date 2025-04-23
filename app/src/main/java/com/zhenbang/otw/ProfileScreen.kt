// ProfileScreen.kt
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
// *** Remove the viewModel import if no longer needed directly here ***
// import androidx.lifecycle.viewmodel.compose.viewModel
// import com.zhenbang.otw.auth.AuthViewModel

@Composable
fun ProfileScreen(onLogout: () -> Unit) { // Keep the lambda parameter
    // *** Remove the direct viewModel instance if only used for logout ***
    // val authViewModel: AuthViewModel = viewModel()

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Main App Screen!", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onLogout) { // <-- *** CHANGE THIS LINE ***
                Text("Logout")
            }
        }
    }
}
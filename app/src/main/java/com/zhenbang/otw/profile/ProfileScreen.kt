// Ensure this package matches where your other UI screens are
package com.zhenbang.otw // Or com.zhenbang.otw.profile

import android.net.Uri // Import Uri
import android.widget.Toast // For placeholder clicks
import androidx.activity.compose.rememberLauncherForActivityResult // Import launcher
import androidx.activity.result.contract.ActivityResultContracts // Import contracts
import androidx.compose.foundation.BorderStroke // Import BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn // Use LazyColumn for scrollable list
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack // Back arrow
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight // Right arrow
import androidx.compose.material.icons.filled.AccountCircle // Placeholder icon
import androidx.compose.material.icons.filled.MoreVert // More options icon
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor // Import SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource // Import painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage // Import Coil
import coil.request.ImageRequest
import com.google.firebase.auth.FirebaseAuth
// Make sure ProfileViewModel is in the correct package
import com.zhenbang.otw.profile.ProfileViewModel // Import ProfileViewModel
import com.zhenbang.otw.profile.ProfileStatus // Import ProfileStatus


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    profileViewModel: ProfileViewModel = viewModel(),
    onLogout: () -> Unit,
    // Add navigation callback if needed, e.g., to go back
    // onNavigateBack: () -> Unit
) {
    val uiState by profileViewModel.uiState.collectAsState()
    val context = LocalContext.current

    // --- ActivityResultLauncher for Image Picker ---
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent() // Use GetContent for gallery
    ) { uri: Uri? ->
        // Pass the selected URI (or null) to the ViewModel
        profileViewModel.handleProfileImageSelection(uri)
    }
    // ---------------------------------------------

    // Fetch profile when the screen is composed if not already loaded/loading
    LaunchedEffect(Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        // Fetch only if we don't have a profile OR if the status indicates an error occurred previously
        if (userId != null && (uiState.userProfile == null || uiState.profileStatus == ProfileStatus.ERROR)) {
            profileViewModel.fetchUserProfile(userId)
        }
    }

    // Show toast for errors (can be refined with Snackbar)
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            profileViewModel.clearError() // Clear error after showing
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                navigationIcon = {
                    // IconButton(onClick = onNavigateBack) { ... }
                },
                actions = {
                    IconButton(onClick = { /* TODO: Handle More options */ }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) { // Use Box to overlay progress indicator
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(bottom = 16.dp)
            ) {

                // Header Section
                item {
                    ProfileHeader(
                        name = uiState.userProfile?.displayName ?: "Loading...",
                        bio = uiState.userProfile?.bio ?: "",
                        imageUrl = uiState.userProfile?.profileImageUrl,
                        // --- Launch image picker on click ---
                        onImageClick = {
                            imagePickerLauncher.launch("image/*") // Mime type for images
                        }
                        // ------------------------------------
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Settings Sections (Using Updated SettingsListItem with Title/Primary Text)
                item { SettingsListItem(title = "Account", primaryText = "Change number") { Toast.makeText(context, "Change number clicked", Toast.LENGTH_SHORT).show() } }
                item { SettingsListItem(title = "Privacy", primaryText = "Block users") { Toast.makeText(context, "Block users clicked", Toast.LENGTH_SHORT).show() } }
                item { SettingsListItem(title = "Avatar", primaryText = "Change profile picture") { imagePickerLauncher.launch("image/*") } } // Also launch from here
                item { SettingsListItem(title = "Chats", primaryText = "Themes") { Toast.makeText(context, "Themes clicked", Toast.LENGTH_SHORT).show() } }
                item { SettingsListItem(title = "Notifications", primaryText = "Message, groups and call tones") { Toast.makeText(context, "Notifications clicked", Toast.LENGTH_SHORT).show() } }
                item { SettingsListItem(title = "App language", primaryText = "English (device's language)") { Toast.makeText(context, "Language clicked", Toast.LENGTH_SHORT).show() } }
                item { SettingsListItem(title = "Help", primaryText = "SOS!") { Toast.makeText(context, "Help clicked", Toast.LENGTH_SHORT).show() } }


                // Logout Button at the bottom
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = onLogout,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Log Out")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            } // End LazyColumn

            // --- Show Loading Indicator during Image Upload ---
            if (uiState.isUploadingImage) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            // -------------------------------------------------
        } // End Box
    } // End Scaffold
}

// --- Helper Composables ---

@Composable
fun ProfileHeader(
    name: String,
    bio: String,
    imageUrl: String?,
    onImageClick: () -> Unit // Callback for image click
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Profile Picture - Make it clickable
        Box( // Use Box to potentially overlay an edit icon later
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onImageClick) // Make clickable
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "Profile Picture",
                modifier = Modifier.fillMaxSize(), // Fill the Box
                contentScale = ContentScale.Crop
            )
            // TODO: Optionally add an edit icon overlay here
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Name and Bio Column
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            if (bio.isNotBlank()) {
                Text(
                    text = bio,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalContentColor.current.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// --- Updated SettingsListItem ---
@Composable
fun SettingsListItem(
    title: String, // Changed primaryText to title
    primaryText: String, // Added primaryText for the main action text
    onClick: () -> Unit
) {
    Column( // Use Column to stack title and primary text
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp) // Adjust vertical padding
    ) {
        Text(
            text = title,
            // --- Use bodyLarge and Bold for Title ---
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            // ---------------------------------------
            modifier = Modifier.padding(bottom = 2.dp) // Small space below title
        )
        Row( // Keep Row for primary text and arrow
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = primaryText,
                // --- Use bodyMedium for Primary Text ---
                style = MaterialTheme.typography.bodyMedium,
                // --------------------------------------
                modifier = Modifier.weight(1f), // Allow text to take space
                // Optional: Dim the primary text slightly
                color = LocalContentColor.current.copy(alpha = 0.8f)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Divider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f)) // Add divider after each item
}

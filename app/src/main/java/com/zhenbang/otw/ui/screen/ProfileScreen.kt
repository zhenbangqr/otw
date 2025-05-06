// Ensure this package matches where your other UI screens are
package com.zhenbang.otw.ui.screen

import android.content.Intent
import android.net.Uri // Import Uri
import android.widget.Toast // For placeholder clicks
import androidx.activity.compose.rememberLauncherForActivityResult // Import launcher
import androidx.activity.result.contract.ActivityResultContracts // Import contracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn // Use LazyColumn for scrollable list
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack // Back arrow
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight // Right arrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage // Import Coil
import coil.request.ImageRequest
import com.google.firebase.auth.FirebaseAuth
import com.zhenbang.otw.R
import android.content.ActivityNotFoundException // Import for Intent error handling
import android.provider.Settings // Import Settings for Intent action
import com.zhenbang.otw.ui.viewmodel.ProfileStatus
import com.zhenbang.otw.ui.viewmodel.ProfileViewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    profileViewModel: ProfileViewModel,
    onLogout: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToHelp: () -> Unit,
    onNavigateToChatTheme: () -> Unit,
    onNavigateToLanguageSettings: () -> Unit,
    onNavigateToManageAccount: () -> Unit,
    onNavigateToPrivacy: () -> Unit
) {
    val uiState by profileViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val userProfile = uiState.userProfile

    var showImageDialog by rememberSaveable { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        profileViewModel.handleProfileImageSelection(uri)
    }

    // Fetch profile when the screen is composed if not already loaded/loading
    LaunchedEffect(Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null && (uiState.userProfile == null || uiState.profileStatus == ProfileStatus.ERROR)) {
            profileViewModel.fetchUserProfile(userId)
        }
    }

    // Show toast for errors
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            profileViewModel.clearError() // Clear error after showing
        }
    }

    fun openNotificationSettings() {
        try {
            val intent = Intent().apply {
                // Use the specific action for app notification settings
                action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                // Provide the package name of YOUR app
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                // Optional: For older versions, channel ID could be added, but EXTRA_APP_PACKAGE is standard
                // putExtra(Settings.EXTRA_CHANNEL_ID, "your_channel_id") // If targeting a specific channel
            }
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Cannot open notification settings.", Toast.LENGTH_SHORT).show()
            // Fallback: Open general app info screen if specific notification settings fail
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:${context.packageName}")
                context.startActivity(intent)
            } catch (e2: ActivityNotFoundException) {
                Toast.makeText(context, "Cannot open app settings.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error opening settings.", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { // Use the callback
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
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
                        imageUrl = userProfile?.profileImageUrl,
                        onImageClick = {
                            showImageDialog = true
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Settings Sections
                item { SettingsListItem(title = "Account", primaryText = "Manage Account") { onNavigateToManageAccount() } }
                item { SettingsListItem(title = "Privacy", primaryText = "Block users") { onNavigateToPrivacy() } }
                item { SettingsListItem(title = "Chats", primaryText = "Themes") { onNavigateToChatTheme() } }
                item { SettingsListItem(title = "Notifications", primaryText = "App Notification") { openNotificationSettings() } }
                item { SettingsListItem(title = "App language", primaryText = "English") { onNavigateToLanguageSettings() } }
                item { SettingsListItem(title = "Help", primaryText = "Feedback") { onNavigateToHelp() } }


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

            // Loading Indicator during Image Upload
            if (uiState.isUploadingImage) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        } // End Box

        // --- Image Viewer Dialog ---
        if (showImageDialog) {
            // Using AlertDialog for simplicity, could use basic Dialog for more control
            AlertDialog(
                onDismissRequest = { showImageDialog = false },
                // No buttons needed for simple view
                confirmButton = { },
                dismissButton = { },
                // Content is just the image, larger
                text = { // Using text slot for main content area
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(userProfile?.profileImageUrl)
                            .crossfade(true)
                            .placeholder(R.drawable.ic_placeholder_profile) // Placeholder while loading large image
                            .error(R.drawable.ic_placeholder_profile)       // Error image
                            .build(),
                        contentDescription = "Profile Picture Enlarged",
                        modifier = Modifier
                            .fillMaxWidth() // Take full dialog width
                            .aspectRatio(1f) // Maintain square aspect ratio
                            .clip(MaterialTheme.shapes.medium), // Optional: slightly rounded corners
                        contentScale = ContentScale.Fit // Fit the image within the bounds
                    )
                },
                // Optional: Make dialog non-dismissable by clicking outside
                // properties = DialogProperties(dismissOnClickOutside = false)
            )
        } // End Image Dialog Condition

    } // End Scaffold
}

@Composable
fun ProfileHeader(
    name: String,
    bio: String,
    imageUrl: String?,
    onImageClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onImageClick)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl)
                    .crossfade(true)
                    .placeholder(R.drawable.ic_placeholder_profile)
                    .error(R.drawable.ic_placeholder_profile)
                    .build(),
                contentDescription = "Profile Picture",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
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

@Composable
fun SettingsListItem(
    title: String,
    primaryText: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = primaryText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                color = LocalContentColor.current.copy(alpha = 0.8f)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Divider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f))
}

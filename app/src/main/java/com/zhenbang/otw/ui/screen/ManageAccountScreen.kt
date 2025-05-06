package com.zhenbang.otw.ui.screen // Correct package

// --- Add ALL necessary imports ---
import android.net.Uri
import android.util.Patterns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions // Import KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.* // Wildcard import for needed icons
import androidx.compose.material3.* // Wildcard import for Material 3 components
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector // Import ImageVector for InfoRow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType // Import KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.firebase.Timestamp
import com.zhenbang.otw.R // Your project's R class
import com.zhenbang.otw.ui.viewmodel.ProfileViewModel
import java.text.SimpleDateFormat
import java.util.Calendar // Import Calendar
import java.util.Locale

// --- End Imports ---

// --- Helper functions ---
fun formatTimestamp(timestamp: Timestamp?, pattern: String = "dd MMM yy"): String {
    return timestamp?.toDate()?.let { date ->
        SimpleDateFormat(pattern, Locale.getDefault()).format(date)
    } ?: "Not set"
}

fun millisToTimestamp(millis: Long?): Timestamp? {
    return millis?.let {
        Timestamp(it / 1000, ((it % 1000) * 1_000_000).toInt())
    }
}
// --- End Helper Functions ---


@OptIn(ExperimentalMaterial3Api::class) // Needed for DatePicker, etc.
@Composable
fun ManageAccountScreen( // Renamed composable
    navController: NavController,
    profileViewModel: ProfileViewModel
) {
    val uiState by profileViewModel.uiState.collectAsState()
    val userProfile = uiState.userProfile
    val userId = userProfile?.uid
    val context = LocalContext.current

    // --- States for values ---
    var currentDisplayName by rememberSaveable(userProfile?.displayName) { mutableStateOf(userProfile?.displayName ?: "") }
    var currentPhoneNumber by rememberSaveable(userProfile?.phoneNumber) { mutableStateOf(userProfile?.phoneNumber ?: "") }
    var currentBio by rememberSaveable(userProfile?.bio) { mutableStateOf(userProfile?.bio ?: "") }
    var currentBirthdateTimestamp by rememberSaveable(userProfile?.dateOfBirth) { mutableStateOf(userProfile?.dateOfBirth) }
    val currentBirthdateString by remember(currentBirthdateTimestamp) { derivedStateOf { formatTimestamp(currentBirthdateTimestamp) } }

    // --- States for dialog visibility ---
    var showEditNameDialog by rememberSaveable { mutableStateOf(false) }
    var showEditPhoneDialog by rememberSaveable { mutableStateOf(false) }
    var showEditBirthdateDialog by rememberSaveable { mutableStateOf(false) }

    // --- Image Picker ---
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> profileViewModel.handleProfileImageSelection(uri) }

    // --- Save action (Top Bar Checkmark) ---
    val onSaveBioIfNeeded: () -> Unit = {
        if (userId != null) {
            if (currentBio != (userProfile?.bio ?: "")) {
                profileViewModel.updateUserProfile(userId, mapOf("bio" to currentBio.trim()))
            }
            navController.popBackStack()
        } else {
            Toast.makeText(context, "Error: User not found", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Scaffold ---
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Profile") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onSaveBioIfNeeded, enabled = !uiState.isUploadingImage) {
                        Icon(Icons.Default.Check, contentDescription = "Save Changes")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column( // Main layout column
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // --- Profile Picture Section ---
            Box(modifier = Modifier.size(120.dp).clickable { imagePickerLauncher.launch("image/*") }) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(uiState.userProfile?.profileImageUrl)
                        .crossfade(true)
                        .placeholder(R.drawable.ic_placeholder_profile) // Use your drawable
                        .error(R.drawable.ic_placeholder_profile) // Use your drawable
                        .build(),
                    contentDescription = "Profile Picture",
                    modifier = Modifier.fillMaxSize().clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = "Change profile picture",
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).padding(6.dp).align(Alignment.BottomEnd),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            // --- Display Name ---
            Text(
                text = currentDisplayName.ifEmpty { userProfile?.displayName ?: "..." }, // Display state or original
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()

            // --- Bio Section ---
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)){
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Bio", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
                OutlinedTextField(
                    value = currentBio,
                    onValueChange = { currentBio = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                    placeholder = { Text("Any details such as age, occupation or city...") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent),
                    textStyle = MaterialTheme.typography.bodyLarge
                )
            }
            HorizontalDivider()

            // --- Name Section (Clickable) ---
            InfoRow(
                icon = Icons.Default.AccountCircle,
                label = "Name",
                value = currentDisplayName.ifEmpty { "..." },
                onClick = { showEditNameDialog = true } // Assign onClick
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // --- Phone Number Section (Clickable) ---
            InfoRow(
                icon = Icons.Default.Phone,
                label = "Phone number",
                value = currentPhoneNumber.ifEmpty { "..." },
                onClick = { showEditPhoneDialog = true } // Assign onClick
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // --- Birthdate Section (Clickable) ---
            InfoRow(
                icon = Icons.Default.Cake,
                label = "Birthdate",
                value = currentBirthdateString,
                onClick = { showEditBirthdateDialog = true } // Assign onClick
            )
            HorizontalDivider()

            // --- Loading Indicator ---
            if (uiState.isUploadingImage) {
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator()
            }

        } // End Main Column

        // --- DIALOGS (Placed outside the main Column but inside Scaffold content) ---

        // Edit Name Dialog
        EditFieldDialog( // Call the dialog composable
            showDialog = showEditNameDialog,
            onDismissRequest = { showEditNameDialog = false },
            title = "Edit Name",
            label = "Display Name",
            initialValue = currentDisplayName,
            onSave = { newName ->
                currentDisplayName = newName
                userId?.let { profileViewModel.updateUserProfile(it, mapOf("displayName" to newName)); Toast.makeText(context,"Name updated", Toast.LENGTH_SHORT).show() }
                    ?: Toast.makeText(context,"Error saving name", Toast.LENGTH_SHORT).show()
            }
        )

        // Edit Phone Dialog
        EditFieldDialog( // Call the dialog composable
            showDialog = showEditPhoneDialog,
            onDismissRequest = { showEditPhoneDialog = false },
            title = "Edit Phone Number",
            label = "Phone Number",
            initialValue = currentPhoneNumber,
            keyboardType = KeyboardType.Phone,
            onSave = { newPhone ->
                if (!Patterns.PHONE.matcher(newPhone).matches()) {
                    Toast.makeText(context,"Invalid phone number format", Toast.LENGTH_SHORT).show()
                    return@EditFieldDialog
                }
                currentPhoneNumber = newPhone
                userId?.let { profileViewModel.updateUserProfile(it, mapOf("phoneNumber" to newPhone)); Toast.makeText(context,"Phone updated", Toast.LENGTH_SHORT).show() }
                    ?: Toast.makeText(context,"Error saving phone", Toast.LENGTH_SHORT).show()
            }
        )

        // Birthdate Picker Dialog
        if (showEditBirthdateDialog) {
            val initialMillis = currentBirthdateTimestamp?.toDate()?.time
            val datePickerState = rememberDatePickerState( // Material 3 date picker state
                initialSelectedDateMillis = initialMillis,
                yearRange = (Calendar.getInstance().apply{add(Calendar.YEAR, -100)}.get(Calendar.YEAR))..(Calendar.getInstance().get(Calendar.YEAR))
            )
            val confirmEnabled by remember { derivedStateOf { datePickerState.selectedDateMillis != null } }

            DatePickerDialog( // Material 3 DatePickerDialog
                onDismissRequest = { showEditBirthdateDialog = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showEditBirthdateDialog = false
                            val selectedMillis = datePickerState.selectedDateMillis
                            val newTimestamp = millisToTimestamp(selectedMillis)
                            if (newTimestamp != null && userId != null) {
                                currentBirthdateTimestamp = newTimestamp
                                profileViewModel.updateUserProfile(userId, mapOf("dateOfBirth" to newTimestamp))
                                Toast.makeText(context,"Birthdate updated", Toast.LENGTH_SHORT).show()
                            } else if (userId == null) {
                                Toast.makeText(context,"Error saving birthdate", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = confirmEnabled
                    ) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { showEditBirthdateDialog = false }) { Text("Cancel") }
                }
            ) {
                DatePicker(state = datePickerState) // Material 3 DatePicker
            }
        } // End Birthdate Dialog Condition

    } // End Scaffold
} // End ManageAccountScreen


// --- InfoRow Composable (WITH onClick parameter added) ---
@Composable
private fun InfoRow(
    icon: ImageVector, // Use ImageVector type
    label: String,
    value: String,
    onClick: (() -> Unit)? = null // Optional onClick lambda
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Apply clickable modifier ONLY if onClick is not null
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        // Optional: Add a chevron icon if clickable
        if (onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Edit $label",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


// --- EditFieldDialog Composable (Definition added here) ---
@Composable
fun EditFieldDialog(
    showDialog: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    label: String,
    initialValue: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onSave: (String) -> Unit
) {
    if (showDialog) {
        var textValue by remember(initialValue) { mutableStateOf(initialValue) }

        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(title) },
            text = {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    label = { Text(label) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSave(textValue.trim())
                        onDismissRequest()
                    },
                    // enabled = textValue.isNotBlank() // Optional validation
                ) { Text("Save") }
            },
            dismissButton = {
                Button(onClick = onDismissRequest) { Text("Cancel") }
            }
        )
    }
}
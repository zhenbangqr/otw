package com.zhenbang.otw // Or your UI package

// --- Keep ALL existing imports ---
import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi // For combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable // For long press
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items // Correct import
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply // Import Reply icon
import androidx.compose.material.icons.automirrored.filled.Send // Send Icon
import androidx.compose.material.icons.filled.* // Import common icons
import androidx.compose.material3.* // Using Material 3
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration // Added for screen width access
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle // For italic text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog // Import Dialog
import androidx.compose.ui.window.DialogProperties // Import DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage // Import Coil
import coil.request.ImageRequest
import com.google.firebase.auth.FirebaseAuth
import com.zhenbang.otw.data.model.ZpResponse
import com.zhenbang.otw.messagemodel.ChatMessage // Import data class with new fields
import com.zhenbang.otw.messagemodel.MessageType
import com.zhenbang.otw.util.JsonFormatter
import com.zhenbang.otw.viewmodel.MessagingViewModel // Import ViewModel
import com.zhenbang.otw.ui.viewmodel.ZpViewModel // For AI part
import com.zhenbang.otw.util.UiState // For AI part
import kotlinx.coroutines.delay // Import delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat // For displaying selected dates
import java.util.* // For Date, Calendar, Locale
import java.util.concurrent.TimeUnit // For time formatting
import com.zhenbang.otw.ui.viewmodel.ZpViewModelFactory // <<< RE-ADD

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
) // Add ExperimentalFoundationApi
@Composable
fun MessagingScreen(
    navController: NavController,
    userIdToChatWith: String,
) {

    val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid
    val context = LocalContext.current
    val application = context.applicationContext as Application

    // Early exit if user is null
    if (currentUserUid == null) {
        Log.e("MessagingScreen", "Current user is null.")
        Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Error: Not Logged In.") }
        LaunchedEffect(Unit) { navController.popBackStack() } // Navigate back
        return
    }

    // --- ViewModel Setup ---

    // MessagingViewModel for chat
    val viewModel: MessagingViewModel = viewModel(
        factory = MessagingViewModel.provideFactory(application, currentUserUid, userIdToChatWith)
    )

    // ZpViewModel for AI Summarization and History Saving
    val zpViewModel: ZpViewModel = viewModel(
        factory = ZpViewModelFactory(application) // Ensure this factory exists
    )

    // --- State from ViewModel ---
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val partnerName by viewModel.partnerName.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val isUploadingImage by viewModel.isUploadingImage.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val isUploadingAudio by viewModel.isUploadingAudio.collectAsStateWithLifecycle()
    val isFetchingLocation by viewModel.isFetchingLocation.collectAsStateWithLifecycle()
    val selectedMessageForReply by viewModel.selectedMessageForReply.collectAsStateWithLifecycle()
    val editingMessage by viewModel.editingMessage.collectAsStateWithLifecycle()

    // --- UI State ---
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Text Input State - reacts to editingMessage starting/stopping
    var messageText by rememberSaveable(editingMessage) {
        mutableStateOf(editingMessage?.text ?: "")
    }

    // Context Menu State
    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuMessage by remember { mutableStateOf<ChatMessage?>(null) }

    // Delete Confirmation Dialog State
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var messageToDelete by remember { mutableStateOf<ChatMessage?>(null) }

    // Audio Playback State
    var currentlyPlayingMessageId by remember { mutableStateOf<String?>(null) }
    var currentAudioPosition by remember { mutableLongStateOf(0L) }
    var currentAudioDuration by remember { mutableLongStateOf(0L) }
    val mediaPlayer = remember { MediaPlayer() } // Consider encapsulating MediaPlayer logic

    // Image Preview State
    var imageToShowFullScreen by rememberSaveable { mutableStateOf<String?>(null) }
    var previewImageUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    // Date Range Picker / JSON Export State (Keep if using AI feature)
    var showDateRangePicker by remember { mutableStateOf(false) }
    val dateRangePickerState = rememberDateRangePickerState() // Use Material 3 state holder
    var selectedStartDateMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedEndDateMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var showJsonDialog by remember { mutableStateOf(false) }
    var generatedJson by remember { mutableStateOf("") }

    // --- AI State Collection
    val apiState by zpViewModel.apiDataState.collectAsStateWithLifecycle()

    // --- Permissions Handling ---
    val imagePermission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
    var hasImagePermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                imagePermission
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val imagePermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            hasImagePermission = isGranted; if (!isGranted) Toast.makeText(
            context,
            "Image permission needed",
            Toast.LENGTH_SHORT
        ).show()
        }

    val audioPermission = Manifest.permission.RECORD_AUDIO
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                audioPermission
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val audioPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            hasAudioPermission = isGranted; if (!isGranted) Toast.makeText(
            context,
            "Audio permission needed",
            Toast.LENGTH_SHORT
        ).show()
        }

    val locationPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    var hasLocationPermission by remember {
        mutableStateOf(locationPermissions.any {
            ContextCompat.checkSelfPermission(
                context,
                it
            ) == PackageManager.PERMISSION_GRANTED
        })
    }
    val locationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            hasLocationPermission = permissions.values.any { it }
            if (hasLocationPermission) viewModel.sendCurrentLocation() else Toast.makeText(
                context,
                "Location permission needed",
                Toast.LENGTH_SHORT
            ).show()
        }

    val imagePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
            if (uri != null) previewImageUri = uri else Log.d("ImagePicker", "No image selected")
        }


    // --- Audio Playback Logic --- (Keep your existing robust implementation)
    fun formatTimeMillis(millis: Long): String {
        if (millis < 0) return "00:00";
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(minutes);
        return String.format("%02d:%02d", minutes, seconds)
    }

    val playbackScope = rememberCoroutineScope()
    var playbackJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun playAudio(url: String, messageId: String) {
        if (currentlyPlayingMessageId == messageId) { // Tap again to stop
            playbackJob?.cancel()
            try {
                mediaPlayer.stop(); mediaPlayer.reset()
            } catch (e: Exception) {
                Log.w("MediaPlayer", "Stop/Reset failed", e)
            } finally {
                currentlyPlayingMessageId = null; currentAudioPosition = 0L; currentAudioDuration =
                    0L
            }
            return
        }
        try {
            playbackJob?.cancel() // Stop previous playback if any
            if (mediaPlayer.isPlaying) mediaPlayer.stop()
            mediaPlayer.reset()
            mediaPlayer.setDataSource(url)
            mediaPlayer.prepareAsync() // Use async preparation
            mediaPlayer.setOnPreparedListener { mp ->
                currentAudioDuration = if (mp.duration > 0) mp.duration.toLong() else 0L
                currentAudioPosition = 0L
                mp.start()
                currentlyPlayingMessageId = messageId
                playbackJob = playbackScope.launch { // Coroutine to update progress
                    while (mp.isPlaying && currentlyPlayingMessageId == messageId) {
                        try {
                            currentAudioPosition = mp.currentPosition.toLong()
                        } catch (e: IllegalStateException) {
                            Log.w("MediaPlayer", "Get position failed", e); break
                        }
                        delay(100) // Update interval
                    }
                    // Update position one last time after stopping/completion
                    if (currentlyPlayingMessageId == messageId && !mp.isPlaying) {
                        try {
                            currentAudioPosition = mp.currentPosition.toLong()
                        } catch (e: IllegalStateException) {
                            currentAudioPosition = currentAudioDuration
                        }
                    }
                }
            }
            mediaPlayer.setOnCompletionListener { mp ->
                playbackJob?.cancel()
                if (currentlyPlayingMessageId == messageId) {
                    currentlyPlayingMessageId = null; currentAudioPosition =
                        0L; // Keep duration? Maybe reset to 0.
                }
                try {
                    mp.reset()
                } catch (e: Exception) {
                    Log.e("MediaPlayer", "Reset on completion failed", e)
                }
            }
            mediaPlayer.setOnErrorListener { mp, _, _ ->
                Log.e("MediaPlayer", "Playback error")
                Toast.makeText(context, "Error playing audio", Toast.LENGTH_SHORT).show()
                playbackJob?.cancel()
                if (currentlyPlayingMessageId == messageId) {
                    currentlyPlayingMessageId = null; currentAudioPosition =
                        0L; currentAudioDuration = 0L
                }
                try {
                    mp.reset()
                } catch (e: Exception) {
                    Log.e("MediaPlayer", "Reset on error failed", e)
                }
                true // Indicate error handled
            }
        } catch (e: Exception) {
            Log.e("MediaPlayer", "Playback setup failed", e)
            Toast.makeText(context, "Cannot play audio", Toast.LENGTH_SHORT).show()
            playbackJob?.cancel()
            if (currentlyPlayingMessageId == messageId) {
                currentlyPlayingMessageId = null; currentAudioPosition = 0L; currentAudioDuration =
                    0L
            }
            try {
                mediaPlayer.reset()
            } catch (re: Exception) { /* Ignore reset error */
            }
        }
    }


    fun stopAudio() {
        playbackJob?.cancel()
        try {
            if (mediaPlayer.isPlaying) mediaPlayer.stop(); mediaPlayer.reset()
        } catch (e: IllegalStateException) {
            Log.e("MediaPlayer", "Stop/Reset error", e)
        }
        currentlyPlayingMessageId = null; currentAudioPosition = 0L; currentAudioDuration = 0L
    }

    // Release MediaPlayer when composable leaves composition
    DisposableEffect(Unit) { onDispose { stopAudio(); mediaPlayer.release() } }


    // --- Effects ---
    LaunchedEffect(messages.size, listState) {
        if (messages.isNotEmpty()) {
            val lastVisibleItemIndex =
                listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val totalItems = listState.layoutInfo.totalItemsCount
            // Scroll to bottom if near the bottom or if it's the initial load/new message from current user
            val shouldScroll =
                lastVisibleItemIndex == -1 || lastVisibleItemIndex >= totalItems - 5 || messages.lastOrNull()?.senderId == currentUserUid
            if (shouldScroll && totalItems > 0) {
                coroutineScope.launch {
                    listState.animateScrollToItem(index = totalItems - 1)
                }
            }
        }
    }

    // --- JSON Generation Logic (Keep if using AI feature) ---
    fun generateJsonAndShowDialog(startMillis: Long?, endMillis: Long?) {
        if (startMillis == null || endMillis == null) {
            Toast.makeText(context, "Invalid date range", Toast.LENGTH_SHORT).show(); return
        }
        // Store the confirmed dates (useful for display)
        selectedStartDateMillis = startMillis
        selectedEndDateMillis = endMillis

        val adjustedEndDateMillis = Calendar.getInstance().apply {
            timeInMillis = endMillis
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(
            Calendar.MILLISECOND,
            999
        )
        }.timeInMillis

        val filteredMessages = messages.filter { msg ->
            val msgTimestamp = msg.timestamp?.toDate()?.time
            msgTimestamp != null && msgTimestamp >= startMillis && msgTimestamp <= adjustedEndDateMillis && !msg.isDeleted // Exclude deleted
        }

        if (filteredMessages.isEmpty()) {
            Toast.makeText(context, "No messages found in timeframe to analyze.", Toast.LENGTH_LONG)
                .show()
            Log.w("JSONExport", "No messages found for AI analysis in timeframe.")
            return
        }

        try {
            val jsonResult =
                JsonFormatter.formatMessagesToJson(filteredMessages) // Use your formatter
            if (jsonResult.isBlank() || jsonResult == "[]") {
                Toast.makeText(context, "Failed to generate analysis data.", Toast.LENGTH_LONG)
                    .show()
                Log.e("JSONExport", "JsonFormatter returned blank/empty array.")
                return
            }
            generatedJson = jsonResult
            Log.d("JSONExport", "Generated JSON for AI:\n$generatedJson")
            zpViewModel.resetState() // Reset AI state before showing dialog
            showJsonDialog = true
        } catch (e: Exception) {
            Log.e("JSONExport", "Error generating JSON", e)
            Toast.makeText(context, "Error preparing data for analysis.", Toast.LENGTH_LONG).show()
        }
    }


    // --- UI Structure ---
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(partnerName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { stopAudio(); navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = { // Keep AI timeframe selection if needed
                    IconButton(onClick = { showDateRangePicker = true }) {
                        Icon(Icons.Filled.DateRange, contentDescription = "Select Timeframe for AI")
                    }
                    // History Button
                    IconButton(onClick = { navController.navigate("history") }) { // Use your actual route name
                        Icon(Icons.Filled.History, contentDescription = "View Summary History")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding() // Adjusts padding for keyboard automatically
        ) {

            // --- Optional: Display selected timeframe for AI ---
            if (selectedStartDateMillis != null && selectedEndDateMillis != null) {
                val formatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                Text(
                    text = "Analysis: ${formatter.format(Date(selectedStartDateMillis!!))} - ${
                        formatter.format(
                            Date(selectedEndDateMillis!!)
                        )
                    }",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .align(Alignment.CenterHorizontally)
                )
            }

            // --- Loading Indicators ---
            if (isUploadingImage || isUploadingAudio || isFetchingLocation) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            // --- Error Display ---
            error?.let { errText ->
                Snackbar( // Use Snackbar for temporary errors?
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    action = { TextButton(onClick = { /* viewModel.clearError() */ }) { Text("Dismiss") } } // Need clearError in VM
                ) {
                    Text(
                        text = errText,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                } // Check Snackbar colors

                // Auto-clear error after a delay
                LaunchedEffect(errText) {
                    delay(4000) // 4 seconds
                    // viewModel.clearError() // Need a clearError function in ViewModel
                }
            }

            // --- Message List ---
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                reverseLayout = false // Keep false for standard top-to-bottom layout
            ) {
                items(
                    items = messages,
                    key = { message ->
                        message.messageId ?: message.hashCode()
                    } // Use ID or fallback
                ) { message ->
                    val isCurrentUserMessage = message.senderId == currentUserUid
                    val isPlaying = message.messageId == currentlyPlayingMessageId
                    val displayDuration =
                        if (isPlaying) currentAudioDuration else (message.audioDurationMillis ?: 0L)
                    val displayPosition = if (isPlaying) currentAudioPosition else 0L

                    // Box to handle context menu trigger area
                    Box {
                        MessageBubble(
                            message = message,
                            isCurrentUserMessage = isCurrentUserMessage,
                            isPlaying = isPlaying,
                            currentPositionMillis = displayPosition,
                            totalDurationMillis = displayDuration,
                            formatTime = ::formatTimeMillis,
                            onImageClick = { imageUrl -> imageToShowFullScreen = imageUrl },
                            onPlayAudioClick = { audioUrl ->
                                playAudio(
                                    audioUrl,
                                    message.messageId ?: ""
                                )
                            },
                            modifier = Modifier.combinedClickable(
                                onClick = { /* No action on single click for now */ },
                                onLongClick = {
                                    if (!message.isDeleted) { // No menu for deleted messages
                                        contextMenuMessage = message
                                        showContextMenu = true
                                    }
                                }
                            )
                        )

                        // Context Menu Dropdown
                        DropdownMenu(
                            expanded = showContextMenu && contextMenuMessage?.messageId == message.messageId,
                            onDismissRequest = { showContextMenu = false },
                            // Adjust offset if needed based on bubble position
                            // offset = DpOffset(x = (10).dp, y = (-50).dp)
                        ) {
                            // Reply
                            DropdownMenuItem(
                                text = { Text("Reply") },
                                onClick = {
                                    viewModel.selectMessageForReply(message); showContextMenu =
                                    false
                                },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Reply, "Reply") }
                            )

                            // Edit (Own, Non-deleted, Text only)
                            if (isCurrentUserMessage && !message.isDeleted && message.messageType == MessageType.TEXT) {
                                DropdownMenuItem(
                                    text = { Text("Edit") },
                                    onClick = {
                                        viewModel.selectMessageForEdit(message); showContextMenu =
                                        false
                                    },
                                    leadingIcon = { Icon(Icons.Filled.Edit, "Edit") }
                                )
                            }

                            // Delete (Own, Non-deleted)
                            if (isCurrentUserMessage && !message.isDeleted) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Delete",
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        messageToDelete = message // Store message for confirmation
                                        showDeleteConfirmDialog = true
                                        showContextMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.Delete,
                                            "Delete",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                )
                            }
                        }
                    } // End Box for Context Menu
                }
            } // --- End Message List ---

            // --- Input Area ---
            Column { // Allows stacking previews above input

                // Reply Preview
                selectedMessageForReply?.let { replyMsg ->
                    ReplyPreview(message = replyMsg, onCancel = { viewModel.cancelReply() })
                }

                // Edit Preview
                editingMessage?.let { editMsg ->
                    EditPreview(message = editMsg, onCancel = { viewModel.cancelEdit() })
                }

                // --- Calculate Send/Save Button Enabled State ---
                // This logic determines if the primary action button (Send/Save) should be enabled
                val isSendEnabled = if (editingMessage != null) {
                    // EDITING MODE: Enable SAVE if text is not blank AND different from original
                    messageText.isNotBlank() && messageText != editingMessage?.text
                } else {
                    // SENDING MODE: Enable SEND if text is not blank OR if currently replying (allows sending empty reply)
                    messageText.isNotBlank() || selectedMessageForReply != null
                }

                val showSendButton = if (editingMessage != null) {
                    true // Always show Check (Save) button when editing
                } else {
                    // Show Send button if text is not blank OR if replying
                    messageText.isNotBlank() || selectedMessageForReply != null
                }

                // Image Send Preview OR Standard Input
                if (previewImageUri != null) {
                    ImagePreviewArea(
                        uri = previewImageUri!!,
                        onSendClick = {
                            viewModel.sendImageMessage(previewImageUri!!, context.contentResolver)
                            previewImageUri = null
                        },
                        onCancelClick = { previewImageUri = null }
                    )
                } else {
                    MessageInput( // Pass calculated enabled state
                        text = messageText,
                        onTextChanged = { messageText = it },
                        onSendClick = {
                            if (editingMessage != null) {
                                viewModel.performEdit(messageText)
                                // State change in viewModel will clear editingMessage, causing recomposition
                            } else {
                                // Allow sending if enabled (covers non-blank text OR replying state)
                                if (isSendEnabled) {
                                    viewModel.sendMessage(messageText)
                                    messageText = "" // Clear input after send
                                }
                            }
                        },
                        isEditing = editingMessage != null,
                        isSendEnabled = isSendEnabled, // <-- Pass the calculated state here
                        showSendButton = showSendButton, // <-- Pass the new state
                        onAttachImageClick = {
                            // Check permission and launch picker
                            if (hasImagePermission) imagePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                            else imagePermissionLauncher.launch(imagePermission)
                        },
                        isRecording = isRecording,
                        onRecordStart = {
                            if (hasAudioPermission) viewModel.startRecording()
                            else audioPermissionLauncher.launch(audioPermission)
                        },
                        onRecordStop = { viewModel.stopRecordingAndSend() },
                        onSendLocationClick = {
                            if (hasLocationPermission) viewModel.sendCurrentLocation()
                            else locationPermissionLauncher.launch(locationPermissions)
                        },
                        isFetchingLocation = isFetchingLocation,
                        // Disable certain actions when editing
                        canAttach = editingMessage == null,
                        canRecord = editingMessage == null,
                        canSendLocation = editingMessage == null
                    )
                }
            } // End Column for Input Area
        } // End Outer Column
    } // End Scaffold

    // --- Dialogs ---

    // Delete Confirmation
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false; messageToDelete = null },
            title = { Text("Delete Message?") },
            text = { Text("Are you sure you want to delete this message?") }, // Soft delete is recoverable if needed later
            confirmButton = {
                TextButton(
                    onClick = {
                        messageToDelete?.let { viewModel.deleteMessage(it) } // Call ViewModel delete
                        showDeleteConfirmDialog = false
                        messageToDelete = null
                    }
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteConfirmDialog = false; messageToDelete = null
                }) { Text("Cancel") }
            }
        )
    }

    // Full Screen Image Viewer
    if (imageToShowFullScreen != null) {
        FullScreenImageDialog(
            imageUrl = imageToShowFullScreen!!,
            onDismiss = { imageToShowFullScreen = null }
        )
    }

    // Date Range Picker (for AI - keep if needed)
    if (showDateRangePicker) {
        DatePickerDialog(
            onDismissRequest = { showDateRangePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val startMillis = dateRangePickerState.selectedStartDateMillis
                        val endMillis = dateRangePickerState.selectedEndDateMillis
                        showDateRangePicker = false
                        generateJsonAndShowDialog(startMillis, endMillis) // Generate JSON
                    },
                    // Enable button only when a valid range is selected
                    enabled = dateRangePickerState.selectedStartDateMillis != null &&
                            dateRangePickerState.selectedEndDateMillis != null &&
                            (dateRangePickerState.selectedEndDateMillis
                                ?: 0) >= (dateRangePickerState.selectedStartDateMillis ?: 0)
                ) { Text("Confirm Timeframe") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDateRangePicker = false
                }) { Text("Cancel") }
            }
        ) { DateRangePicker(state = dateRangePickerState) } // Material 3 DateRangePicker
    }


    if (showJsonDialog) {
        // apiState is collected above

        // State to hold response text when success, needed for Save button
        var responseTextForSave by remember(apiState) {
            mutableStateOf(
                if (apiState is UiState.Success) {
                    (apiState as UiState.Success<ZpResponse>).data.choices?.firstOrNull()?.message?.content
                } else {
                    null
                }
            )
        }

        AlertDialog(
            onDismissRequest = {
                showJsonDialog = false
                zpViewModel.resetState() // Reset on dismiss
            },
            title = { Text("AI Analysis") },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 150.dp, max = 400.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (val state = apiState) {
                        is UiState.Idle -> {
                            LazyColumn {
                                item { Text("JSON to send:", fontWeight = FontWeight.Bold) }
                                item { Spacer(Modifier.height(8.dp)) }
                                item { Text(generatedJson) } // Display the generated JSON
                            }
                        }

                        is UiState.Loading -> {
                            CircularProgressIndicator()
                        }

                        is UiState.Success -> {
                            val textToShow = responseTextForSave ?: "No content in response."
                            LazyColumn {
                                item { Text("AI Response:", fontWeight = FontWeight.Bold) }
                                item { Spacer(Modifier.height(8.dp)) }
                                item { Text(textToShow) }
                            }
                        }

                        is UiState.Error -> {
                            Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {
                Row { // Use Row to layout buttons
                    // Conditionally show Send/Retry button
                    when (apiState) {
                        is UiState.Idle -> {
                            TextButton(onClick = { zpViewModel.fetchDataFromApi(generatedJson) }) {
                                Text("Send to AI")
                            }
                        }

                        is UiState.Loading -> {
                            TextButton(onClick = {}, enabled = false) { Text("Sending...") }
                        }

                        is UiState.Success -> { /* No primary button here */
                        }

                        is UiState.Error -> {
                            TextButton(onClick = { zpViewModel.fetchDataFromApi(generatedJson) }) {
                                Text("Retry")
                            }
                        }
                    }

                    // Add Save Button ONLY on Success
                    if (apiState is UiState.Success && !responseTextForSave.isNullOrBlank()) {
                        Spacer(Modifier.width(8.dp)) // Add space
                        Button( // Filled button for Save
                            onClick = {
                                if (selectedStartDateMillis != null && selectedEndDateMillis != null && generatedJson.isNotBlank()) {
                                    zpViewModel.saveSummaryToHistory(
                                        startMillis = selectedStartDateMillis,
                                        endMillis = selectedEndDateMillis,
                                        requestJson = generatedJson,
                                        aiResponseText = responseTextForSave!! // Safe due to outer check
                                    )
                                    showJsonDialog = false // Close after save
                                    zpViewModel.resetState()
                                } else {
                                    Log.w("SaveAction", "Missing data for saving history.")
                                    Toast.makeText(context, "Could not save.", Toast.LENGTH_SHORT)
                                        .show()
                                }
                            }
                        ) { Text("Save") }
                    }
                }
            },
            dismissButton = {
                // Close button is always visible
                TextButton(onClick = {
                    showJsonDialog = false
                    zpViewModel.resetState()
                }) { Text("Close") }
            }
        )
    }

} // End MessagingScreen


// --- Reusable Composables (MessageBubble, Inputs, Previews) ---

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: ChatMessage,
    isCurrentUserMessage: Boolean,
    modifier: Modifier = Modifier, // Accept modifier for combinedClickable
    isPlaying: Boolean,
    currentPositionMillis: Long,
    totalDurationMillis: Long,
    formatTime: (Long) -> String,
    onImageClick: (imageUrl: String) -> Unit,
    onPlayAudioClick: (audioUrl: String) -> Unit
) {
    val context = LocalContext.current
    val bubbleColor = if (isCurrentUserMessage) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.secondaryContainer
    val contentColor = if (isCurrentUserMessage) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSecondaryContainer

    Row(
        modifier = modifier // Apply combinedClickable here
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isCurrentUserMessage) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium, // Rounded corners
            color = bubbleColor,
            modifier = Modifier
                .widthIn(max = LocalConfiguration.current.screenWidthDp.dp * 0.8f) // Max width constraint
                .padding( // Indent opposite side
                    start = if (isCurrentUserMessage) 64.dp else 0.dp,
                    end = if (isCurrentUserMessage) 0.dp else 64.dp
                ),
            tonalElevation = 1.dp // Slight shadow
        ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {

                // --- Reply Preview ---
                if (message.repliedToMessageId != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                // Slightly different shape for reply part
                                RoundedCornerShape(
                                    topStart = 4.dp,
                                    topEnd = 4.dp,
                                    bottomStart = 2.dp,
                                    bottomEnd = 2.dp
                                )
                            )
                            .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Vertical indicator bar
                        Spacer(
                            modifier = Modifier
                                .width(3.dp)
                                .height(30.dp)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = message.repliedToSenderName
                                    ?: "User", // Use name from message data
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = contentColor // Inherit content color
                            )
                            Text(
                                // Show appropriate preview based on replied type
                                text = message.repliedToPreview ?: "[Message]",
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = contentColor.copy(alpha = 0.8f) // Slightly faded
                            )
                        }
                    }
                } // End Reply Preview

                // --- Main Content or Deleted Placeholder ---
                if (message.isDeleted) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Filled.Block, // Or use DoNotDisturbOn or RemoveCircleOutline
                            contentDescription = "Deleted",
                            tint = contentColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Message deleted",
                            fontStyle = FontStyle.Italic,
                            fontSize = 14.sp,
                            color = contentColor.copy(alpha = 0.7f),
                        )
                    }
                } else {
                    // Display actual content based on type
                    when (message.messageType) {
                        MessageType.TEXT -> {
                            Row(verticalAlignment = Alignment.Bottom) { // Align text and edited tag
                                Text(
                                    text = message.text ?: "",
                                    color = contentColor,
                                    modifier = Modifier.padding(end = if (message.isEdited) 4.dp else 0.dp) // Padding before edited tag
                                )
                                // Show (edited) tag if applicable
                                if (message.isEdited) {
                                    Text(
                                        "(edited)",
                                        fontSize = 10.sp,
                                        color = contentColor.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(bottom = 1.dp) // Fine tune alignment
                                    )
                                }
                            }
                        }

                        MessageType.IMAGE -> {
                            Column { // Use column if image can have text below/overlayed
                                if (message.imageUrl != null) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context).data(message.imageUrl)
                                            .crossfade(true).build(),
                                        contentDescription = "Sent image",
                                        modifier = Modifier
                                            .sizeIn(
                                                maxWidth = 240.dp,
                                                maxHeight = 300.dp
                                            ) // Constrain image size
                                            .padding(bottom = if (message.isEdited) 2.dp else 0.dp) // Padding if edited tag below
                                            .clip(MaterialTheme.shapes.small) // Clip image corners
                                            .clickable { onImageClick(message.imageUrl) },
                                        contentScale = ContentScale.Fit // Fit within bounds
                                    )
                                } else {
                                    // Placeholder for missing image?
                                    Box(
                                        modifier = Modifier
                                            .size(100.dp)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Text("?", modifier = Modifier.align(Alignment.Center))
                                    }
                                }
                                // Add edited tag below image if needed
                                if (message.isEdited) {
                                    Text(
                                        "(edited)",
                                        fontSize = 10.sp,
                                        color = contentColor.copy(alpha = 0.7f),
                                        modifier = Modifier
                                            .align(Alignment.End)
                                            .padding(top = 2.dp)
                                    )
                                }
                            }
                        }

                        MessageType.AUDIO -> {
                            Column(
                                modifier = Modifier.widthIn(
                                    min = 120.dp,
                                    max = 250.dp
                                )
                            ) { // Constrain audio width
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = message.audioUrl != null) {
                                            message.audioUrl?.let { onPlayAudioClick(it) }
                                        },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        tint = contentColor
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    LinearProgressIndicator(
                                        progress = { if (totalDurationMillis > 0) currentPositionMillis.toFloat() / totalDurationMillis.toFloat() else 0f },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(6.dp),
                                        strokeCap = StrokeCap.Round,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant, // M3 track color
                                        color = MaterialTheme.colorScheme.primary // M3 progress color
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.End // Align to end
                                ) {
                                    if (message.isEdited) { // Unlikely for audio, but check
                                        Text(
                                            "(edited)",
                                            fontSize = 10.sp,
                                            color = contentColor.copy(alpha = 0.7f)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = "${formatTime(currentPositionMillis)} / ${
                                            formatTime(
                                                totalDurationMillis
                                            )
                                        }",
                                        fontSize = 12.sp,
                                        color = contentColor.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }

                        MessageType.LOCATION -> {
                            Column(
                                modifier = Modifier
                                    .width(200.dp)
                                    .clickable(enabled = message.location != null) {
                                        message.location?.let { geoPoint ->
                                            val lat = geoPoint.latitude;
                                            val lon = geoPoint.longitude
                                            val name = Uri.encode(
                                                message.locationName ?: "Shared Location"
                                            )
                                            val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon($name)")
                                            val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                                            // Try Google Maps first, then generic
                                            mapIntent.setPackage("com.google.android.apps.maps")
                                            try {
                                                context.startActivity(mapIntent)
                                            } catch (e: Exception) {
                                                try {
                                                    context.startActivity(
                                                        Intent(
                                                            Intent.ACTION_VIEW,
                                                            uri
                                                        )
                                                    )
                                                } catch (e2: Exception) {
                                                    Toast.makeText(
                                                        context,
                                                        "No map app found",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        }
                                    }
                            ) {
                                // Static Map Image
                                if (message.staticMapUrl != null) {
                                    AsyncImage(
                                        model = message.staticMapUrl,
                                        contentDescription = "Map preview: ${message.locationName}",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(120.dp)
                                            .clip(MaterialTheme.shapes.small),
                                        contentScale = ContentScale.Crop // Crop map to fit aspect ratio
                                    )
                                } else {
                                    // Placeholder Map Error Box
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(120.dp)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Icon(
                                            Icons.Filled.Map,
                                            "Map Error",
                                            modifier = Modifier
                                                .align(Alignment.Center)
                                                .size(40.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                // Location Name Row
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.LocationOn,
                                        null,
                                        tint = contentColor.copy(alpha = 0.8f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = message.locationName ?: "[Shared Location]",
                                        color = contentColor,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(
                                            1f,
                                            fill = false
                                        ) // Take needed space
                                    )
                                    if (message.isEdited) { // Unlikely for location
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            "(edited)",
                                            fontSize = 10.sp,
                                            color = contentColor.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }

                        null -> {
                            Text("[Unsupported message]", color = contentColor)
                        }
                    } // End when
                } // End else (!isDeleted)
            } // End Column
        } // End Surface
    } // End Row
}


@Composable
fun MessageInput(
    text: String,
    onTextChanged: (String) -> Unit,
    onSendClick: () -> Unit,
    isEditing: Boolean,
    isSendEnabled: Boolean, // Use this parameter passed from parent
    showSendButton: Boolean, // <-- Add this parameter
    onAttachImageClick: () -> Unit,
    isRecording: Boolean,
    onRecordStart: () -> Unit,
    onRecordStop: () -> Unit,
    onSendLocationClick: () -> Unit,
    isFetchingLocation: Boolean,
    canAttach: Boolean,
    canRecord: Boolean,
    canSendLocation: Boolean
) {
    val disabledContentColor =
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) // M3 standard disabled alpha
    val defaultContentColor = LocalContentColor.current // Use LocalContentColor for enabled tint

    Surface(shadowElevation = 4.dp, tonalElevation = 1.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom // Align items vertically to the bottom
        ) {
            if (isRecording) {
                // Recording Indicator UI
                Icon(
                    Icons.Filled.Mic,
                    "Recording",
                    tint = Color.Red,
                    modifier = Modifier.padding(12.dp)
                ) // Consistent padding
                Text(
                    "Recording...",
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .align(Alignment.CenterVertically)
                )
                IconButton(onClick = onRecordStop) {
                    Icon(
                        Icons.Filled.Stop,
                        "Stop Recording",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                // Action Buttons (Left Side)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onAttachImageClick, enabled = canAttach) {
                        Icon(
                            Icons.Filled.AddPhotoAlternate, "Attach Image",
                            tint = if (canAttach) defaultContentColor else disabledContentColor
                        )
                    }
                    IconButton(
                        onClick = onSendLocationClick,
                        enabled = canSendLocation && !isFetchingLocation
                    ) {
                        if (isFetchingLocation) CircularProgressIndicator(
                            Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        else Icon(
                            Icons.Filled.LocationOn, "Send Location",
                            tint = if (canSendLocation) defaultContentColor else disabledContentColor
                        )
                    }
                }

                // Text Field (Center, takes remaining space)
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChanged,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .align(Alignment.Bottom), // Align to bottom
                    placeholder = { Text(if (isEditing) "Edit message..." else "Type a message...") },
                    shape = MaterialTheme.shapes.medium, // Rounded corners
                    colors = TextFieldDefaults.colors(
                        // Remove underline indicators
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        // Consider customizing container color if needed
                        // containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    maxLines = 5 // Allow multiple lines
                )

                // Send / Save Edit / Record Button (Right Side)
                Box(modifier = Modifier.align(Alignment.CenterVertically)) {
                    if (isEditing) {
                        // Save Edit Button (Always show Check when isEditing is true)
                        IconButton(onClick = onSendClick, enabled = isSendEnabled) {
                            Icon(
                                Icons.Filled.Check, "Save Edit",
                                tint = if (isSendEnabled) MaterialTheme.colorScheme.primary else disabledContentColor
                            )
                        }
                    } else if (showSendButton) { // <-- Use the passed parameter HERE
                        // Send Button
                        IconButton(onClick = onSendClick, enabled = isSendEnabled) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send, "Send",
                                tint = if (isSendEnabled) MaterialTheme.colorScheme.primary else disabledContentColor
                            )
                        }
                    } else {
                        // Record Button
                        IconButton(onClick = onRecordStart, enabled = canRecord) {
                            Icon(
                                Icons.Filled.Mic, "Record Audio",
                                tint = if (canRecord) defaultContentColor else disabledContentColor
                            )
                        }
                    }
                } // End Box for Button
            } // End else (!isRecording)
        } // End Row
    } // End Surface
}


@Composable
fun ImagePreviewArea(uri: Uri, onSendClick: () -> Unit, onCancelClick: () -> Unit) {
    Surface(shadowElevation = 4.dp, tonalElevation = 1.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancelClick) { Icon(Icons.Filled.Cancel, "Cancel Image") }
            Spacer(modifier = Modifier.width(8.dp))
            AsyncImage(
                model = uri, contentDescription = "Image Preview",
                modifier = Modifier
                    .size(64.dp)
                    .clip(MaterialTheme.shapes.small),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onSendClick) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    "Send Image",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun FullScreenImageDialog(imageUrl: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f)) // Slightly darker background
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }) { onDismiss() }, // Click outside to dismiss
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(imageUrl).crossfade(true)
                    .build(),
                contentDescription = "Full screen image",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp), // Padding around image
                contentScale = ContentScale.Fit // Fit image within screen
            )
            // Close button top right
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Filled.Close, "Close", tint = Color.White)
            }
        }
    }
}


// --- Reply/Edit Preview Composables ---

@Composable
fun ReplyPreview(
    message: ChatMessage,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) { // Use column to place divider below
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)) // Use elevation color
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Reply,
                "Replying",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            ) // Tint icon
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = message.repliedToSenderName ?: "User", // Use name from message data
                    fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface // Use appropriate onSurface color
                )
                Text(
                    text = generatePreview(message), // Use common preview generator
                    fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant // Slightly muted color for preview text
                )
            }
            // Cancel Button
            IconButton(
                onClick = onCancel, modifier = Modifier
                    .size(32.dp)
                    .padding(4.dp)
            ) {
                Icon(Icons.Filled.Close, "Cancel Reply", modifier = Modifier.size(18.dp))
            }
        }
        HorizontalDivider(
            thickness = Dp.Hairline,
            color = MaterialTheme.colorScheme.outlineVariant
        ) // Separator line below
    }
}

@Composable
fun EditPreview(
    message: ChatMessage, // Pass the message being edited for context if needed
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) { // Use column for divider
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Edit,
                "Editing",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            ) // Tint icon
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Editing message...", // Simple text indicator
                fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant, // Muted color
                modifier = Modifier.weight(1f)
            )
            // Cancel Button
            IconButton(
                onClick = onCancel, modifier = Modifier
                    .size(32.dp)
                    .padding(4.dp)
            ) {
                Icon(Icons.Filled.Close, "Cancel Edit", modifier = Modifier.size(18.dp))
            }
        }
        HorizontalDivider(
            thickness = Dp.Hairline,
            color = MaterialTheme.colorScheme.outlineVariant
        ) // Separator line below
    }
}


// --- Helper function for previews (can be private to the file) ---
private fun generatePreview(message: ChatMessage): String {
    return when (message.messageType) {
        MessageType.TEXT -> message.text?.take(60)?.let { if (it.length == 60) "$it..." else it }
            ?: "[Message]" // Longer preview?
        MessageType.IMAGE -> "[Image]" + (message.text?.take(40)?.let { "\n\"$it...\"" }
            ?: "") // Show caption preview if exists
        MessageType.AUDIO -> "[Audio Message]"
        MessageType.LOCATION -> message.locationName ?: "[Location]"
        null -> "[Message]"
    }
}

// --- AI Dialog Content Composable (Placeholder - use your actual implementation) ---
@Composable
fun AiDialogContent(
    apiState: UiState<ZpResponse>,
    jsonToSend: String
) { // Replace ChatResponse with your actual response model
    Box( /* ... Box setup ... */
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 150.dp, max = 400.dp), // Adjust max height if needed
        contentAlignment = Alignment.Center
    ) {
        when (val state = apiState) {
            is UiState.Idle -> {
                LazyColumn { // Make JSON scrollable
                    item { Text("JSON to send to AI:", fontWeight = FontWeight.Bold) }
                    item { Spacer(Modifier.height(8.dp)) }
                    item { Text(jsonToSend) } // Display the generated JSON
                }
            }

            is UiState.Loading -> CircularProgressIndicator()
            is UiState.Success -> {
                // --- Replace with accessing your actual response data structure ---
                val responseText =
                    state.data.choices?.firstOrNull()?.message?.content ?: "No content in response."
                // --- End Replace ---
                LazyColumn { // Make response scrollable
                    item { Text("AI Response:", fontWeight = FontWeight.Bold) }
                    item { Spacer(Modifier.height(8.dp)) }
                    item { Text(responseText) }
                }
            }

            is UiState.Error -> Text(
                "Error: ${state.message}",
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

// --- Placeholder for your AI Response Data Class ---
// Replace with your actual data classes from the API response
data class ChatResponse(val choices: List<Choice>?)
data class Choice(val message: ResponseMessage?)
data class ResponseMessage(val content: String?)
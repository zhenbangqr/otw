package com.zhenbang.otw // Or your UI package

import android.Manifest
import android.app.Application // Import Application
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer // Import MediaPlayer
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items // Correct import for LazyColumn items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send // Send Icon
import androidx.compose.material.icons.filled.* // Import common icons
import androidx.compose.material3.* // Using Material 3
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable // Import rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip // For clipping map image corners
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap // For progress indicator cap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight // For bold text
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp // For smaller text size
import androidx.compose.ui.window.Dialog // Import Dialog
import androidx.compose.ui.window.DialogProperties // Import DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage // Import Coil
import coil.request.ImageRequest
import com.google.firebase.auth.FirebaseAuth
import com.zhenbang.otw.messagemodel.MessageType
import com.zhenbang.otw.messagemodel.ChatMessage // Import data class
import com.zhenbang.otw.util.JsonFormatter
import com.zhenbang.otw.viewmodel.MessagingViewModel // Import ViewModel
import kotlinx.coroutines.delay // Import delay
import kotlinx.coroutines.launch
import java.io.IOException // Import IOException
import java.text.SimpleDateFormat // For displaying selected dates
import java.util.* // For Date, Calendar, Locale
import java.util.concurrent.TimeUnit // For time formatting
import com.zhenbang.otw.ui.viewmodel.ZpViewModel // Import MessagingScreen
import com.zhenbang.otw.util.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagingScreen(
    navController: NavController,
    userIdToChatWith: String, // This is the 'otherUserUid'
    zpViewModel: ZpViewModel = viewModel()
) {

    val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid
    val context = LocalContext.current
    val application = context.applicationContext as Application

    // Early exit if user is null
    if (currentUserUid == null) {
        Log.e("MessagingScreen", "Current user is null.")
        Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Error: Not Logged In.") }
        LaunchedEffect(Unit) { navController.popBackStack() }
        return
    }

    val viewModel: MessagingViewModel = viewModel(
        factory = MessagingViewModel.provideFactory(application, currentUserUid, userIdToChatWith)
    )

    // --- Existing State Variables ---
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val partnerName by viewModel.partnerName.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var messageText by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val isUploadingImage by viewModel.isUploadingImage.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val isUploadingAudio by viewModel.isUploadingAudio.collectAsStateWithLifecycle()
    val isFetchingLocation by viewModel.isFetchingLocation.collectAsStateWithLifecycle()

    // --- State for Audio Playback ---
    var currentlyPlayingMessageId by remember { mutableStateOf<String?>(null) }
    var currentAudioPosition by remember { mutableLongStateOf(0L) }
    var currentAudioDuration by remember { mutableLongStateOf(0L) }
    val mediaPlayer = remember { MediaPlayer() }

    // --- State for Image Preview ---
    var imageToShowFullScreen by rememberSaveable { mutableStateOf<String?>(null) }
    var previewImageUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    // --- State for Timeframe Selection & JSON Export (Remains the same) ---
    var showDateRangePicker by remember { mutableStateOf(false) }
    // *** Instantiate the DateRangePickerState here ***
    val dateRangePickerState = rememberDateRangePickerState()
    var selectedStartDateMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedEndDateMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var showJsonDialog by remember { mutableStateOf(false) }
    var generatedJson by remember { mutableStateOf("") } // Will hold the generated JSON

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
            hasImagePermission = isGranted; if (!isGranted) Log.w(
            "Permission",
            "Image permission denied."
        )
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
            hasAudioPermission = isGranted; if (!isGranted) {
            Log.w("Permission", "Audio permission denied."); Toast.makeText(
                context,
                "Audio permission needed",
                Toast.LENGTH_SHORT
            ).show()
        }
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
            hasLocationPermission = permissions.values.any { it }; if (!hasLocationPermission) {
            Log.w("Permission", "Location permission denied."); Toast.makeText(
                context,
                "Location permission needed",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            viewModel.sendCurrentLocation()
        }
        }
    val imagePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
            if (uri != null) previewImageUri = uri else Log.d("ImagePicker", "No image selected")
        }


    // --- Audio Playback Logic ---
    fun formatTimeMillis(millis: Long): String {
        if (millis < 0) return "00:00";
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        val seconds =
            TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(minutes); return String.format(
            "%02d:%02d",
            minutes,
            seconds
        )
    }

    val playbackScope = rememberCoroutineScope();
    var playbackJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun playAudio(
        url: String,
        messageId: String
    ) { /* ... keep existing implementation ... */ if (currentlyPlayingMessageId == messageId) {
        playbackJob?.cancel(); try {
            mediaPlayer.stop(); mediaPlayer.reset()
        } catch (e: IllegalStateException) {/*ignore*/
        } finally {
            currentlyPlayingMessageId = null; currentAudioPosition = 0L; currentAudioDuration =
                0L; }; return
    }; try {
        playbackJob?.cancel(); if (mediaPlayer.isPlaying) mediaPlayer.stop(); mediaPlayer.reset(); mediaPlayer.setDataSource(
            url
        ); mediaPlayer.prepareAsync(); mediaPlayer.setOnPreparedListener { mp ->
            currentAudioDuration =
                if (mp.duration > 0) mp.duration.toLong() else 0L; currentAudioPosition =
            0L; mp.start(); currentlyPlayingMessageId = messageId; playbackJob =
            playbackScope.launch {
                while (mp.isPlaying && currentlyPlayingMessageId == messageId) {
                    try {
                        currentAudioPosition = mp.currentPosition.toLong()
                    } catch (e: IllegalStateException) {
                        Log.w("MediaPlayer", "Failed to get current position."); break
                    }; delay(100)
                }; if (currentlyPlayingMessageId == messageId && !mp.isPlaying) {
                try {
                    currentAudioPosition = mp.currentPosition.toLong()
                } catch (e: IllegalStateException) {
                    Log.w("MediaPlayer", "Failed to get final position."); currentAudioPosition =
                        currentAudioDuration
                }
            }
            }
        }; mediaPlayer.setOnCompletionListener { mp ->
            playbackJob?.cancel(); if (currentlyPlayingMessageId == messageId) {
            currentlyPlayingMessageId = null; currentAudioPosition = 0L; currentAudioDuration =
                0L; }; try {
            mp.reset()
        } catch (e: Exception) {
            Log.e("MediaPlayer", "Error resetting on completion", e)
        }
        }; mediaPlayer.setOnErrorListener { mp, _, _ ->
            Log.e(
                "MediaPlayer",
                "Error playing audio"
            ); Toast.makeText(context, "Error playing audio", Toast.LENGTH_SHORT)
            .show(); playbackJob?.cancel(); if (currentlyPlayingMessageId == messageId) {
            currentlyPlayingMessageId = null; currentAudioPosition = 0L; currentAudioDuration =
                0L; }; try {
            mp.reset()
        } catch (e: Exception) {
            Log.e("MediaPlayer", "Error resetting on error", e)
        }; true
        }
    } catch (e: Exception) {
        Log.e("MediaPlayer", "Playback setup failed", e); Toast.makeText(
            context,
            "Cannot play audio",
            Toast.LENGTH_SHORT
        ).show(); playbackJob?.cancel(); if (currentlyPlayingMessageId == messageId) {
            currentlyPlayingMessageId = null; currentAudioPosition = 0L; currentAudioDuration =
                0L; }; try {
            mediaPlayer.reset()
        } catch (e: Exception) {
            Log.e("MediaPlayer", "Error resetting after setup failure", e)
        }
    }
    }

    fun stopAudio() { /* ... keep existing implementation ... */ playbackJob?.cancel(); try {
        if (mediaPlayer.isPlaying) mediaPlayer.stop(); mediaPlayer.reset()
    } catch (e: IllegalStateException) {
        Log.e("MediaPlayer", "Error stopping/resetting MediaPlayer", e)
    }; currentlyPlayingMessageId = null; currentAudioPosition = 0L; currentAudioDuration = 0L
    }
    DisposableEffect(Unit) {
        onDispose {
            playbackJob?.cancel(); try {
            mediaPlayer.release()
        } catch (e: Exception) {
            Log.e("MediaPlayer", "Error releasing MediaPlayer", e)
        }
        }
    }


    // --- Effects ---
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            val targetIndex =
                (messages.size - 1).coerceAtLeast(0); if (targetIndex < listState.layoutInfo.totalItemsCount) listState.animateScrollToItem(
                index = targetIndex
            ); else if (listState.layoutInfo.totalItemsCount > 0) listState.animateScrollToItem(
                index = listState.layoutInfo.totalItemsCount - 1
            )
        }
    }

    // This function now takes the confirmed dates as parameters
    fun generateJsonAndShowDialog(startMillis: Long?, endMillis: Long?) {
        if (startMillis == null || endMillis == null) {
            Toast.makeText(context, "Invalid date range selected", Toast.LENGTH_SHORT).show()
            return
        }

        // Store the confirmed dates (useful for display)
        selectedStartDateMillis = startMillis
        selectedEndDateMillis = endMillis

        // Adjust end date to be end of the selected day (e.g., 23:59:59.999) for inclusive check
        val adjustedEndDateMillis = Calendar.getInstance().apply {
            timeInMillis = endMillis
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        // Filter messages based on the *passed-in* adjusted timeframe
        // Ensure message.timestamp is not null before trying to access it
        val filteredMessages = messages.filter { msg ->
            val msgTimestamp = msg.timestamp?.toDate()?.time
            // Check if timestamp is valid and within the range
            msgTimestamp != null && msgTimestamp >= startMillis && msgTimestamp <= adjustedEndDateMillis
        }

        // --- CORE CHANGE: Check if any messages were actually found ---
        if (filteredMessages.isEmpty()) {
            // If no messages, inform the user and DO NOT proceed
            Toast.makeText(
                context,
                "No messages found in the selected timeframe to analyze.",
                Toast.LENGTH_LONG // Longer duration might be helpful
            ).show()
            Log.w(
                "JSONExport",
                "No messages found for timeframe $startMillis - $endMillis. AI Dialog will not be shown."
            )
            return // <-- Exit the function here, preventing dialog display
        }

        // --- If messages WERE found, proceed to generate JSON and show dialog ---
        try {
            // Generate JSON using the utility function
            val jsonResult = JsonFormatter.formatMessagesToJson(filteredMessages)

            // Optional safety check: Ensure formatter didn't return empty/invalid JSON
            // for a non-empty list (depends on JsonFormatter implementation)
            if (jsonResult.isBlank() || jsonResult == "[]") {
                Toast.makeText(
                    context,
                    "Failed to generate valid analysis data from messages.",
                    Toast.LENGTH_LONG
                ).show()
                Log.e(
                    "JSONExport",
                    "JsonFormatter returned blank/empty array for non-empty message list."
                )
                return // Exit if formatting unexpectedly failed
            }


            generatedJson = jsonResult // Store the generated JSON
            Log.d("JSONExport", "Generated JSON for AI:\n$generatedJson") // Log the JSON

            // Reset AI ViewModel state (assuming resetState function exists now)
            // If resetState() doesn't exist, remove this line but be aware of potential stale states
            zpViewModel.resetState()

            // Show the AI dialog only because we have valid JSON
            showJsonDialog = true

        } catch (e: Exception) {
            // Catch potential errors during the JSON formatting itself
            Log.e("JSONExport", "Error generating JSON from message list", e)
            Toast.makeText(context, "Error preparing data for analysis.", Toast.LENGTH_LONG).show()
            // Do not show the dialog if formatting failed
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
                // Add actions for timeframe selection
                actions = {
                    IconButton(onClick = { showDateRangePicker = true }) {
                        Icon(Icons.Filled.DateRange, contentDescription = "Select Timeframe")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
        ) {
            // Display selected timeframe (optional, uses the stored selected dates)
            if (selectedStartDateMillis != null && selectedEndDateMillis != null) {
                val formatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                Text(
                    text = "Timeframe for analysis: ${formatter.format(Date(selectedStartDateMillis!!))} - ${
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

            if (isUploadingImage || isUploadingAudio || isFetchingLocation) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            error?.let {
                Text(
                    text = "Error: $it",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // --- Message List ---
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(
                    items = messages,
                    // *** FIXED KEY: Provide non-null fallback using Elvis operator ***
                    key = { message -> // Use explicit parameter name
                        message.messageId ?: message.hashCode()
                    }
                ) { message -> // message is ChatMessage from the lambda parameter
                    val isCurrentlyPlaying = message.messageId == currentlyPlayingMessageId
                    val displayDuration =
                        if (isCurrentlyPlaying) currentAudioDuration else (message.audioDurationMillis
                            ?: 0L)
                    val displayPosition = if (isCurrentlyPlaying) currentAudioPosition else 0L
                    MessageBubble(
                        message = message,
                        isCurrentUserMessage = message.senderId == currentUserUid,
                        isPlaying = isCurrentlyPlaying,
                        currentPositionMillis = displayPosition,
                        totalDurationMillis = displayDuration,
                        formatTime = ::formatTimeMillis,
                        onImageClick = { imageUrl -> imageToShowFullScreen = imageUrl },
                        // *** Added fallback for messageId in case it's null during playback interaction ***
                        onPlayAudioClick = { audioUrl ->
                            playAudio(
                                audioUrl,
                                message.messageId ?: ""
                            )
                        }
                    )
                }
            } // --- End Message List ---

            // --- Image Preview or Message Input ---
            if (previewImageUri != null) {
                ImagePreviewArea(
                    uri = previewImageUri!!,
                    onSendClick = {
                        viewModel.sendImageMessage(
                            previewImageUri!!,
                            context.contentResolver
                        ); previewImageUri = null
                    },
                    onCancelClick = { previewImageUri = null })
            } else {
                MessageInput(
                    text = messageText,
                    onTextChanged = { messageText = it },
                    onSendClick = {
                        if (messageText.isNotBlank()) {
                            viewModel.sendMessage(messageText); messageText = ""
                        }
                    },
                    onAttachImageClick = {
                        if (hasImagePermission) {
                            imagePickerLauncher.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        } else {
                            imagePermissionLauncher.launch(imagePermission)
                        }
                    },
                    isRecording = isRecording,
                    onRecordStart = {
                        if (hasAudioPermission) {
                            viewModel.startRecording()
                        } else {
                            audioPermissionLauncher.launch(audioPermission)
                        }
                    },
                    onRecordStop = { viewModel.stopRecordingAndSend() },
                    onSendLocationClick = {
                        if (hasLocationPermission) {
                            viewModel.sendCurrentLocation()
                        } else {
                            locationPermissionLauncher.launch(locationPermissions)
                        }
                    },
                    isFetchingLocation = isFetchingLocation
                )
            }
        } // End Column
    } // End Scaffold

    // --- Dialogs ---

    if (showDateRangePicker) {
        // Use the state declared earlier
        // val dateRangePickerState = rememberDateRangePickerState() // Defined above

        DatePickerDialog(
            onDismissRequest = { showDateRangePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        // --- THIS IS THE CORE CHANGE ---
                        // 1. Get selected dates from the picker state
                        val startMillis = dateRangePickerState.selectedStartDateMillis
                        val endMillis = dateRangePickerState.selectedEndDateMillis

                        // 2. Close the date picker
                        showDateRangePicker = false

                        // 3. Call the function to generate JSON and show the AI dialog
                        generateJsonAndShowDialog(startMillis, endMillis)
                        // --- End of core change ---
                    },
                    // Enable OK only when a valid range is selected
                    enabled = dateRangePickerState.selectedStartDateMillis != null &&
                            dateRangePickerState.selectedEndDateMillis != null &&
                            // Ensure end date is not before start date
                            (dateRangePickerState.selectedEndDateMillis
                                ?: 0) >= (dateRangePickerState.selectedStartDateMillis ?: 0)
                ) { Text("Confirm Timeframe") } // Changed text slightly for clarity
            },
            dismissButton = {
                TextButton(onClick = { showDateRangePicker = false }) { Text("Cancel") }
            }
        ) {
            DateRangePicker(state = dateRangePickerState) // Pass the state here
        }
    }

    // --- MODIFIED: AI Interaction Dialog (Ensure state reset on close) ---
    if (showJsonDialog) {
        val apiState by zpViewModel.apiDataState.collectAsStateWithLifecycle() // Use collectAsStateWithLifecycle

        AlertDialog(
            onDismissRequest = {
                showJsonDialog = false
                // --- Reset ViewModel state when dialog is dismissed externally ---
                zpViewModel.resetState()
            },
            title = { Text("AI Analysis Input") }, // Adjusted title
            text = {
                // Use the extracted content composable (assuming you have it from previous examples)
                // AiDialogContent(apiState = apiState, jsonToSend = generatedJson)

                // Or inline it as before:
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
                                item { Text(generatedJson) } // Display the generated JSON
                            }
                        }

                        is UiState.Loading -> {
                            CircularProgressIndicator()
                        }

                        is UiState.Success -> {
                            val responseText = state.data.choices.firstOrNull()?.message?.content
                                ?: "No content in response."
                            LazyColumn { // Make response scrollable
                                item { Text("AI Response:", fontWeight = FontWeight.Bold) }
                                item { Spacer(Modifier.height(8.dp)) }
                                item { Text(responseText) }
                                Log.d("JSONResponse", "AI Response Displayed: $responseText")
                            }
                        }

                        is UiState.Error -> {
                            Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {
                val isLoading = apiState is UiState.Loading
                // Define button text and action based on state for Retry logic
                val buttonText: String
                val onClickAction: () -> Unit
                val isEnabled: Boolean

                when (apiState) {
                    is UiState.Idle -> {
                        buttonText = "Send to AI"
                        onClickAction = { zpViewModel.fetchDataFromApi(generatedJson) }
                        isEnabled = true
                    }

                    is UiState.Loading -> {
                        buttonText = "Sending..."
                        onClickAction = {}
                        isEnabled = false
                    }

                    is UiState.Success -> {
                        buttonText = "Done" // Or "Send Again" if you implement reset+send
                        onClickAction =
                            { /* Maybe close dialog? showJsonDialog = false; zpViewModel.resetState() */ }
                        isEnabled = false // Disable after success unless you want "Send Again"
                    }

                    is UiState.Error -> {
                        buttonText = "Retry"
                        onClickAction =
                            { zpViewModel.fetchDataFromApi(generatedJson) } // Resend the same JSON
                        isEnabled = true
                    }
                }

                TextButton(
                    onClick = onClickAction,
                    enabled = isEnabled
                ) {
                    Text(buttonText)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showJsonDialog = false
                    // --- Reset ViewModel state on explicit close ---
                    zpViewModel.resetState()
                }) { Text("Close") }
            }
        )
    }

    // Full Screen Image Dialog
    if (imageToShowFullScreen != null) {
        FullScreenImageDialog(
            imageUrl = imageToShowFullScreen!!,
            onDismiss = { imageToShowFullScreen = null })
    }
}


// --- Composables for MessageBubble, MessageInput, ImagePreviewArea, FullScreenImageDialog ---
@Composable
fun MessageBubble(
    message: ChatMessage,
    isCurrentUserMessage: Boolean,
    isPlaying: Boolean,
    currentPositionMillis: Long,
    totalDurationMillis: Long,
    formatTime: (Long) -> String,
    onImageClick: (imageUrl: String) -> Unit,
    onPlayAudioClick: (audioUrl: String) -> Unit
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isCurrentUserMessage) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = if (isCurrentUserMessage) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.padding(
                start = if (isCurrentUserMessage) 64.dp else 0.dp,
                end = if (isCurrentUserMessage) 0.dp else 64.dp
            ),
            tonalElevation = 1.dp
        ) {
            val contentColor =
                if (isCurrentUserMessage) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer

            when (message.messageType) {
                MessageType.TEXT -> {
                    Text(
                        text = message.text ?: "",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        color = contentColor
                    )
                }

                MessageType.IMAGE -> {
                    if (message.imageUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(message.imageUrl)
                                .crossfade(true).build(),
                            contentDescription = "Sent image",
                            modifier = Modifier
                                .sizeIn(maxWidth = 200.dp, maxHeight = 250.dp)
                                .padding(4.dp)
                                .clickable { onImageClick(message.imageUrl) },
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Text(
                            "[Image unavailable]",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = contentColor
                        )
                    }
                }

                MessageType.LOCATION -> {
                    Column( // Use Column to stack map image and optional name
                        modifier = Modifier
                            .clickable(enabled = message.location != null) {
                                message.location?.let { geoPoint ->
                                    val lat = geoPoint.latitude
                                    val lon = geoPoint.longitude
                                    val gmmIntentUri =
                                        Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(message.locationName ?: "Shared Location")})") // Encode location name
                                    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                                    mapIntent.setPackage("com.google.android.apps.maps")
                                    try {
                                        context.startActivity(mapIntent)
                                    } catch (e: Exception) {
                                        Log.e(
                                            "MapLink",
                                            "Failed to open Google Maps, trying generic.",
                                            e
                                        )
                                        val genericMapIntent =
                                            Intent(Intent.ACTION_VIEW, gmmIntentUri)
                                        try {
                                            context.startActivity(genericMapIntent)
                                        } catch (e2: Exception) {
                                            Log.e(
                                                "MapLink",
                                                "Failed to open any map app",
                                                e2
                                            ); Toast.makeText(
                                                context,
                                                "No map app found",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            }
                            .padding(4.dp) // Add padding around the map/text
                            .width(200.dp) // Constrain width of location bubble
                    ) {
                        if (message.staticMapUrl != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(message.staticMapUrl) // Load the map URL
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Location map preview: ${message.locationName ?: "Shared Location"}",
                                modifier = Modifier
                                    .fillMaxWidth() // Fill constrained width
                                    .height(120.dp) // Set a fixed height
                                    .clip(MaterialTheme.shapes.small), // Clip corners
                                contentScale = ContentScale.Crop // Crop to fit
                            )
                        } else {
                            // Placeholder if map image fails or is null
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Text("Map Error", Modifier.align(Alignment.Center))
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) { // Padding for text below map
                            Icon(
                                imageVector = Icons.Filled.LocationOn,
                                contentDescription = null, // Decorative
                                tint = contentColor.copy(alpha = 0.8f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = message.locationName ?: "[Shared Location]",
                                color = contentColor,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                MessageType.AUDIO -> {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .widthIn(min = 100.dp, max = 250.dp)
                            .clickable(enabled = message.audioUrl != null) {
                                message.audioUrl?.let {
                                    onPlayAudioClick(it)
                                }
                            }) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pause audio" else "Play audio",
                                tint = contentColor
                            ); Spacer(modifier = Modifier.width(8.dp)); LinearProgressIndicator(
                            progress = { if (totalDurationMillis > 0) currentPositionMillis.toFloat() / totalDurationMillis.toFloat() else 0f },
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp),
                            strokeCap = StrokeCap.Round,
                            trackColor = if (isPlaying) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.onSurface.copy(
                                alpha = 0.12f
                            ),
                            color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(
                                alpha = 0.38f
                            )
                        )
                        }; Spacer(modifier = Modifier.height(4.dp)); Text(
                        text = "${
                            formatTime(
                                currentPositionMillis
                            )
                        } / ${formatTime(totalDurationMillis)}",
                        fontSize = 12.sp,
                        color = contentColor.copy(alpha = 0.7f),
                        modifier = Modifier.align(Alignment.End)
                    )
                    }
                }

                null -> {
                    Text(
                        "[Unsupported message]",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        color = contentColor
                    )
                }
            }
        }
    }
}

@Composable
fun MessageInput(
    text: String,
    onTextChanged: (String) -> Unit,
    onSendClick: () -> Unit,
    onAttachImageClick: () -> Unit,
    isRecording: Boolean,
    onRecordStart: () -> Unit,
    onRecordStop: () -> Unit,
    onSendLocationClick: () -> Unit,
    isFetchingLocation: Boolean
) {
    Surface(shadowElevation = 4.dp, tonalElevation = 1.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isRecording) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = "Recording...",
                    tint = Color.Red,
                    modifier = Modifier.padding(horizontal = 12.dp)
                ); Text(
                    text = "Recording...",
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                ); IconButton(onClick = onRecordStop) {
                    Icon(
                        Icons.Filled.Stop,
                        contentDescription = "Stop Recording",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                IconButton(onClick = onAttachImageClick) {
                    Icon(
                        Icons.Filled.AddPhotoAlternate,
                        contentDescription = "Attach Image"
                    )
                }; IconButton(
                    onClick = onSendLocationClick,
                    enabled = !isFetchingLocation
                ) {
                    if (isFetchingLocation) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Filled.LocationOn, contentDescription = "Send Location")
                    }
                }; OutlinedTextField(
                    value = text,
                    onValueChange = onTextChanged,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type a message...") },
                    shape = MaterialTheme.shapes.medium,
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    maxLines = 5
                ); Spacer(modifier = Modifier.width(4.dp)); if (text.isNotBlank()) {
                    IconButton(
                        onClick = onSendClick,
                        enabled = true
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send message",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    IconButton(onClick = onRecordStart) {
                        Icon(
                            Icons.Filled.Mic,
                            contentDescription = "Record Voice Message"
                        )
                    }
                }
            }
        }
    }
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
            IconButton(onClick = onCancelClick) {
                Icon(
                    Icons.Filled.Cancel,
                    contentDescription = "Cancel Image Selection"
                )
            }; Spacer(modifier = Modifier.width(8.dp)); AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(uri).crossfade(true).build(),
            contentDescription = "Image Preview",
            modifier = Modifier
                .size(64.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                .clip(MaterialTheme.shapes.small),
            contentScale = ContentScale.Crop
        ); Spacer(modifier = Modifier.weight(1f)); IconButton(onClick = onSendClick) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send Image",
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
                .background(Color.Black.copy(alpha = 0.8f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ), contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(imageUrl).crossfade(true)
                    .build(),
                contentDescription = "Full screen image",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentScale = ContentScale.Fit
            ); IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Close full screen image",
                tint = Color.White
            )
        }
        }
    }
}
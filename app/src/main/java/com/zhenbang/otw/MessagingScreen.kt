package com.zhenbang.otw

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
import androidx.compose.material.icons.filled.AddPhotoAlternate // Attach icon
import androidx.compose.material.icons.filled.Cancel // Cancel icon
import androidx.compose.material.icons.filled.Close // Close icon
import androidx.compose.material.icons.filled.LocationOn // Location Icon
import androidx.compose.material.icons.filled.Mic // Microphone icon
import androidx.compose.material.icons.filled.Pause // Pause icon
import androidx.compose.material.icons.filled.PlayArrow // Play icon
import androidx.compose.material.icons.filled.Stop // Stop icon
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
import com.zhenbang.otw.ui.viewmodel.MessagingViewModel // Import ViewModel
import kotlinx.coroutines.delay // Import delay
import kotlinx.coroutines.launch
import java.io.IOException // Import IOException
import java.util.concurrent.TimeUnit // For time formatting

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagingScreen(
    navController: NavController,
    userIdToChatWith: String // This is the 'otherUserUid'
) {
    val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid
    val context = LocalContext.current
    val application = context.applicationContext as Application

    if (currentUserUid == null) {
        Log.e("MessagingScreen", "Current user is null.")
        Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Error: Not Logged In.") }
        LaunchedEffect(Unit) { navController.popBackStack() }
        return
    }

    val viewModel: MessagingViewModel = viewModel(
        factory = MessagingViewModel.provideFactory(application, currentUserUid, userIdToChatWith)
    )

    // --- State Variables ---
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

    // Audio Playback State
    var currentlyPlayingMessageId by remember { mutableStateOf<String?>(null) }
    var currentAudioPosition by remember { mutableLongStateOf(0L) }
    var currentAudioDuration by remember { mutableLongStateOf(0L) }
    val mediaPlayer = remember { MediaPlayer() }

    // Image Preview State
    var imageToShowFullScreen by rememberSaveable { mutableStateOf<String?>(null) }
    var previewImageUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    // --- Permissions Handling ---
    val imagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
    var hasImagePermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, imagePermission) == PackageManager.PERMISSION_GRANTED) }
    val imagePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted -> hasImagePermission = isGranted; if (!isGranted) Log.w("Permission", "Image permission denied.") }
    val audioPermission = Manifest.permission.RECORD_AUDIO
    var hasAudioPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED) }
    val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted -> hasAudioPermission = isGranted; if (!isGranted) { Log.w("Permission", "Audio permission denied."); Toast.makeText(context, "Audio permission needed", Toast.LENGTH_SHORT).show() } }
    val locationPermissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    var hasLocationPermission by remember { mutableStateOf(locationPermissions.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions -> hasLocationPermission = permissions.values.any { it }; if (!hasLocationPermission) { Log.w("Permission", "Location permission denied."); Toast.makeText(context, "Location permission needed", Toast.LENGTH_SHORT).show() } else { viewModel.sendCurrentLocation() } }
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? -> if (uri != null) previewImageUri = uri else Log.d("ImagePicker", "No image selected") }


    // --- Audio Playback Logic ---
    fun formatTimeMillis(millis: Long): String { if (millis < 0) return "00:00"; val minutes = TimeUnit.MILLISECONDS.toMinutes(millis); val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(minutes); return String.format("%02d:%02d", minutes, seconds) }
    val playbackScope = rememberCoroutineScope(); var playbackJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun playAudio(url: String, messageId: String) { if (currentlyPlayingMessageId == messageId) { playbackJob?.cancel(); mediaPlayer.stop(); mediaPlayer.reset(); currentlyPlayingMessageId = null; currentAudioPosition = 0L; currentAudioDuration = 0L; return }; try { playbackJob?.cancel(); if (mediaPlayer.isPlaying) mediaPlayer.stop(); mediaPlayer.reset(); mediaPlayer.setDataSource(url); mediaPlayer.prepareAsync(); mediaPlayer.setOnPreparedListener { mp -> currentAudioDuration = if(mp.duration > 0) mp.duration.toLong() else 0L; currentAudioPosition = 0L; mp.start(); currentlyPlayingMessageId = messageId; playbackJob = playbackScope.launch { while (mp.isPlaying && currentlyPlayingMessageId == messageId) { try { currentAudioPosition = mp.currentPosition.toLong() } catch (e: IllegalStateException) { Log.w("MediaPlayer", "Failed to get current position."); break }; delay(100) }; if(currentlyPlayingMessageId == messageId && !mp.isPlaying) { try { currentAudioPosition = mp.currentPosition.toLong() } catch (e: IllegalStateException) { Log.w("MediaPlayer", "Failed to get final position."); currentAudioPosition = currentAudioDuration } } } }; mediaPlayer.setOnCompletionListener { mp -> playbackJob?.cancel(); if (currentlyPlayingMessageId == messageId) { currentlyPlayingMessageId = null; currentAudioPosition = 0L; currentAudioDuration = 0L; }; try { mp.reset() } catch (e: Exception) { Log.e("MediaPlayer", "Error resetting on completion", e) } }; mediaPlayer.setOnErrorListener { mp, _, _ -> Log.e("MediaPlayer", "Error playing audio"); Toast.makeText(context, "Error playing audio", Toast.LENGTH_SHORT).show(); playbackJob?.cancel(); if (currentlyPlayingMessageId == messageId) { currentlyPlayingMessageId = null; currentAudioPosition = 0L; currentAudioDuration = 0L; }; try { mp.reset() } catch (e: Exception) { Log.e("MediaPlayer", "Error resetting on error", e) }; true } } catch (e: Exception) { Log.e("MediaPlayer", "Playback setup failed", e); Toast.makeText(context, "Cannot play audio", Toast.LENGTH_SHORT).show(); playbackJob?.cancel(); if (currentlyPlayingMessageId == messageId) { currentlyPlayingMessageId = null; currentAudioPosition = 0L; currentAudioDuration = 0L; }; try { mediaPlayer.reset() } catch (e: Exception) { Log.e("MediaPlayer", "Error resetting after setup failure", e) } } }
    fun stopAudio() { playbackJob?.cancel(); try { if (mediaPlayer.isPlaying) mediaPlayer.stop(); mediaPlayer.reset() } catch (e: IllegalStateException) { Log.e("MediaPlayer", "Error stopping/resetting MediaPlayer", e) }; currentlyPlayingMessageId = null; currentAudioPosition = 0L; currentAudioDuration = 0L }
    DisposableEffect(Unit) { onDispose { playbackJob?.cancel(); try { mediaPlayer.release() } catch (e: Exception) { Log.e("MediaPlayer", "Error releasing MediaPlayer", e) } } }


    // --- Effects ---
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) { val targetIndex = (messages.size - 1).coerceAtLeast(0); if (targetIndex < listState.layoutInfo.totalItemsCount) listState.animateScrollToItem(index = targetIndex); else if (listState.layoutInfo.totalItemsCount > 0) listState.animateScrollToItem(index = listState.layoutInfo.totalItemsCount - 1) } }

    // --- UI Structure ---
    Scaffold(
        topBar = { TopAppBar( title = { Text(partnerName, maxLines = 1, overflow = TextOverflow.Ellipsis) }, navigationIcon = { IconButton(onClick = { stopAudio(); navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } } ) }
    ) { paddingValues ->
        Column( modifier = Modifier.fillMaxSize().padding(paddingValues).imePadding() ) {
            if (isUploadingImage || isUploadingAudio || isFetchingLocation) { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            error?.let { Text(text = "Error: $it", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }

            LazyColumn( state = listState, modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp), contentPadding = PaddingValues(vertical = 8.dp) ) {
                items(messages, key = { it.messageId }) { message ->
                    val isCurrentlyPlaying = message.messageId == currentlyPlayingMessageId
                    val displayDuration = if (isCurrentlyPlaying) currentAudioDuration else (message.audioDurationMillis ?: 0L)
                    val displayPosition = if (isCurrentlyPlaying) currentAudioPosition else 0L
                    MessageBubble( message = message, isCurrentUserMessage = message.senderId == currentUserUid, isPlaying = isCurrentlyPlaying, currentPositionMillis = displayPosition, totalDurationMillis = displayDuration, formatTime = ::formatTimeMillis, onImageClick = { imageUrl -> imageToShowFullScreen = imageUrl }, onPlayAudioClick = { audioUrl -> playAudio(audioUrl, message.messageId) } )
                }
            }

            if (previewImageUri != null) {
                ImagePreviewArea( uri = previewImageUri!!, onSendClick = { viewModel.sendImageMessage(previewImageUri!!, context.contentResolver); previewImageUri = null }, onCancelClick = { previewImageUri = null } )
            } else {
                MessageInput( text = messageText, onTextChanged = { messageText = it }, onSendClick = { if (messageText.isNotBlank()) { viewModel.sendMessage(messageText); messageText = "" } }, onAttachImageClick = { if (hasImagePermission) { imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } else { imagePermissionLauncher.launch(imagePermission) } }, isRecording = isRecording, onRecordStart = { if (hasAudioPermission) { viewModel.startRecording() } else { audioPermissionLauncher.launch(audioPermission) } }, onRecordStop = { viewModel.stopRecordingAndSend() }, onSendLocationClick = { if (hasLocationPermission) { viewModel.sendCurrentLocation() } else { locationPermissionLauncher.launch(locationPermissions) } }, isFetchingLocation = isFetchingLocation )
            }
        }
    }

    if (imageToShowFullScreen != null) { FullScreenImageDialog(imageUrl = imageToShowFullScreen!!, onDismiss = { imageToShowFullScreen = null }) }
}

// --- Composable for displaying a single message bubble ---
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = if (isCurrentUserMessage) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = if (isCurrentUserMessage) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.padding(start = if (isCurrentUserMessage) 64.dp else 0.dp, end = if (isCurrentUserMessage) 0.dp else 64.dp),
            tonalElevation = 1.dp
        ) {
            val contentColor = if (isCurrentUserMessage) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer

            when (message.messageType) {
                MessageType.TEXT -> { /* ... Text unchanged ... */
                    Text( text = message.text ?: "", modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = contentColor )
                }
                MessageType.IMAGE -> { /* ... Image unchanged ... */
                    if (message.imageUrl != null) { AsyncImage( model = ImageRequest.Builder(context).data(message.imageUrl).crossfade(true).build(), contentDescription = "Sent image", modifier = Modifier.sizeIn(maxWidth = 200.dp, maxHeight = 250.dp).padding(4.dp).clickable { onImageClick(message.imageUrl) }, contentScale = ContentScale.Fit ) } else { Text("[Image unavailable]", modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = contentColor) }
                }
                MessageType.LOCATION -> {
                    // **MODIFIED:** Display Static Map Image or Fallback Text
                    Column( // Use Column to stack map image and optional name
                        modifier = Modifier
                            .clickable(enabled = message.location != null) {
                                // Intent to open in Maps app remains the same
                                message.location?.let { geoPoint ->
                                    val lat = geoPoint.latitude
                                    val lon = geoPoint.longitude
                                    val gmmIntentUri = Uri.parse("geo:$lat,$lon?q=$lat,$lon(${message.locationName ?: "Shared Location"})")
                                    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                                    mapIntent.setPackage("com.google.android.apps.maps")
                                    try { context.startActivity(mapIntent) }
                                    catch (e: Exception) {
                                        Log.e("MapLink", "Failed to open Google Maps", e)
                                        val genericMapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                                        try { context.startActivity(genericMapIntent) }
                                        catch (e2: Exception) { Log.e("MapLink", "Failed to open any map app", e2) }
                                    }
                                }
                            }
                            .padding(4.dp) // Add padding around the map/text
                    ) {
                        if (message.staticMapUrl != null) {
                            // Display the Static Map using AsyncImage
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(message.staticMapUrl) // Load the map URL
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Location map preview: ${message.locationName ?: "Shared Location"}",
                                modifier = Modifier
                                    .fillMaxWidth() // Let image determine width based on aspect ratio
                                    .height(120.dp) // Set a fixed height for the map preview
                                    .clip(MaterialTheme.shapes.small), // Clip corners slightly
                                contentScale = ContentScale.Crop // Crop to fit the bounds
                            )
                        }

                        // Always display the location name (or fallback) below the map/placeholder
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.LocationOn,
                                contentDescription = null, // Decorative
                                tint = contentColor.copy(alpha = 0.8f), // Slightly faded icon
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = message.locationName ?: "[Shared Location]", // Show name or generic text
                                color = contentColor,
                                style = MaterialTheme.typography.bodySmall, // Use smaller style for name
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Fallback text if staticMapUrl is null (e.g., for older messages or errors)
                        if (message.staticMapUrl == null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = message.location?.let { "Lat: ${"%.4f".format(it.latitude)}, Lon: ${"%.4f".format(it.longitude)}" } ?: "[Location unavailable]",
                                color = contentColor.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
                MessageType.AUDIO -> { /* ... Audio unchanged ... */
                    Column( modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).widthIn(min = 100.dp, max = 250.dp).clickable(enabled = message.audioUrl != null) { message.audioUrl?.let { onPlayAudioClick(it) } } ) { Row( modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically ) { Icon( imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (isPlaying) "Pause audio" else "Play audio", tint = contentColor ); Spacer(modifier = Modifier.width(8.dp)); LinearProgressIndicator( progress = { if (totalDurationMillis > 0) currentPositionMillis.toFloat() / totalDurationMillis.toFloat() else 0f }, modifier = Modifier.weight(1f).height(6.dp), strokeCap = StrokeCap.Round, trackColor = if(isPlaying) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), color = if(isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) ) }; Spacer(modifier = Modifier.height(4.dp)); Text( text = "${formatTime(currentPositionMillis)} / ${formatTime(totalDurationMillis)}", fontSize = 12.sp, color = contentColor.copy(alpha = 0.7f), modifier = Modifier.align(Alignment.End) ) }
                }
                null -> { /* ... Unsupported unchanged ... */
                    Text("[Unsupported message]", modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = contentColor)
                }
            }
        }
    }
}

// --- MessageInput Composable --- (Unchanged)
@Composable
fun MessageInput( /* ... */ text: String, onTextChanged: (String) -> Unit, onSendClick: () -> Unit, onAttachImageClick: () -> Unit, isRecording: Boolean, onRecordStart: () -> Unit, onRecordStop: () -> Unit, onSendLocationClick: () -> Unit, isFetchingLocation: Boolean ) { /* ... implementation unchanged ... */
    Surface(shadowElevation = 4.dp, tonalElevation = 1.dp) { Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { if (isRecording) { Icon(imageVector = Icons.Filled.Mic, contentDescription = "Recording...", tint = Color.Red, modifier = Modifier.padding(horizontal = 12.dp)); Text(text = "Recording...", modifier = Modifier.weight(1f).padding(horizontal = 8.dp)); IconButton(onClick = onRecordStop) { Icon(Icons.Filled.Stop, contentDescription = "Stop Recording", tint = MaterialTheme.colorScheme.primary) } } else { IconButton(onClick = onAttachImageClick) { Icon(Icons.Filled.AddPhotoAlternate, contentDescription = "Attach Image") }; IconButton( onClick = onSendLocationClick, enabled = !isFetchingLocation ) { if (isFetchingLocation) { CircularProgressIndicator( modifier = Modifier.size(24.dp), strokeWidth = 2.dp ) } else { Icon(Icons.Filled.LocationOn, contentDescription = "Send Location") } }; OutlinedTextField(value = text, onValueChange = onTextChanged, modifier = Modifier.weight(1f), placeholder = { Text("Type a message...") }, shape = MaterialTheme.shapes.medium, colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent), maxLines = 5); Spacer(modifier = Modifier.width(4.dp)); if (text.isNotBlank()) { IconButton(onClick = onSendClick, enabled = true) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send message", tint = MaterialTheme.colorScheme.primary) } } else { IconButton(onClick = onRecordStart) { Icon(Icons.Filled.Mic, contentDescription = "Record Voice Message") } } } } }
}

// --- ImagePreviewArea Composable --- (Unchanged)
@Composable
fun ImagePreviewArea( /* ... */ uri: Uri, onSendClick: () -> Unit, onCancelClick: () -> Unit) { /* ... implementation unchanged ... */
    Surface(shadowElevation = 4.dp, tonalElevation = 1.dp) { Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onCancelClick) { Icon(Icons.Filled.Cancel, contentDescription = "Cancel Image Selection") }; Spacer(modifier = Modifier.width(8.dp)); AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(uri).crossfade(true).build(), contentDescription = "Image Preview", modifier = Modifier.size(64.dp).background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small), contentScale = ContentScale.Crop); Spacer(modifier = Modifier.weight(1f)); IconButton(onClick = onSendClick) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Image", tint = MaterialTheme.colorScheme.primary) } } }
}

// --- FullScreenImageDialog Composable --- (Unchanged)
@Composable
fun FullScreenImageDialog( /* ... */ imageUrl: String, onDismiss: () -> Unit) { /* ... implementation unchanged ... */
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) { Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss), contentAlignment = Alignment.Center) { AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(imageUrl).crossfade(true).build(), contentDescription = "Full screen image", modifier = Modifier.fillMaxWidth().padding(16.dp), contentScale = ContentScale.Fit); IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) { Icon(Icons.Filled.Close, contentDescription = "Close full screen image", tint = Color.White) } } }
}
package com.zhenbang.otw.ui.viewmodel

import android.Manifest // Import Manifest for permission check
import android.annotation.SuppressLint // For suppressing location permission check lint warning
import android.app.Application
import android.content.ContentResolver
import android.content.pm.PackageManager // Import PackageManager
import android.location.Address // Import Address
import android.location.Geocoder // <-- Import Geocoder
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Looper // Import Looper for location request
import android.util.Log
import androidx.core.app.ActivityCompat // Import ActivityCompat
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.* // Import LocationServices and related classes
import com.google.firebase.Timestamp // Make sure Timestamp is imported
import com.google.firebase.firestore.FieldValue // For serverTimestamp() used in updateChatMetadata
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException // Explicit import for error checking
import com.google.firebase.firestore.GeoPoint // Import GeoPoint
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions // For merge()
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.storage.FirebaseStorage
import com.zhenbang.otw.data.AuthRepository
import com.zhenbang.otw.data.FirebaseAuthRepository
import com.zhenbang.otw.data.UserProfile
// import com.zhenbang.otw.BuildConfig // <-- Import BuildConfig if API key is stored there
import com.zhenbang.otw.messagemodel.ChatMessage // Ensure this has the necessary fields
import com.zhenbang.otw.messagemodel.MessageType
import kotlinx.coroutines.Dispatchers // Import Dispatchers for background work
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext // Import withContext
import java.io.File
import java.io.IOException
import java.net.URLEncoder // Import URL Encoder
import java.util.*
import java.util.concurrent.TimeUnit // For location request timeout

class MessagingViewModel(
    application: Application,
    private val currentUserUid: String,
    private val otherUserUid: String,
    private val authRepository: AuthRepository
) : AndroidViewModel(application) {

    private val TAG = "MessagingViewModel" // Define the TAG for logging

    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val chatDocId = generateChatId(currentUserUid, otherUserUid)

    // References
    private val chatDocRef = db.collection("chats").document(chatDocId)
    private val messagesCollectionRef = chatDocRef.collection("messages")
    private val chatImagesStorageRef = storage.reference.child("chat_images").child(chatDocId)
    private val chatAudioStorageRef = storage.reference.child("chat_audio").child(chatDocId)

    // Location services client
    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(application)
    private var locationCallback: LocationCallback? = null

    // --- StateFlows ---
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    private val _partnerName = MutableStateFlow("User...")
    val partnerName: StateFlow<String> = _partnerName.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _isUploadingImage = MutableStateFlow(false)
    val isUploadingImage: StateFlow<Boolean> = _isUploadingImage.asStateFlow()
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()
    private val _isUploadingAudio = MutableStateFlow(false)
    val isUploadingAudio: StateFlow<Boolean> = _isUploadingAudio.asStateFlow()
    private val _isFetchingLocation = MutableStateFlow(false)
    val isFetchingLocation: StateFlow<Boolean> = _isFetchingLocation.asStateFlow()

    // --- StateFlows for Selection ---
    private val _selectedMessageForReply = MutableStateFlow<ChatMessage?>(null)
    val selectedMessageForReply: StateFlow<ChatMessage?> = _selectedMessageForReply.asStateFlow()

    private val _editingMessage = MutableStateFlow<ChatMessage?>(null)
    val editingMessage: StateFlow<ChatMessage?> = _editingMessage.asStateFlow()

    private val _partnerProfile = MutableStateFlow<UserProfile?>(null) // Store full partner profile
    val partnerProfile: StateFlow<UserProfile?> = _partnerProfile.asStateFlow()

    private val _currentUserProfile = MutableStateFlow<UserProfile?>(null)

    private val _isChatBlocked = MutableStateFlow(false) // Default to false
    val isChatBlocked: StateFlow<Boolean> = _isChatBlocked.asStateFlow()

    // --- MediaRecorder ---
    private var mediaRecorder: MediaRecorder? = null
    private var currentAudioFilePath: String? = null

    // **IMPORTANT:** Replace with secure API key retrieval
    private val MAPS_API_KEY = "YOUR_STATIC_MAPS_API_KEY" // <-- REPLACE THIS

    // --- State for Preview Update Logic ---
    private var lastUpdatedPreviewMessageId: String? = null // <-- ADDED: Tracks the last message used for preview

    init {
        Log.d("MessagingViewModel", "Initializing with currentUserUid: [$currentUserUid], otherUserUid: [$otherUserUid]")
        if (currentUserUid.isBlank() || otherUserUid.isBlank()) {
            Log.e("MessagingViewModel", "CRITICAL: One or both UIDs are blank!")
            _error.value = "Cannot initialize chat: Invalid user IDs."
        } else {
            viewModelScope.launch {
                fetchCurrentUserProfile()
                fetchPartnerProfile()
                val chatSetupSuccess = ensureChatDocumentExists()
                if (chatSetupSuccess) {
                    Log.d("MessagingViewModel", "Chat document confirmed/created.")
                    fetchPartnerName()
                    listenForMessages() // This will now handle initial preview setting too
                } else {
                    Log.e("MessagingViewModel", "Initialization failed: Could not ensure chat document exists.")
                }
            }
        }
    }

    private fun fetchCurrentUserProfile() {
        viewModelScope.launch {
            val result = authRepository.getUserProfile(currentUserUid)
            if (result.isSuccess) {
                _currentUserProfile.value = result.getOrNull()
            } else {
                Log.e("MessagingViewModel", "Failed to fetch current user profile", result.exceptionOrNull())
                // Handle error - maybe prevent sending?
            }
        }
    }

    private fun fetchPartnerProfile() {
        viewModelScope.launch {
            val result = authRepository.getUserProfile(otherUserUid)
            if (result.isSuccess) {
                _partnerProfile.value = result.getOrNull()
            } else {
                Log.e("MessagingViewModel", "Failed to fetch partner profile", result.exceptionOrNull())
                _partnerProfile.value = null // Set to null on error
                // Handle error - maybe prevent sending?
            }
        }
    }

    private fun observeProfileUpdates() {
        viewModelScope.launch {
            // Combine the flows of both profiles
            combine(_currentUserProfile, _partnerProfile) { currentUser, partnerUser ->
                // Calculate block status whenever either profile updates
                val iBlockedPartner = currentUser?.blockedUserIds?.contains(otherUserUid) == true
                val partnerBlockedMe = partnerUser?.blockedUserIds?.contains(currentUserUid) == true
                iBlockedPartner || partnerBlockedMe // Result is true if either block exists
            }
                .distinctUntilChanged() // Only emit when the block status actually changes
                .collect { isBlocked ->
                    Log.d("MessagingViewModel", "Block status updated: $isBlocked")
                    _isChatBlocked.value = isBlocked // Update the state flow
                }
        }
    }

    override fun onCleared() {
        stopRecordingInternal(deleteFile = true)
        removeLocationUpdates()
        super.onCleared()
    }

    // --- Helper Functions ---
    private fun generateChatId(uid1: String, uid2: String): String {
        return if (uid1 < uid2) "${uid1}_${uid2}" else "${uid2}_${uid1}"
    }

    private fun createAudioFilePath(): String? {
        val context = getApplication<Application>().applicationContext
        val audioDir = File(context.cacheDir, "audio_recordings")
        if (!audioDir.exists()) audioDir.mkdirs()
        val fileName = "${UUID.randomUUID()}.3gp"
        val audioFile = File(audioDir, fileName)
        return try {
            audioFile.createNewFile(); audioFile.absolutePath
        } catch (e: IOException) {
            Log.e("MessagingViewModel", "Failed to create audio file", e); _error.value = "Failed to prepare for recording."; null
        }
    }

    // --- Denormalization Helper ---
    // This function remains the same, it's called by the listener now.
    private suspend fun updateChatMetadata(preview: String?, timestamp: Any?) {
        val chatUpdateData = mapOf(
            "participants" to listOf(currentUserUid, otherUserUid), // Keep participants updated just in case
            "lastMessagePreview" to preview,
            "lastMessageTimestamp" to timestamp
        )
        try {
            // Use merge to avoid overwriting other potential chat fields
            chatDocRef.set(chatUpdateData, SetOptions.merge()).await()
            Log.d("MessagingViewModel", "Chat metadata updated via listener/send. Preview: $preview, Timestamp: $timestamp")
        } catch (e: Exception) {
            if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                Log.e("MessagingViewModel", "PERMISSION_DENIED updating chat metadata for $chatDocId. Check Firestore Rules!", e)
                //_error.value = "Error syncing chat status (Permissions)." // Potentially less intrusive error message
            } else {
                Log.e("MessagingViewModel", "Failed to update chat metadata for $chatDocId", e)
                // Avoid overwriting specific user action errors
            }
        }
    }

    // --- Recording Logic (Unchanged) ---
    fun startRecording() {
        if (_isRecording.value) { Log.w("MessagingViewModel", "Already recording."); return }
        currentAudioFilePath = createAudioFilePath() ?: return
        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(getApplication()) else @Suppress("DEPRECATION") MediaRecorder()
        mediaRecorder?.apply {
            try {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(currentAudioFilePath)
                prepare()
                start()
                _isRecording.value = true; _error.value = null
                Log.d("MessagingViewModel", "Recording started: $currentAudioFilePath")
            } catch (e: Exception) {
                Log.e("MessagingViewModel", "MediaRecorder setup/start failed", e); _error.value = "Recording failed: ${e.localizedMessage}"; stopRecordingInternal(deleteFile = true)
            }
        }
    }

    fun stopRecordingAndSend() {
        if (!_isRecording.value) return
        val filePath = currentAudioFilePath
        stopRecordingInternal(deleteFile = false) // Stop recording but keep the file for upload
        if (filePath != null) {
            val audioFile = File(filePath)
            if (audioFile.exists() && audioFile.length() > 0) {
                val durationMillis = getAudioDuration(filePath)
                Log.d("MessagingViewModel", "Recording stopped. File: ${audioFile.toUri()}, Duration: $durationMillis ms")
                uploadAudioFile(audioFile.toUri(), filePath, durationMillis) // Upload initiates message send
            } else {
                Log.e("MessagingViewModel", "Audio file missing/empty: $filePath"); _error.value = "Failed to save recording."
            }
        } else {
            Log.e("MessagingViewModel", "Audio file path null after stop."); _error.value = "Failed to save recording."
        }
        currentAudioFilePath = null // Clear path after attempting upload
    }

    private fun getAudioDuration(filePath: String): Long? {
        if (!File(filePath).exists()) return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(filePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationStr?.toLongOrNull()
        } catch (e: Exception) {
            Log.e("MessagingViewModel", "Failed to get audio duration for $filePath", e)
            null
        } finally {
            // Ensure release happens even if conversion fails or exceptions occur
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun stopRecordingInternal(deleteFile: Boolean) {
        mediaRecorder?.let {
            try {
                // Check recording state before stopping to avoid crash if already stopped
                if (_isRecording.value) it.stop()
            } catch (e: RuntimeException) { // Catch specific exception on stop if known, otherwise broad Exception
                Log.e("MessagingViewModel", "MediaRecorder stop threw exception", e)
            } catch (e: Exception) {
                Log.e("MessagingViewModel", "MediaRecorder stop general exception", e)
            }
            finally {
                try { it.reset() } catch (e: Exception) { Log.e("MessagingViewModel", "MediaRecorder reset failed", e) }
                try { it.release() } catch (e: Exception) { Log.e("MessagingViewModel", "MediaRecorder release failed", e) }
                mediaRecorder = null
                _isRecording.value = false // Ensure state is updated
                Log.d("MessagingViewModel", "MediaRecorder stopped/released.")
            }
        } ?: run { _isRecording.value = false } // Ensure state is false if recorder was already null

        if (deleteFile && currentAudioFilePath != null) {
            try {
                val file = File(currentAudioFilePath!!)
                if (file.exists()) {
                    if (file.delete()) Log.d("MessagingViewModel", "Deleted temp audio file: $currentAudioFilePath")
                    else Log.w("MessagingViewModel", "Failed to delete temp audio file: $currentAudioFilePath")
                }
            } catch (e: Exception) { Log.e("MessagingViewModel", "Error deleting temp audio file", e) }
            currentAudioFilePath = null // Clear path after deletion attempt
        }
    }

    // --- Location Logic (Unchanged) ---
    @SuppressLint("MissingPermission")
    fun sendCurrentLocation() {
        val context = getApplication<Application>().applicationContext
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            _error.value = "Location permission not granted."; return
        }
        if (MAPS_API_KEY.startsWith("YOUR_") || MAPS_API_KEY.length < 20) {
            _error.value = "Map feature not configured."; Log.e("MessagingViewModel", "Maps API Key invalid."); return
        }

        _isFetchingLocation.value = true; _error.value = null; Log.d("MessagingViewModel", "Requesting location...")
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, TimeUnit.SECONDS.toMillis(15)).setMaxUpdates(1).build()

        removeLocationUpdates() // Ensure no previous callback is active

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation
                removeLocationUpdates() // Got result or null, remove callback
                if (location != null) {
                    Log.d("MessagingViewModel", "Location received: Lat ${location.latitude}, Lon ${location.longitude}")
                    val geoPoint = GeoPoint(location.latitude, location.longitude)
                    // Launch coroutine to handle geocoding and sending
                    viewModelScope.launch { createAndSendLocationMessage(geoPoint) }
                } else {
                    Log.e("MessagingViewModel", "Location result null."); _error.value = "Failed to get location."; _isFetchingLocation.value = false
                }
            }
            override fun onLocationAvailability(availability: LocationAvailability) {
                if (!availability.isLocationAvailable) {
                    Log.e("MessagingViewModel", "Location not available."); _error.value = "Location unavailable."; _isFetchingLocation.value = false; removeLocationUpdates()
                }
            }
        }
        try { fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback!!, Looper.getMainLooper()) }
        catch (e: SecurityException) { Log.e("MessagingViewModel", "Location permission error", e); _error.value = "Location permission error."; _isFetchingLocation.value = false; removeLocationUpdates() }
        catch (e: Exception) { Log.e("MessagingViewModel", "Location request error", e); _error.value = "Could not get location: ${e.localizedMessage}"; _isFetchingLocation.value = false; removeLocationUpdates() }
    }

    private fun removeLocationUpdates() {
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it); locationCallback = null; Log.d("MessagingViewModel", "Location updates removed.") }
    }

    private suspend fun createAndSendLocationMessage(geoPoint: GeoPoint) {
        // Note: _isFetchingLocation is set true before calling this
        val replyInfo = _selectedMessageForReply.value // Capture reply state
        var fetchedLocationName: String? = "Shared Location"
        val context = getApplication<Application>().applicationContext
        val repliedToSenderName = replyInfo?.senderId?.let { getDisplayName(it) }

        // Reverse Geocoding in background IO thread
        withContext(Dispatchers.IO) {
            if (Geocoder.isPresent()) {
                val geocoder = Geocoder(context, Locale.getDefault())
                try {
                    // Handle potential SDK differences for getFromLocation
                    val addresses: List<Address>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        // Android 13+ provides a callback mechanism, but for simplicity,
                        // let's stick with the potentially blocking deprecated version for now.
                        // If targeting 33+, consider implementing the async version.
                        @Suppress("DEPRECATION") geocoder.getFromLocation(geoPoint.latitude, geoPoint.longitude, 1)
                    } else {
                        @Suppress("DEPRECATION") geocoder.getFromLocation(geoPoint.latitude, geoPoint.longitude, 1)
                    }

                    addresses?.firstOrNull()?.let { addr ->
                        // Construct a readable address string
                        fetchedLocationName = listOfNotNull(addr.featureName, addr.thoroughfare, addr.locality)
                            .joinToString(", ").ifEmpty { // Fallback if main parts are null
                                listOfNotNull(addr.subLocality, addr.adminArea).joinToString(", ").ifEmpty { "Nearby Location" }
                            }
                        Log.d("Geocoder", "Fetched address: $fetchedLocationName")
                    } ?: Log.w("Geocoder", "No address found for location.")
                } catch (e: IOException) { Log.e("Geocoder", "Geocoder network I/O error", e)
                } catch (e: Exception) { Log.e("Geocoder", "Geocoder general error", e) } // Catch other potential issues
            } else { Log.w("Geocoder", "Geocoder service not present on device.") }
        } // End of withContext(Dispatchers.IO)

        val staticMapUrl = generateStaticMapUrl(geoPoint)
        val newMessage = ChatMessage(
            senderId = currentUserUid, receiverId = otherUserUid,
            location = geoPoint, locationName = fetchedLocationName, staticMapUrl = staticMapUrl,
            messageType = MessageType.LOCATION, timestamp = null, // Server sets timestamp
            // Reply fields
            repliedToMessageId = replyInfo?.messageId, repliedToSenderName = repliedToSenderName,
            repliedToPreview = replyInfo?.let { generatePreview(it) }, repliedToType = replyInfo?.messageType
        )

        try {
            val messageRef = messagesCollectionRef.add(newMessage).await()
            Log.d("MessagingViewModel", "Location message sent. ID: ${messageRef.id}. ReplyTo: ${replyInfo?.messageId}")
            _error.value = null
            // Preview updated by listener now, no need to call updateChatMetadata here
            // val previewText = "[Location] ${fetchedLocationName ?: ""}".trim()
            // updateChatMetadata(previewText, FieldValue.serverTimestamp()) // <-- REMOVED
            cancelReply() // Clear reply state
        } catch (e: Exception) {
            Log.e("MessagingViewModel", "Error sending location", e); _error.value = "Failed to send location: ${e.localizedMessage}"
        } finally {
            _isFetchingLocation.value = false // Stop loading indicator regardless of success/failure
        }
    }


    private fun generateStaticMapUrl(geoPoint: GeoPoint): String? {
        if (MAPS_API_KEY.startsWith("YOUR_") || MAPS_API_KEY.length < 20) {
            Log.e("StaticMap", "API Key missing/invalid."); return null
        }
        return try {
            val lat = geoPoint.latitude; val lon = geoPoint.longitude
            val marker = URLEncoder.encode("color:red|label:P|$lat,$lon", "UTF-8")
            // Consider making size/zoom configurable if needed
            "https://maps.googleapis.com/maps/api/staticmap?center=$lat,$lon&zoom=15&size=400x200&maptype=roadmap&markers=$marker&key=$MAPS_API_KEY"
        } catch (e: java.io.UnsupportedEncodingException) {
            Log.e("StaticMap", "Error encoding URL params (UTF-8 unsupported?!)", e); null // Should not happen
        } catch (e: Exception) {
            Log.e("StaticMap", "Error generating static map URL", e); null
        }
    }

    // --- Upload and Send Logic ---

    private fun uploadAudioFile(audioUri: Uri, localFilePath: String, durationMillis: Long?) {
        _isUploadingAudio.value = true; _error.value = null
        val replyInfo = _selectedMessageForReply.value

        viewModelScope.launch {
            val repliedToSenderName = replyInfo?.senderId?.let { getDisplayName(it) }
            try {
                val fileName = "${UUID.randomUUID()}.3gp"
                val audioRef = chatAudioStorageRef.child(fileName)
                Log.d("MessagingViewModel", "Starting audio upload: $fileName to ${audioRef.path}")
                audioRef.putFile(audioUri).await()
                val downloadUrl = audioRef.downloadUrl.await()
                Log.d("MessagingViewModel", "Audio uploaded: $downloadUrl")

                val newMessage = ChatMessage(
                    senderId = currentUserUid, receiverId = otherUserUid,
                    audioUrl = downloadUrl.toString(), messageType = MessageType.AUDIO,
                    audioDurationMillis = durationMillis, timestamp = null, // Server sets timestamp
                    // Reply fields
                    repliedToMessageId = replyInfo?.messageId, repliedToSenderName = repliedToSenderName,
                    repliedToPreview = replyInfo?.let { generatePreview(it) }, repliedToType = replyInfo?.messageType
                )
                val messageRef = messagesCollectionRef.add(newMessage).await()
                Log.d("MessagingViewModel", "Audio message metadata saved. ID: ${messageRef.id}. ReplyTo: ${replyInfo?.messageId}")
                // Preview updated by listener now, no need to call updateChatMetadata here
                // updateChatMetadata("[Audio]", FieldValue.serverTimestamp()) // <-- REMOVED
                cancelReply() // Clear reply state

                // Delete local file after successful upload and metadata save
                try { File(localFilePath).delete() } catch (e: Exception) { Log.w("MessagingViewModel", "Failed to delete local audio post-upload", e)}

            } catch (e: Exception) {
                Log.e("MessagingViewModel", "Error sending audio", e); _error.value = "Failed to send audio: ${e.localizedMessage}"
                // Attempt to delete local file even on failure to avoid clutter
                try { File(localFilePath).delete() } catch (_: Exception) {}
            } finally {
                _isUploadingAudio.value = false
            }
        }
    }

    fun sendImageMessage(imageUri: Uri, contentResolver: ContentResolver) {
        _isUploadingImage.value = true; _error.value = null
        val replyInfo = _selectedMessageForReply.value

        viewModelScope.launch {
            val repliedToSenderName = replyInfo?.senderId?.let { getDisplayName(it) }
            try {
                val fileName = "${UUID.randomUUID()}.jpg" // Consider more robust extension detection if needed
                val imageRef = chatImagesStorageRef.child(fileName)
                Log.d("MessagingViewModel", "Starting image upload: $fileName to ${imageRef.path}")
                imageRef.putFile(imageUri).await()
                val downloadUrl = imageRef.downloadUrl.await()
                Log.d("MessagingViewModel", "Image uploaded: $downloadUrl")

                val newMessage = ChatMessage(
                    senderId = currentUserUid, receiverId = otherUserUid,
                    imageUrl = downloadUrl.toString(), messageType = MessageType.IMAGE,
                    timestamp = null, // Server sets timestamp
                    // Reply fields
                    repliedToMessageId = replyInfo?.messageId, repliedToSenderName = repliedToSenderName,
                    repliedToPreview = replyInfo?.let { generatePreview(it) }, repliedToType = replyInfo?.messageType
                )

                val messageRef = messagesCollectionRef.add(newMessage).await()
                Log.d("MessagingViewModel", "Image message metadata saved. ID: ${messageRef.id}. ReplyTo: ${replyInfo?.messageId}")
                // Preview updated by listener now, no need to call updateChatMetadata here
                // updateChatMetadata("[Image]", FieldValue.serverTimestamp()) // <-- REMOVED
                cancelReply() // Clear reply state

            } catch (e: Exception) {
                Log.e("MessagingViewModel", "Error sending image", e); _error.value = "Failed to send image: ${e.localizedMessage}"
            } finally {
                _isUploadingImage.value = false
            }
        }
    }

    fun sendMessage(text: String) {
        if (_isChatBlocked.value) {
            Log.w(TAG, "Attempted to send message, but chat is blocked.")
            return
        }
        val trimmedText = text.trim()
        if (trimmedText.isBlank() && _selectedMessageForReply.value == null) return
        val replyInfo = _selectedMessageForReply.value // Capture reply state before coroutine

        viewModelScope.launch { // Launch coroutine for potential async name fetch
            val repliedToSenderName = replyInfo?.senderId?.let { getDisplayName(it) }

            val newMessage = ChatMessage(
                senderId = currentUserUid, receiverId = otherUserUid,
                text = trimmedText, messageType = MessageType.TEXT,
                timestamp = null, // Firestore sets this via serverTimestamp() implicitly on create usually
                // Reply fields
                repliedToMessageId = replyInfo?.messageId,
                repliedToSenderName = repliedToSenderName,
                repliedToPreview = replyInfo?.let { generatePreview(it) },
                repliedToType = replyInfo?.messageType
            )

            try {
                val messageRef = messagesCollectionRef.add(newMessage).await()
                Log.d("MessagingViewModel", "Text message sent. ID: ${messageRef.id}. ReplyTo: ${replyInfo?.messageId}")
                _error.value = null
                // Preview updated by listener now, no need to call updateChatMetadata here
                // updateChatMetadata(trimmedText, FieldValue.serverTimestamp()) // <-- REMOVED
                cancelReply() // Clear reply state after sending attempt
            } catch (e: Exception) {
                Log.e(TAG, "Error sending text message", e);
                if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    // Keep setting error for permission denied, as it confirms the block from backend
                    //_error.value = "Cannot send message. User is blocked or has blocked you."
                    Log.w(TAG, "Firestore PERMISSION_DENIED, forcing block status check/update.")
                    _isChatBlocked.value = true // Force UI state update
                } else {
                    _error.value = "Failed to send message: ${e.localizedMessage}" // Set other errors
                }            }
        }
    }

    // --- Functions for Edit/Delete/Reply Selection ---

    private suspend fun getDisplayName(userId: String): String {
        return when (userId) {
            currentUserUid -> "You" // Simple case for current user
            otherUserUid -> partnerName.value.takeIf { it != "User..." } ?: fetchUserName(otherUserUid) ?: "User..." // Use cached partner name if available
            else -> fetchUserName(userId) ?: "Unknown User" // Fetch for other cases (future proofing?)
        }
    }

    private suspend fun fetchUserName(userId: String): String? {
        if (userId.isBlank()) return null // Avoid query with blank ID
        return try {
            val doc = db.collection("users").document(userId).get().await()
            // Prioritize username, fallback to name, then return null if neither exist
            doc.getString("username") ?: doc.getString("displayName")
        } catch (e: Exception) {
            Log.e("MessagingViewModel", "Failed to fetch username for $userId", e)
            null // Return null on any exception during fetch
        }
    }

    private fun generatePreview(message: ChatMessage): String {
        // Add null check for text just in case
        val textPreview = message.text?.take(50)?.let { if (it.length == 50) "$it..." else it }
        return when (message.messageType) {
            MessageType.TEXT -> textPreview ?: "[Message]" // Fallback if text is null somehow
            MessageType.IMAGE -> "[Image]" + (textPreview?.let { "\n\"$it\"" } ?: "") // Add caption preview if text exists
            MessageType.AUDIO -> "[Audio Message]"
            MessageType.LOCATION -> message.locationName?.takeIf { it.isNotBlank() } ?: "[Location]" // Use locationName, fallback
            null -> "[Unsupported Message]" // Handle potential null type
        }
    }

    fun selectMessageForReply(message: ChatMessage) {
        // Consider adding a check !message.isDeleted if the field still exists and you want to prevent replying to "deleted" items
        _selectedMessageForReply.value = message
        _editingMessage.value = null // Cancel edit if replying
    }

    fun cancelReply() {
        _selectedMessageForReply.value = null
    }

    fun selectMessageForEdit(message: ChatMessage) {
        // Only allow editing own, non-deleted (if field exists), text messages
        // Add !message.isDeleted check if relevant
        if (message.senderId == currentUserUid && message.messageType == MessageType.TEXT /* && !message.isDeleted */ ) {
            _editingMessage.value = message
            _selectedMessageForReply.value = null // Cancel reply if editing
        } else {
            Log.w("MessagingViewModel", "Cannot edit message: Not owner or not text.") // Simplified log
            _error.value = "You can only edit your own text messages."
        }
    }

    fun cancelEdit() {
        _editingMessage.value = null
    }


    fun performEdit(newText: String) {
        val messageToEdit = _editingMessage.value ?: return
        // Add !messageToEdit.isDeleted check if relevant
        if (messageToEdit.senderId != currentUserUid || messageToEdit.messageId == null || messageToEdit.messageType != MessageType.TEXT /* || messageToEdit.isDeleted */) {
            _error.value = "Cannot edit this message."; cancelEdit(); return
        }

        val trimmedText = newText.trim()
        if (trimmedText.isBlank()) {
            _error.value = "Message cannot be empty."; return // Don't allow editing to empty
        }
        if (trimmedText == messageToEdit.text) {
            cancelEdit(); return // No change needed
        }

        viewModelScope.launch {
            try {
                // Ensure isEdited flag exists in ChatMessage data class
                val updates = mapOf("text" to trimmedText, "isEdited" to true)
                messagesCollectionRef.document(messageToEdit.messageId).update(updates).await()
                Log.d("MessagingViewModel", "Message ${messageToEdit.messageId} edited.")
                _error.value = null
                cancelEdit() // Exit editing mode on success

                // Preview update is handled by listener now, no need for specific logic here
                // val lastMsg = _messages.value.lastOrNull { !it.isDeleted }
                // if (lastMsg?.messageId == messageToEdit.messageId) {
                //     updateChatMetadata(trimmedText, lastMsg.timestamp ?: FieldValue.serverTimestamp())
                // }

            } catch (e: Exception) {
                Log.e("MessagingViewModel", "Error editing message ${messageToEdit.messageId}", e)
                _error.value = "Failed to edit message: ${e.localizedMessage}"
                // Optionally cancel edit mode on failure too, depending on desired UX
                // cancelEdit()
            }
        }
    }

    // --- Delete Function (Direct Delete) ---
    fun deleteMessage(message: ChatMessage) {
        // Ensure sender and messageId are valid
        if (message.senderId != currentUserUid || message.messageId == null) {
            _error.value = "You can only delete your own messages."; return
        }

        viewModelScope.launch {
            try {
                // Perform the direct delete
                messagesCollectionRef.document(message.messageId).delete().await()
                Log.d("MessagingViewModel", "Message ${message.messageId} deleted successfully.")
                _error.value = null
                // Preview update is handled by the listener reacting to the deletion

            } catch (e: Exception) {
                Log.e("MessagingViewModel", "Error deleting message ${message.messageId}", e)
                if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    _error.value = "Failed to delete: Permission denied. Check Rules."
                } else {
                    _error.value = "Failed to delete message: ${e.localizedMessage}"
                }
            }
        }
    }


    // --- Initialization and Listener ---
    private suspend fun ensureChatDocumentExists(): Boolean {
        try {
            val docSnapshot = chatDocRef.get().await()
            if (!docSnapshot.exists()) {
                Log.d("MessagingViewModel", "Chat document $chatDocId creating...")
                val initialChatData = mapOf(
                    "participants" to listOf(currentUserUid, otherUserUid),
                    "lastMessagePreview" to null, // Initialize as null
                    "lastMessageTimestamp" to FieldValue.serverTimestamp() // Use server timestamp for creation ordering
                )
                chatDocRef.set(initialChatData).await()
                Log.d("MessagingViewModel", "Chat document created.")
            } else {
                Log.d("MessagingViewModel", "Chat document $chatDocId exists.")
                // Optional: Verify/update participants if needed (handles edge cases where one user creates chat before other joins)
                val currentParticipants = docSnapshot.get("participants") as? List<*>
                if (currentParticipants == null || currentUserUid !in currentParticipants || otherUserUid !in currentParticipants) {
                    Log.w("MessagingViewModel", "Participants list incomplete or invalid in $chatDocId. Merging current users.")
                    chatDocRef.set(mapOf("participants" to listOf(currentUserUid, otherUserUid)), SetOptions.merge()).await()
                }
            }
            return true
        } catch (e: Exception) {
            Log.e("MessagingViewModel", "Error ensuring chat document $chatDocId exists", e)
            val errorMsg = if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                "Chat init failed: Check Firestore Rules for chat creation/read."
            } else { "Chat init failed: ${e.localizedMessage}" }
            _error.value = errorMsg
            return false // Indicate failure
        }
    }

    // --- MODIFIED LISTENER ---
    private fun listenForMessages() {
        messagesCollectionRef
            .orderBy("timestamp", Query.Direction.ASCENDING)
            // .limitToLast(100) // Consider adding limit for performance with large chats
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("MessagingViewModel", "Listen failed on messages collection.", e)
                    if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        _error.value = "Cannot load messages (Permissions). Check Rules."
                    } // Avoid overwriting specific action errors
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    val loadedMessages = snapshots.documents.mapNotNull { doc ->
                        try {
                            // Assumes ChatMessage class handles all fields correctly
                            doc.toObject<ChatMessage>()?.copy(messageId = doc.id)
                        } catch (convError: Exception) {
                            Log.e("MessagingViewModel", "Error converting Firestore document ${doc.id} to ChatMessage", convError)
                            null // Skip messages that fail conversion
                        }
                    }
                    _messages.value = loadedMessages // Update the UI list first
                    Log.d("MessagingViewModel", "Listener received ${loadedMessages.size} messages.")

                    // --- Update Last Message Preview based on current listener state ---
                    // Find the actual last message in the current list.
                    // If you still have an 'isDeleted' field for UI filtering, use:
                    // val lastValidMessage = loadedMessages.lastOrNull { !it.isDeleted }
                    // Otherwise, if direct delete means no 'isDeleted' field matters:
                    val lastValidMessage = loadedMessages.lastOrNull()

                    // Get the ID of the potential new last message (or null if list is empty)
                    val newLastMessageId = lastValidMessage?.messageId

                    // Check if the last message has actually changed since the last update we performed
                    if (newLastMessageId != lastUpdatedPreviewMessageId) {
                        Log.d("MessagingViewModel", "Last message changed (or initial/clear). Updating preview. Old ID: $lastUpdatedPreviewMessageId, New ID: $newLastMessageId")

                        // Generate the preview and get the timestamp for the chat document
                        val newPreview = lastValidMessage?.let { generatePreview(it) }
                        // Use the actual timestamp from the message, or null if no message
                        // Firestore Timestamps should be handled correctly by 'Any?' in updateChatMetadata
                        val newTimestamp = lastValidMessage?.timestamp

                        // Update the chat document metadata in a coroutine
                        viewModelScope.launch(Dispatchers.IO) { // Use IO dispatcher for Firestore write
                            updateChatMetadata(newPreview, newTimestamp)
                            // Update the tracking ID *after* the update attempt
                            // Switch back to Main thread only if necessary for UI updates triggered by this,
                            // but here we just update the tracking variable.
                            lastUpdatedPreviewMessageId = newLastMessageId
                        }
                    } else {
                        // Optional: Verbose log if last message ID didn't change
                        Log.v("MessagingViewModel", "Listener update, but last message ($lastUpdatedPreviewMessageId) hasn't changed. Skipping preview update.")
                    }
                    // --- End of Preview Update ---

                } else {
                    // Snapshot is null (e.g., initial load failed, permission issue after init, or chat truly empty)
                    Log.d("MessagingViewModel", "Messages snapshot received as null.");
                    _messages.value = emptyList() // Clear message list

                    // Check if we need to clear the preview because the list is now confirmed empty/inaccessible
                    if (lastUpdatedPreviewMessageId != null) {
                        Log.d("MessagingViewModel", "Messages snapshot null, clearing chat preview metadata.")
                        viewModelScope.launch(Dispatchers.IO) { // Use IO dispatcher
                            updateChatMetadata(null, null)
                            lastUpdatedPreviewMessageId = null // Mark preview as cleared
                        }
                    }
                }
            }
    }
    // --- END OF MODIFIED LISTENER ---


    private fun fetchPartnerName() {
        if (otherUserUid.isBlank()) { // Prevent query with blank ID
            _partnerName.value = "Invalid User"
            return
        }
        viewModelScope.launch {
            try {
                val fetchedName = fetchUserName(otherUserUid) // Use helper function
                _partnerName.value = fetchedName ?: "Unknown User" // Provide fallback
                if (fetchedName == null) {
                    Log.w("MessagingViewModel", "Partner user document $otherUserUid not found or has no name fields.")
                }
            } catch (e: Exception) {
                // Handle specific Firestore exceptions if needed
                if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    Log.e("MessagingViewModel", "PERMISSION_DENIED fetching partner name $otherUserUid.", e)
                    _partnerName.value = "User (Restricted)" // Indicate permission issue subtly
                    _error.value = "Cannot load partner name (Permissions)."
                } else {
                    Log.e("MessagingViewModel", "Error fetching partner name $otherUserUid", e)
                    _partnerName.value = "User..." // Generic error state
                    // Avoid setting _error here unless it's critical, listener might set it
                }
            }
        }
    }

    // --- Factory ---
    companion object {
        fun provideFactory(
            application: Application,
            currentUserUid: String,
            otherUserUid: String,
            authRepository: AuthRepository = FirebaseAuthRepository() // Provide default implementation
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(MessagingViewModel::class.java)) {
                    // Ensure UIDs are actually passed and not blank if possible
                    if (currentUserUid.isBlank() || otherUserUid.isBlank()) {
                        Log.e("ViewModelFactory", "Attempting to create MessagingViewModel with blank UIDs!")
                        // Depending on app structure, either throw or handle this state
                        // throw IllegalArgumentException("User UIDs cannot be blank")
                    }
                    return MessagingViewModel(application, currentUserUid, otherUserUid, authRepository) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    }
}
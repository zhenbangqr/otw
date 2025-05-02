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
import com.google.firebase.firestore.FieldValue // For serverTimestamp() used in updateChatMetadata
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint // Import GeoPoint
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions // For merge()
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.storage.FirebaseStorage
// import com.zhenbang.otw.BuildConfig // <-- Import BuildConfig if API key is stored there
import com.zhenbang.otw.messagemodel.ChatMessage // Ensure this has a @ServerTimestamp annotated timestamp field (Timestamp? or Date?) = null
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
    private val otherUserUid: String
) : AndroidViewModel(application) {

    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val chatDocId = generateChatId(currentUserUid, otherUserUid)

    // Reference to the main chat document (for denormalized data)
    private val chatDocRef = db.collection("chats").document(chatDocId)
    // Reference to the messages subcollection
    private val messagesCollectionRef = chatDocRef.collection("messages")

    // Storage references
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

    // --- MediaRecorder ---
    private var mediaRecorder: MediaRecorder? = null
    private var currentAudioFilePath: String? = null

    // **IMPORTANT:** Replace this with your actual secure way of getting the API key
    // For example, using BuildConfig: private val MAPS_API_KEY = BuildConfig.MAPS_API_KEY
    // Or inject it via Hilt/Dagger. NEVER hardcode it here.
    private val MAPS_API_KEY = "AIzaSyCppCCTlCgmdXPLpkrhCYHy2vXEdYsgY08" // <-- REPLACE THIS

    init {
        if (MAPS_API_KEY.startsWith("YOUR_")) { // Basic check if placeholder is still there
            Log.w("ViewModelSetup", "Static Maps API Key is not set!")
            // Consider setting an error state or disabling location feature
        }
        listenForMessages()
        fetchPartnerName()
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
        if (!audioDir.exists()) {
            audioDir.mkdirs()
        }
        val fileName = "${UUID.randomUUID()}.3gp"
        val audioFile = File(audioDir, fileName)
        return try {
            audioFile.createNewFile()
            audioFile.absolutePath
        } catch (e: IOException) {
            Log.e("MessagingViewModel", "Failed to create audio file", e)
            _error.value = "Failed to prepare for recording."
            null
        }
    }

    // --- Denormalization Helper ---
    // Updates the main chat document with last message info
    private suspend fun updateChatMetadata(preview: String, timestamp: Any) {
        val chatUpdateData = mapOf(
            "participants" to listOf(currentUserUid, otherUserUid), // Ensure this is always set/updated
            "lastMessagePreview" to preview,
            "lastMessageTimestamp" to timestamp // Use FieldValue.serverTimestamp() or actual Timestamp
        )
        try {
            // Use set with merge option to create the document if it doesn't exist,
            // or update existing fields without overwriting others.
            chatDocRef.set(chatUpdateData, SetOptions.merge()).await()
            Log.d("MessagingViewModel", "Chat metadata updated for $chatDocId")
        } catch (e: Exception) {
            if (e is com.google.firebase.firestore.FirebaseFirestoreException && e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                Log.e("MessagingViewModel", "PERMISSION_DENIED updating chat metadata for $chatDocId. Check Firestore Rules!", e)
                _error.value = "Error updating chat (Permissions). Check Rules." // Show specific error if needed
            } else {
                Log.e("MessagingViewModel", "Failed to update chat metadata for $chatDocId", e)
                // Optionally set an error state, but maybe non-critical
            }
        }
    }


    // --- Recording Logic ---
    fun startRecording() {
        if (_isRecording.value) { Log.w("MessagingViewModel", "Already recording."); return }

        currentAudioFilePath = createAudioFilePath()
        if (currentAudioFilePath == null) { Log.e("MessagingViewModel", "Cannot start recording, file path is null."); return }

        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(getApplication<Application>().applicationContext)
        } else {
            @Suppress("DEPRECATION") MediaRecorder()
        }

        mediaRecorder?.apply {
            try {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(currentAudioFilePath)
                prepare()
                start()
                _isRecording.value = true
                _error.value = null
                Log.d("MessagingViewModel", "Recording started to: $currentAudioFilePath")
            } catch (e: Exception) {
                Log.e("MessagingViewModel", "MediaRecorder setup/start failed", e)
                _error.value = "Recording failed to start: ${e.localizedMessage}"
                stopRecordingInternal(deleteFile = true) // Clean up failed recording
            }
        }
    }

    fun stopRecordingAndSend() {
        if (!_isRecording.value) { Log.w("MessagingViewModel", "Not recording."); return }

        val filePath = currentAudioFilePath
        stopRecordingInternal(deleteFile = false) // Stop recording but keep the file for upload

        if (filePath != null) {
            val audioFile = File(filePath)
            if (audioFile.exists() && audioFile.length() > 0) {
                val durationMillis = getAudioDuration(filePath)
                Log.d("MessagingViewModel", "Recording stopped. File: ${audioFile.toUri()}, Size: ${audioFile.length()} bytes, Duration: $durationMillis ms")
                uploadAudioFile(audioFile.toUri(), filePath, durationMillis)
            } else {
                Log.e("MessagingViewModel", "Audio file is missing or empty: $filePath")
                _error.value = "Failed to save recording."
            }
        } else {
            Log.e("MessagingViewModel", "Audio file path was null after stopping recording.");
            _error.value = "Failed to save recording."
        }
        currentAudioFilePath = null // Clear path after attempting send
    }

    private fun getAudioDuration(filePath: String): Long? {
        if (!File(filePath).exists()) return null // Add check if file exists
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(filePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release() // Ensure release happens
            durationStr?.toLongOrNull()
        } catch (e: Exception) {
            Log.e("MessagingViewModel", "Failed to get audio duration for $filePath", e)
            try { retriever.release() } catch (_: Exception) {} // Attempt release again in catch block
            null
        }
    }

    private fun stopRecordingInternal(deleteFile: Boolean) {
        if (mediaRecorder != null) {
            try {
                if (_isRecording.value) { // Only stop if actually recording
                    mediaRecorder?.stop()
                }
            } catch (e: IllegalStateException) {
                Log.e("MessagingViewModel", "MediaRecorder stop() failed - likely already stopped or not started", e)
            } catch (e: RuntimeException) {
                Log.e("MessagingViewModel", "MediaRecorder stop() failed with RuntimeException", e)
            } finally {
                try {
                    // Reset prepares the recorder for reuse, release frees resources
                    mediaRecorder?.reset()
                    mediaRecorder?.release()
                } catch (e: Exception) {
                    Log.e("MessagingViewModel", "Error resetting/releasing MediaRecorder", e)
                }
                mediaRecorder = null
                _isRecording.value = false // Update state *after* cleanup attempts
                Log.d("MessagingViewModel", "MediaRecorder stopped and released.")
            }
        } else {
            // If recorder is already null, just ensure state is false
            _isRecording.value = false
        }

        if (deleteFile && currentAudioFilePath != null) {
            try {
                val fileToDelete = File(currentAudioFilePath!!)
                if (fileToDelete.exists()) {
                    if (!fileToDelete.delete()) {
                        Log.w("MessagingViewModel", "Failed to delete temporary audio file: $currentAudioFilePath")
                    } else {
                        Log.d("MessagingViewModel", "Deleted temporary audio file: $currentAudioFilePath")
                    }
                }
            } catch (e: Exception) {
                Log.e("MessagingViewModel", "Error deleting temporary audio file: $currentAudioFilePath", e)
            }
            currentAudioFilePath = null // Clear path after deletion
        }
    }

    // --- Location Logic ---
    @SuppressLint("MissingPermission")
    fun sendCurrentLocation() {
        val context = getApplication<Application>().applicationContext
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            _error.value = "Location permission not granted."; Log.w("MessagingViewModel", "sendCurrentLocation called without permission."); return
        }
        // Check if API key is set
        if (MAPS_API_KEY.startsWith("YOUR_") || MAPS_API_KEY.length < 20) { // Basic check
            _error.value = "Map feature not configured."
            Log.e("MessagingViewModel", "Static Maps API Key is missing or invalid.")
            return
        }

        _isFetchingLocation.value = true; _error.value = null; Log.d("MessagingViewModel", "Requesting current location...")
        // Increased timeout and ensure only one update request
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, TimeUnit.SECONDS.toMillis(15))
            .setMaxUpdates(1)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation
                removeLocationUpdates() // Remove updates as soon as we get a result (or fail)
                if (location != null) {
                    Log.d("MessagingViewModel", "Location received: Lat ${location.latitude}, Lon ${location.longitude}")
                    val geoPoint = GeoPoint(location.latitude, location.longitude)
                    viewModelScope.launch { createAndSendLocationMessage(geoPoint) } // Launch suspend fun
                } else {
                    Log.e("MessagingViewModel", "Location result was null.")
                    _error.value = "Failed to get current location."
                    _isFetchingLocation.value = false
                }
                // Removed removeLocationUpdates() from here, moved to top of function
            }
            override fun onLocationAvailability(locationAvailability: LocationAvailability) {
                if (!locationAvailability.isLocationAvailable) {
                    Log.e("MessagingViewModel", "Location is not available.")
                    _error.value = "Location currently unavailable.";
                    _isFetchingLocation.value = false;
                    removeLocationUpdates() // Remove updates if location becomes unavailable
                }
            }
        }
        try { fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback!!, Looper.getMainLooper()) }
        catch (e: SecurityException) { Log.e("MessagingViewModel", "SecurityException during location request", e); _error.value = "Location permission error."; _isFetchingLocation.value = false; removeLocationUpdates() }
        catch (e: Exception) { Log.e("MessagingViewModel", "Exception during location request", e); _error.value = "Could not request location: ${e.localizedMessage}"; _isFetchingLocation.value = false; removeLocationUpdates() }
    }

    private fun removeLocationUpdates() {
        locationCallback?.let {
            Log.d("MessagingViewModel", "Removing location updates callback.")
            fusedLocationClient.removeLocationUpdates(it)
            locationCallback = null
        }
    }

    private suspend fun createAndSendLocationMessage(geoPoint: GeoPoint) {
        var fetchedLocationName: String? = "Shared Location" // Default name
        val context = getApplication<Application>().applicationContext

        // Get Location Name (Reverse Geocoding) in background thread
        withContext(Dispatchers.IO) {
            if (Geocoder.isPresent()) {
                val geocoder = Geocoder(context, Locale.getDefault()) // Specify locale
                try {
                    // Use the newer API for Android Tiramisu (API 33) and above if needed
                    val addresses: List<Address>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        // Requires a callback, simpler to stick with deprecated for now unless targeting 33+ strictly
                        geocoder.getFromLocation(geoPoint.latitude, geoPoint.longitude, 1)
                    } else {
                        @Suppress("DEPRECATION")
                        geocoder.getFromLocation(geoPoint.latitude, geoPoint.longitude, 1)
                    }

                    if (addresses != null && addresses.isNotEmpty()) {
                        val address = addresses[0]
                        // Build a more comprehensive name
                        fetchedLocationName = listOfNotNull(address.featureName, address.thoroughfare, address.locality)
                            .joinToString(", ")
                            .ifEmpty { "Nearby Location" } // Fallback if parts are null/empty
                        Log.d("Geocoder", "Fetched address: $fetchedLocationName")
                    } else {
                        Log.w("Geocoder", "No address found for location.")
                    }
                } catch (e: IOException) { Log.e("Geocoder", "Geocoder failed due to network or I/O error", e) }
                catch (e: IllegalArgumentException) { Log.e("Geocoder", "Geocoder failed due to invalid lat/lon", e)}
                catch (e: Exception) { Log.e("Geocoder", "Geocoder failed", e) } // Catch other potential errors
            } else { Log.w("Geocoder", "Geocoder not present on this device.") }
        }

        // Generate Static Map URL
        val staticMapUrl = generateStaticMapUrl(geoPoint)

        // Create the message object with server timestamp
        val newMessage = ChatMessage(
            senderId = currentUserUid,
            receiverId = otherUserUid,
            location = geoPoint,
            locationName = fetchedLocationName,
            staticMapUrl = staticMapUrl, // Save the map URL
            messageType = MessageType.LOCATION,
            timestamp = null, // CORRECT: Keep null for @ServerTimestamp annotation
            text = null, audioUrl = null, imageUrl = null, audioDurationMillis = null
        )

        // Save to Firestore messages subcollection
        try {
            // CORRECT: Pass newMessage directly. Firestore SDK handles @ServerTimestamp.
            val messageRef = messagesCollectionRef.add(newMessage).await()
            Log.d("MessagingViewModel", "Location message sent successfully. Doc ID: ${messageRef.id}")
            _error.value = null
            // **DENORMALIZE:** Update the main chat document
            updateChatMetadata("[Location] ${fetchedLocationName ?: ""}".trim(), FieldValue.serverTimestamp())
        } catch (e: Exception) {
            Log.e("MessagingViewModel", "Error sending location message", e)
            _error.value = "Failed to send location: ${e.localizedMessage}"
        } finally {
            _isFetchingLocation.value = false // Stop loading indicator
        }
    }

    // Function to generate Static Map URL
    private fun generateStaticMapUrl(geoPoint: GeoPoint): String? {
        if (MAPS_API_KEY.startsWith("YOUR_") || MAPS_API_KEY.length < 20) { // Basic check
            Log.e("StaticMap", "API Key not set, cannot generate map URL.")
            return null // Return null if key is missing or looks invalid
        }
        return try {
            val lat = geoPoint.latitude
            val lon = geoPoint.longitude
            val size = "400x200" // Desired image size (width x height)
            val zoom = 15 // Zoom level (adjust as needed)
            // URL encode marker parameters separately for clarity and safety
            val markerValue = URLEncoder.encode("color:red|label:P|$lat,$lon", "UTF-8")
            val centerValue = URLEncoder.encode("$lat,$lon", "UTF-8")

            // Construct the URL
            val url = "https://maps.googleapis.com/maps/api/staticmap?" +
                    "center=$centerValue" +
                    "&zoom=$zoom" +
                    "&size=$size" +
                    "&maptype=roadmap" + // map type (roadmap, satellite, etc.)
                    "&markers=$markerValue" + // Use the encoded marker string
                    "&key=$MAPS_API_KEY" // Your API key

            Log.d("StaticMap", "Generated Map URL: $url")
            url
        } catch (e: Exception) {
            Log.e("StaticMap", "Error encoding URL parameters", e)
            null
        }
    }


    // --- Upload and Send Logic ---
    private fun uploadAudioFile(audioUri: Uri, localFilePath: String, durationMillis: Long?) {
        _isUploadingAudio.value = true; _error.value = null
        viewModelScope.launch {
            try {
                val fileName = "${UUID.randomUUID()}.3gp"
                val audioRef = chatAudioStorageRef.child(fileName)
                Log.d("MessagingViewModel", "Starting audio upload for: $fileName")
                // Upload
                val uploadTask = audioRef.putFile(audioUri).await()
                val downloadUrl = audioRef.downloadUrl.await()
                Log.d("MessagingViewModel", "Audio uploaded successfully. URL: $downloadUrl")

                // Create message object
                val newMessage = ChatMessage(
                    senderId = currentUserUid,
                    receiverId = otherUserUid,
                    audioUrl = downloadUrl.toString(),
                    messageType = MessageType.AUDIO,
                    audioDurationMillis = durationMillis,
                    timestamp = null, // CORRECT: Keep null for @ServerTimestamp annotation
                    text = null, imageUrl = null, location = null, locationName = null, staticMapUrl = null
                )
                // Save message metadata to Firestore
                // CORRECT: Pass newMessage directly. Firestore SDK handles @ServerTimestamp.
                val messageRef = messagesCollectionRef.add(newMessage).await()
                Log.d("MessagingViewModel", "Audio message metadata saved to Firestore. Doc ID: ${messageRef.id}")

                // **DENORMALIZE:** Update the main chat document
                updateChatMetadata("[Audio]", FieldValue.serverTimestamp())

                // Delete local file *after* successful upload and metadata save
                try {
                    val fileToDelete = File(localFilePath)
                    if (fileToDelete.exists()) {
                        if (!fileToDelete.delete()) { Log.w("MessagingViewModel", "Failed to delete local audio file after upload: $localFilePath") }
                        else { Log.d("MessagingViewModel", "Deleted local audio file after upload: $localFilePath") }
                    }
                } catch (e: Exception) { Log.e("MessagingViewModel", "Error deleting local audio file after upload: $localFilePath", e) }

            } catch (e: Exception) {
                Log.e("MessagingViewModel", "Error sending audio message", e)
                _error.value = "Failed to send audio: ${e.localizedMessage}"
                // Consider deleting local file even on failure if appropriate
                // try { File(localFilePath).delete() } catch (_: Exception) {}
            } finally {
                _isUploadingAudio.value = false
            }
        }
    }

    fun sendImageMessage(imageUri: Uri, contentResolver: ContentResolver) { // Added contentResolver if needed later
        _isUploadingImage.value = true; _error.value = null
        viewModelScope.launch {
            try {
                val fileName = "${UUID.randomUUID()}.jpg"
                val imageRef = chatImagesStorageRef.child(fileName)
                Log.d("MessagingViewModel", "Starting image upload for: $fileName")
                // Upload
                val uploadTask = imageRef.putFile(imageUri).await()
                val downloadUrl = imageRef.downloadUrl.await()
                Log.d("MessagingViewModel", "Image uploaded successfully. URL: $downloadUrl")

                // Create message object
                val newMessage = ChatMessage(
                    senderId = currentUserUid,
                    receiverId = otherUserUid,
                    imageUrl = downloadUrl.toString(),
                    messageType = MessageType.IMAGE,
                    timestamp = null, // CORRECT: Keep null for @ServerTimestamp annotation
                    text = null, audioUrl = null, audioDurationMillis = null, location = null, locationName = null, staticMapUrl = null
                )
                // Save message metadata to Firestore
                // CORRECT: Pass newMessage directly. Firestore SDK handles @ServerTimestamp.
                val messageRef = messagesCollectionRef.add(newMessage).await()
                Log.d("MessagingViewModel", "Image message metadata saved to Firestore. Doc ID: ${messageRef.id}")

                // **DENORMALIZE:** Update the main chat document
                updateChatMetadata("[Image]", FieldValue.serverTimestamp())

            } catch (e: Exception) {
                Log.e("MessagingViewModel", "Error sending image message", e)
                _error.value = "Failed to send image: ${e.localizedMessage}"
            } finally {
                _isUploadingImage.value = false
            }
        }
    }

    fun sendMessage(text: String) {
        val trimmedText = text.trim()
        if (trimmedText.isBlank()) return // Don't send empty messages

        // Create message object
        val newMessage = ChatMessage(
            senderId = currentUserUid,
            receiverId = otherUserUid,
            text = trimmedText,
            messageType = MessageType.TEXT,
            timestamp = null // CORRECT: Keep null for @ServerTimestamp annotation
            // Other fields are null by default if using data class defaults
        )

        viewModelScope.launch {
            try {
                // Save message to Firestore
                // CORRECT: Pass newMessage directly. Firestore SDK handles @ServerTimestamp.
                val messageRef = messagesCollectionRef.add(newMessage).await()
                Log.d("MessagingViewModel", "Text message sent successfully. Doc ID: ${messageRef.id}")
                _error.value = null // Clear previous errors

                // **DENORMALIZE:** Update the main chat document
                updateChatMetadata(trimmedText, FieldValue.serverTimestamp())

            } catch (e: Exception) {
                // Log the specific error from Firestore if available
                if (e is com.google.firebase.firestore.FirebaseFirestoreException) {
                    Log.e("MessagingViewModel", "Firestore error sending text message: ${e.code}", e)
                } else {
                    Log.e("MessagingViewModel", "Error sending text message", e)
                }
                _error.value = "Failed to send message: ${e.localizedMessage}"
            }
            // No finally block needed here unless managing a loading state for text sending
        }
    }

    // --- Message Listener & Partner Name Fetch ---
    private fun listenForMessages() {
        messagesCollectionRef
            .orderBy("timestamp", Query.Direction.ASCENDING) // Listen for messages in order
            .limitToLast(50) // Optional: Limit the initial load and updates to recent messages
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    // Log specific Firestore error code if available
                    if (e is com.google.firebase.firestore.FirebaseFirestoreException) {
                        Log.w("MessagingViewModel", "Listen failed on messages: ${e.code}", e)
                        if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            _error.value = "Error loading messages (Permissions). Check Rules & Chat Data."
                        }
                    } else {
                        Log.w("MessagingViewModel", "Listen failed on messages.", e)
                    }
                    // Don't overwrite specific upload/send errors with listen errors generally
                    // _error.value = "Failed to load messages: ${e.localizedMessage}"
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    val loadedMessages = snapshots.documents.mapNotNull { doc ->
                        try {
                            // Ensure your ChatMessage class correctly handles Timestamps from Firestore
                            doc.toObject<ChatMessage>()?.copy(messageId = doc.id) // Add doc ID to model
                        } catch (convError: Exception) {
                            // Log the conversion error in more detail if needed
                            Log.e("MessagingViewModel", "Error converting message doc ${doc.id}. Data: ${doc.data}", convError)
                            // Provide a more specific error message if conversion fails often
                            // _error.value = "Error reading message data. Please try again."
                            null // Skip messages that fail to parse
                        }
                    }
                    _messages.value = loadedMessages
                    // Don't clear general errors on successful message load
                    // _error.value = null
                    Log.d("MessagingViewModel", "Loaded/Updated ${loadedMessages.size} messages.")
                } else {
                    Log.d("MessagingViewModel", "Current messages data: null")
                    _messages.value = emptyList() // Clear messages if snapshot is null
                }
            }
    }

    private fun fetchPartnerName() {
        viewModelScope.launch {
            try {
                val docRef = db.collection("users").document(otherUserUid)
                val snapshot = docRef.get().await()
                if (snapshot.exists()) {
                    // Try "username" first, then "name"
                    val name = snapshot.getString("username") ?: snapshot.getString("name")
                    if (!name.isNullOrBlank()) { // Check for blank string too
                        _partnerName.value = name
                    } else {
                        // Fallback if name fields are empty/missing
                        _partnerName.value = "User ${otherUserUid.take(6)}..."
                        Log.w("MessagingViewModel", "Partner user document $otherUserUid exists but has no 'username' or 'name'.")
                    }
                } else {
                    _partnerName.value = "Unknown User"
                    Log.w("MessagingViewModel", "Partner user document $otherUserUid does not exist.")
                }
            } catch (e: Exception) {
                if (e is com.google.firebase.firestore.FirebaseFirestoreException && e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    Log.e("MessagingViewModel", "PERMISSION_DENIED fetching partner name $otherUserUid. Check Rules!", e)
                    _partnerName.value = "User..." // Reset to default on permission error
                    _error.value = "Cannot load partner name (Permissions)."
                } else {
                    Log.e("MessagingViewModel", "Error fetching partner name for $otherUserUid", e)
                    _partnerName.value = "User..." // Reset to default on other errors
                }
            }
        }
    }

    // --- Factory ---
    companion object {
        fun provideFactory(
            application: Application,
            currentUserUid: String,
            otherUserUid: String
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(MessagingViewModel::class.java)) {
                    return MessagingViewModel(application, currentUserUid, otherUserUid) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }
}
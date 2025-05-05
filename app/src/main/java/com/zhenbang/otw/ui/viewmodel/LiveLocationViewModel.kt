package com.zhenbang.otw.ui.viewmodel // Or your preferred package

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
// RTDB Imports
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.database.ktx.getValue
// Firestore Imports
import com.google.firebase.firestore.ListenerRegistration as FirestoreListenerRegistration // Alias to avoid name clash
import com.google.firebase.firestore.ktx.firestore
// General Firebase Import
import com.google.firebase.ktx.Firebase
// Your App's classes
import com.zhenbang.otw.util.LocationHelper
import com.zhenbang.otw.util.UserLocation // Make sure this path is correct
// Coroutine/Flow Imports
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch


class LiveLocationViewModel(application: Application) : AndroidViewModel(application) {
    // --- Dependencies ---
    private val locationHelper = LocationHelper(application.applicationContext)
    private val auth = FirebaseAuth.getInstance()
    // Realtime Database (for Locations)
    private val firebaseDatabase = Firebase.database
    private val locationsRef = firebaseDatabase.getReference("locations")
    // Cloud Firestore (for User Names/Profiles)
    private val dbFirestore = Firebase.firestore
    private val usersCollectionRef = dbFirestore.collection("users")

    // --- State Flows ---
    // Locations from RTDB
    private val _userLocations = MutableStateFlow<Map<String, UserLocation>>(emptyMap())
    val userLocations: StateFlow<Map<String, UserLocation>> = _userLocations.asStateFlow()

    // ** NEW: User Names from Firestore **
    private val _userNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val userNames: StateFlow<Map<String, String>> = _userNames.asStateFlow()
    // ** END NEW **

    // --- Internal State ---
    internal val currentUserId: String?
        get() = auth.currentUser?.uid

    // RTDB Listener
    private var locationRTDBListener: ValueEventListener? = null
    // ** NEW: Firestore Listener Registration **
    private var profilesFirestoreListenerReg: FirestoreListenerRegistration? = null
    // ** END NEW **

    // Job for local location tracking
    private var locationTrackingJob: Job? = null

    // --- Public Functions ---

    // Call this when location permission is granted
    fun startLocationTracking() {
        if (currentUserId == null) {
            Log.e("LiveLocationViewModel", "User not logged in, cannot track location.")
            return
        }
        if (locationTrackingJob?.isActive == true) {
            Log.d("LiveLocationViewModel", "Location tracking already active.")
            return
        }

        Log.d("LiveLocationViewModel", "Starting location tracking for user $currentUserId ...")
        // 1. Start local location updates and push to RTDB
        startLocalLocationUpdates()
        // 2. Start listening to ALL locations from RTDB
        listenForAllUserLocationsRTDB()
        // 3. ** NEW: Start listening to ALL user profiles from Firestore **
        listenForAllUserProfilesFirestore()
        // ** END NEW **
    }

    fun stopLocationTracking() {
        Log.d("LiveLocationViewModel", "Stopping location tracking explicitly.")
        // Stop local updates
        locationTrackingJob?.cancel()
        locationTrackingJob = null
        // Stop RTDB listener
        locationRTDBListener?.let { locationsRef.removeEventListener(it); locationRTDBListener = null }
        // ** NEW: Stop Firestore listener **
        profilesFirestoreListenerReg?.remove(); profilesFirestoreListenerReg = null
        // ** END NEW **

        // Optional: Decide if you want to remove the Firebase entry when tracking stops
        // currentUserId?.let { locationsRef.child(it).removeValue() }
    }

    // --- Private Helper Functions ---

    // Starts collecting from LocationHelper flow
    private fun startLocalLocationUpdates() {
        locationTrackingJob = viewModelScope.launch {
            try {
                locationHelper.trackLocation().collect { location ->
                    Log.d("LiveLocationViewModel", "Collected local location: ${location.latitude}, ${location.longitude}")
                    updateFirebaseLocationRTDB(location) // Update RTDB
                    // Update local state immediately for current user
                    _userLocations.update { currentMap ->
                        currentUserId?.let { userId ->
                            currentMap + (userId to location)
                        } ?: currentMap
                    }
                }
            } catch (e: Exception) {
                Log.e("LiveLocationViewModel", "Error collecting local location updates", e)
            } finally {
                Log.d("LiveLocationViewModel", "Local location tracking flow finished.")
            }
        }
    }

    // Writes current user's location to RTDB
    private fun updateFirebaseLocationRTDB(location: UserLocation) {
        currentUserId?.let { userId ->
            // Log only occasionally to avoid spam
            // Log.d("LiveLocationViewModel", "Updating RTDB for user $userId")
            locationsRef.child(userId).setValue(location)
                .addOnSuccessListener { /* Log.d("LiveLocationViewModel", "RTDB location update success for $userId") */ }
                .addOnFailureListener { e -> Log.e("LiveLocationViewModel", "RTDB location update failed for $userId", e) }
        } ?: Log.w("LiveLocationViewModel", "Cannot update RTDB location, user ID is null.")
    }

    // Listens to /locations node in RTDB
    private fun listenForAllUserLocationsRTDB() {
        if (locationRTDBListener != null) {
            Log.d("LiveLocationViewModel", "Already listening to RTDB locations.")
            return
        }
        if (currentUserId == null) {
            Log.w("LiveLocationViewModel", "Cannot listen for RTDB locations, user not logged in.")
            return
        }

        Log.d("LiveLocationViewModel", "Attaching RTDB listener for /locations ...")
        locationRTDBListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d("LiveLocationViewModel", "RTDB data changed. Processing ${snapshot.childrenCount} users.")
                val updatedLocations = mutableMapOf<String, UserLocation>()
                for (userSnapshot in snapshot.children) {
                    val location = try { userSnapshot.getValue<UserLocation>() } catch (e: Exception) { null }
                    val userId = userSnapshot.key
                    if (userId != null && location != null) {
                        updatedLocations[userId] = location
                    } else {
                        Log.w("LiveLocationViewModel", "Skipping invalid RTDB entry: userId=$userId, location=$location")
                    }
                }
                Log.d("LiveLocationViewModel", "Updating local state with ${updatedLocations.size} locations from RTDB.")
                // Be careful about overwriting local update for current user if RTDB is slower
                _userLocations.update { currentLocalMap ->
                    // Merge RTDB data with potentially newer local data for current user
                    val currentLocalUserLoc = currentUserId?.let { currentLocalMap[it] }
                    val mergedMap = updatedLocations.toMutableMap() // Start with RTDB data
                    if (currentUserId != null && currentLocalUserLoc != null) {
                        mergedMap[currentUserId!!] = currentLocalUserLoc // Ensure our latest local update isn't overwritten by slightly older RTDB data
                    }
                    mergedMap // Return the merged map
                }
                // _userLocations.value = updatedLocations // Simpler version - overwrites local state
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("LiveLocationViewModel", "RTDB listener cancelled", error.toException())
                _userLocations.value = emptyMap()
            }
        }
        locationsRef.addValueEventListener(locationRTDBListener!!)
    }

    // ** NEW: Listens to /users collection in Firestore **
    // WARNING: Listens to the *entire* collection. Optimize in real apps.
    private fun listenForAllUserProfilesFirestore() {
        if (profilesFirestoreListenerReg != null) {
            Log.d("LiveLocationViewModel", "Already listening to Firestore profiles.")
            return
        }
        if (currentUserId == null) {
            Log.w("LiveLocationViewModel", "Cannot listen for Firestore profiles, user not logged in.")
            return
        }

        Log.d("LiveLocationViewModel", "Attaching Firestore listener for /users...")
        profilesFirestoreListenerReg = usersCollectionRef
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("LiveLocationViewModel", "Firestore User profiles listener failed.", e)
                    _userNames.value = emptyMap() // Clear on error
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    Log.d("LiveLocationViewModel", "Firestore received ${snapshots.size()} user profile updates.")
                    val namesMap = mutableMapOf<String, String>()
                    for (doc in snapshots) {
                        try {
                            // Assuming 'name' is the field storing the display name
                            val name = doc.getString("name")
                            if (name != null) {
                                namesMap[doc.id] = name // Use document ID (uid) as key
                            } else {
                                Log.w("LiveLocationViewModel", "Firestore user profile document ${doc.id} missing 'name' field.")
                            }
                        } catch (ex: Exception) {
                            Log.e("LiveLocationViewModel", "Error converting Firestore user profile document ${doc.id}", ex)
                        }
                    }
                    _userNames.value = namesMap
                    Log.d("LiveLocationViewModel", "Updated user names state from Firestore: ${namesMap.keys}")
                } else {
                    Log.d("LiveLocationViewModel", "Firestore User profiles snapshot was null")
                    _userNames.value = emptyMap()
                }
            }
    }
    // ** END NEW **


    // --- Lifecycle ---
    override fun onCleared() {
        super.onCleared()
        Log.d("LiveLocationViewModel", "ViewModel cleared. Cleaning up resources.")
        stopLocationTracking() // Call the consolidated cleanup function
    }
}
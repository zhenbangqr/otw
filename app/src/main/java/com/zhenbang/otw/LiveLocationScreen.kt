package com.zhenbang.otw // Your main package or ui package

// --- Keep existing imports ---
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
// --- Add Material 3 Icon & FAB imports ---
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.FloatingActionButton // Import FAB
import androidx.compose.material3.Icon // Import Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable // Keep this
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.zhenbang.otw.viewmodel.LiveLocationViewModel
import kotlinx.coroutines.launch
import java.util.Locale

// Assumed data structure
data class LocationData(val latitude: Double = 0.0, val longitude: Double = 0.0) {
    fun toLatLng(): LatLng = LatLng(latitude, longitude)
}


@Composable
fun LiveLocationScreen(
    liveLocationViewModel: LiveLocationViewModel = viewModel()
) {
    val context = LocalContext.current
    var hasLocationPermission by rememberSaveable { mutableStateOf(checkLocationPermissions(context)) }
    val scope = rememberCoroutineScope()

    // --- NEW State: Track if camera should follow current user ---
    val followCurrentUser = rememberSaveable { mutableStateOf(true) }
    // --- END NEW State ---

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // ... (permission handling logic remains the same) ...
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        hasLocationPermission = fineLocationGranted || coarseLocationGranted

        if (hasLocationPermission) {
            Log.d("LiveLocationScreen", "Permissions granted after request.")
            liveLocationViewModel.startLocationTracking()
        } else {
            Log.w("LiveLocationScreen", "Permissions denied after request.")
        }
    }

    LaunchedEffect(key1 = hasLocationPermission) {
        // ... (permission request logic remains the same) ...
        if (!hasLocationPermission) {
            Log.d("LiveLocationScreen", "Requesting location permissions...")
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            Log.d("LiveLocationScreen", "Permissions are granted. Starting location tracking if needed.")
            liveLocationViewModel.startLocationTracking()
        }
    }

    val userLocations by liveLocationViewModel.userLocations.collectAsStateWithLifecycle()
    val currentUserId = liveLocationViewModel.currentUserId
    val userNames by liveLocationViewModel.userNames.collectAsStateWithLifecycle()

    val defaultCameraLocation = LatLng(3.1390, 101.6869)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultCameraLocation, 12f)
    }

    val mapProperties by remember { mutableStateOf(MapProperties(isMyLocationEnabled = false)) }
    // Disable built-in location button if we add our own FAB
    val mapUiSettings by remember { mutableStateOf(MapUiSettings(zoomControlsEnabled = true, myLocationButtonEnabled = false)) }


    // --- MODIFIED: Effect to center camera conditionally ---
    LaunchedEffect(key1 = userLocations, key2 = currentUserId, key3 = followCurrentUser.value) {
        // Only auto-center if the flag is true
        if (followCurrentUser.value) {
            currentUserId?.let { uid ->
                userLocations[uid]?.let { currentUserLocation ->
                    val currentMapTarget = cameraPositionState.position.target
                    // Only animate if significantly far or at default
                    if (currentMapTarget == defaultCameraLocation || distanceBetween(currentMapTarget, currentUserLocation.toLatLng()) > 10000) {
                        Log.d("LiveLocationScreen", "Auto-centering camera because followCurrentUser is true.")
                        scope.launch {
                            cameraPositionState.animate(
                                update = CameraUpdateFactory.newLatLngZoom(currentUserLocation.toLatLng(), 15f),
                                durationMs = 1000
                            )
                        }
                    }
                    // Optional: If followCurrentUser is true, you might want smoother following
                    // for smaller movements too, adjust conditions as needed.
                }
            }
        } else {
            Log.d("LiveLocationScreen", "Not auto-centering because followCurrentUser is false.")
        }
    }
    // --- END MODIFIED Effect ---


    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // --- MODIFIED: Map container Box to include FAB ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Google Map Composable (remains the same)
                if (hasLocationPermission) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        properties = mapProperties,
                        uiSettings = mapUiSettings, // Use updated settings
                        onMapLoaded = { Log.d("LiveLocationScreen", "Map loaded.") },
                        // Clear follow flag if user manually moves map? (Optional)
                        // onMapMoved = { if (it.isGesture) followCurrentUser.value = false }
                        onMapClick = { latLng ->
                            Log.d("LiveLocationScreen", "Map clicked at: $latLng")
                            // Optional: Clicking map could also disable following
                            // followCurrentUser.value = false
                        }
                    ) {
                        // Draw markers (remains the same)
                        userLocations.forEach { (userId, userLocation) ->
                            val isCurrentUser = userId == currentUserId
                            val markerTitle = userNames[userId] ?: if (isCurrentUser) "You" else "User ${userId.take(6)}..."
                            val snippetText = try { "Lat: ${String.format(Locale.US, "%.4f", userLocation.latitude)}, Lng: ${String.format(Locale.US, "%.4f", userLocation.longitude)}" } catch (e: Exception) { "Location data error" }
                            Marker( /* ... Marker parameters ... */
                                state = MarkerState(position = userLocation.toLatLng()),
                                title = markerTitle,
                                snippet = snippetText,
                                icon = BitmapDescriptorFactory.defaultMarker( if (isCurrentUser) BitmapDescriptorFactory.HUE_AZURE else BitmapDescriptorFactory.HUE_RED )
                            )
                        }
                    }
                } else {
                    // Permission denial UI (remains the same)
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { /* ... Button ... */
                        Button(onClick = {
                            permissionLauncher.launch( arrayOf( Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION ) )
                        }) { Text("Grant Location Permission") }
                    }
                }

                // --- NEW: Reposition Floating Action Button ---
                if (hasLocationPermission) {
                    FloatingActionButton(
                        onClick = {
                            Log.d("LiveLocationScreen", "Reposition button clicked.")
                            // 1. Get current user's latest location
                            val latestCurrentUserLocation = currentUserId?.let { userLocations[it] }

                            if (latestCurrentUserLocation != null) {
                                // 2. Animate camera to it
                                scope.launch {
                                    cameraPositionState.animate(
                                        update = CameraUpdateFactory.newLatLngZoom(latestCurrentUserLocation.toLatLng(), 15f),
                                        durationMs = 1000
                                    )
                                    // 3. Set flag to true AFTER animation starts/finishes
                                    followCurrentUser.value = true
                                    Log.d("LiveLocationScreen", "Camera repositioned, followCurrentUser set to true.")
                                }
                            } else {
                                Log.w("LiveLocationScreen", "Could not reposition, current user location unavailable.")
                                // Optionally show a Toast message to the user
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomStart) // Position FAB
                            .padding(start = 16.dp, bottom = 32.dp), // Increased bottom padding
                        containerColor = MaterialTheme.colorScheme.primary // Use theme colors
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Re-center on my location"
                        )
                    }
                }
                // --- END NEW FAB ---

            } // End of Map container Box

            // --- User List Section ---
            if (hasLocationPermission && userLocations.size > 1) {
                val currentUserLocation = remember(currentUserId, userLocations) { currentUserId?.let { userLocations[it] } }
                val otherUsers = remember(userLocations, currentUserId) { userLocations.filterKeys { it != currentUserId } }

                if (otherUsers.isNotEmpty() && currentUserLocation != null) {
                    Text( /* ... Title ... */
                        text = "Other Users Online:", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    LazyColumn( /* ... Modifiers ... */
                        modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp).padding(bottom = 8.dp)
                    ) {
                        items(otherUsers.toList(), key = { (userId, _) -> userId }) { (userId, otherUserLocation) ->
                            val userName = userNames[userId] ?: "User ${userId.take(6)}..."
                            val distanceMeters = distanceBetween(currentUserLocation.toLatLng(), otherUserLocation.toLatLng())
                            val distanceText = formatDistance(distanceMeters)

                            UserListItem(
                                name = userName,
                                distanceText = distanceText,
                                onClick = { // --- MODIFIED onClick ---
                                    Log.d("LiveLocationScreen", "User list item clicked: $userId. Animating and disabling follow.")
                                    // 1. Stop following current user
                                    followCurrentUser.value = false
                                    // 2. Animate to the clicked user
                                    scope.launch {
                                        cameraPositionState.animate(
                                            update = CameraUpdateFactory.newLatLngZoom(otherUserLocation.toLatLng(), 15f),
                                            durationMs = 1000
                                        )
                                    }
                                    // --- END MODIFIED onClick ---
                                }
                            )
                            Divider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                } else if (otherUsers.isNotEmpty() && currentUserLocation == null) {
                    Text( /* ... Calculating text ... */
                        text = "Calculating distances...", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall
                    )
                }
            } // End of User List Section
        } // End of main Column
    } // End of Scaffold
}

// UserListItem composable (remains the same)
@Composable
fun UserListItem(name: String, distanceText: String?, onClick: () -> Unit) { /* ... Implementation ... */
    Row( modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically ) {
        Column(modifier = Modifier.weight(1f)) {
            Text( text = name, style = MaterialTheme.typography.bodyLarge )
            if (distanceText != null) { Text( text = "Distance: $distanceText", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant ) }
        }
    }
}

// checkLocationPermissions function (remains the same)
private fun checkLocationPermissions(context: Context): Boolean { /* ... Implementation ... */
    val fine = ContextCompat.checkSelfPermission( context, Manifest.permission.ACCESS_FINE_LOCATION ) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission( context, Manifest.permission.ACCESS_COARSE_LOCATION ) == PackageManager.PERMISSION_GRANTED
    Log.d("PermissionsCheck", "Fine: $fine, Coarse: $coarse")
    return fine || coarse
}

// distanceBetween function (remains the same)
private fun distanceBetween(p1: LatLng, p2: LatLng): Float { /* ... Implementation ... */
    val results = FloatArray(1)
    try { android.location.Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, results) }
    catch (e: Exception) { Log.e("DistanceCalc", "Error calculating distance", e); return Float.MAX_VALUE }
    return results[0]
}

// formatDistance function (remains the same)
private fun formatDistance(meters: Float): String { /* ... Implementation ... */
    if (meters == Float.MAX_VALUE) return "N/A"
    return if (meters < 1000) { "${meters.toInt()} m" } else { String.format(Locale.US, "%.1f km", meters / 1000f) }
}
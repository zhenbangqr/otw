package com.zhenbang.otw.ui.screen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
// import android.location.Location // Was unused, removed
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.* // Import animation functions
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown // Import icons
import androidx.compose.material.icons.filled.KeyboardArrowUp   // Import icons
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
import androidx.navigation.NavController // Use NavController
// import androidx.navigation.NavHostController // Remove duplicate import
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.zhenbang.otw.ui.viewmodel.LiveLocationViewModel
import kotlinx.coroutines.launch
import java.util.Locale

// Assumed data structure (remains the same)
data class LocationData(val latitude: Double = 0.0, val longitude: Double = 0.0) {
    fun toLatLng(): LatLng = LatLng(latitude, longitude)
}


@Composable
fun LiveLocationScreen(
    navController: NavController,
    liveLocationViewModel: LiveLocationViewModel = viewModel()
) {
    val context = LocalContext.current
    var hasLocationPermission by rememberSaveable { mutableStateOf(checkLocationPermissions(context)) }
    val scope = rememberCoroutineScope()
    val followCurrentUser = rememberSaveable { mutableStateOf(true) }
    // **NEW:** State for user list visibility
    var isUserListExpanded by rememberSaveable { mutableStateOf(true) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions: Map<String, Boolean> ->
        val fine = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarse = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        hasLocationPermission = fine || coarse
        if (hasLocationPermission) {
            Log.d("LiveLocationScreen", "Permissions granted after request.")
            liveLocationViewModel.startLocationTracking()
        } else {
            Log.w("LiveLocationScreen", "Permissions denied.")
        }
    }

    LaunchedEffect(key1 = hasLocationPermission) {
        if (!hasLocationPermission) { permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }
        else { liveLocationViewModel.startLocationTracking() }
    }

    val userLocations by liveLocationViewModel.userLocations.collectAsStateWithLifecycle()
    val currentUserId = liveLocationViewModel.currentUserId
    val userNames by liveLocationViewModel.userNames.collectAsStateWithLifecycle()

    val defaultCameraLocation = LatLng(3.1390, 101.6869) // Default to KL
    val cameraPositionState = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(defaultCameraLocation, 12f) }

    val mapProperties by remember { mutableStateOf(MapProperties(isMyLocationEnabled = false)) } // Keep user marker separate
    val mapUiSettings by remember { mutableStateOf(MapUiSettings(zoomControlsEnabled = true, myLocationButtonEnabled = false)) }

    // Effect to center camera conditionally (remains the same)
    LaunchedEffect(key1 = userLocations, key2 = currentUserId, key3 = followCurrentUser.value) {
        if (followCurrentUser.value) {
            currentUserId?.let { uid -> userLocations[uid]?.let { loc ->
                val target = cameraPositionState.position.target
                // Auto-center if it's the default location OR if current location is far from map center
                if (target == defaultCameraLocation || distanceBetween(target, loc.toLatLng()) > 10000) { // e.g., > 10km
                    Log.d("LiveLocationScreen", "Auto-centering camera.")
                    scope.launch { cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(loc.toLatLng(), 15f), 1000) }
                }
            } }
        } else { Log.d("LiveLocationScreen", "Not auto-centering.") }
    }

    Scaffold { paddingValues ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)) {
            // Map container Box with FAB
            Box(modifier = Modifier
                .fillMaxWidth()
                .weight(1f)) {
                if (hasLocationPermission) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        properties = mapProperties,
                        uiSettings = mapUiSettings,
                        onMapLoaded = { Log.d("LiveLocationScreen", "Map loaded.") },
                        // Disable follow mode if user interacts with map
                        onMapClick = { followCurrentUser.value = false },
                        onMapLongClick = { followCurrentUser.value = false },
                        // Consider adding onCameraMoveStarted if needed
                    ) {
                        userLocations.forEach { (userId, userLocation) ->
                            val isCurrentUser = userId == currentUserId
                            val name = userNames[userId]
                            val markerTitle = name ?: if (isCurrentUser) "You" else "User ${userId.take(6)}..."
                            val snippet = try { "Lat: ${"%.4f".format(userLocation.latitude)}, Lng: ${"%.4f".format(userLocation.longitude)}" } catch (e: Exception) { "N/A" }
                            Marker(
                                state = MarkerState(position = userLocation.toLatLng()),
                                title = markerTitle,
                                snippet = snippet,
                                icon = BitmapDescriptorFactory.defaultMarker(if (isCurrentUser) BitmapDescriptorFactory.HUE_AZURE else BitmapDescriptorFactory.HUE_RED)
                            )
                        }
                    }
                } else { // Permission denial UI
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Button(onClick = { permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }) { Text("Grant Location Permission") }
                    }
                }

                // Re-center FAB
                if (hasLocationPermission) {
                    FloatingActionButton(
                        onClick = {
                            Log.d("LiveLocationScreen", "Reposition button clicked.")
                            currentUserId?.let { uid -> userLocations[uid]?.let { loc ->
                                scope.launch {
                                    cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(loc.toLatLng(), 15f), 1000)
                                    followCurrentUser.value = true // Re-enable follow on re-center
                                    Log.d("LiveLocationScreen", "Repositioned. Follow set to true.")
                                }
                            } } ?: Log.w("LiveLocationScreen", "Cannot reposition, current location unknown.")
                        },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp, bottom = 32.dp),
                        containerColor = MaterialTheme.colorScheme.primary
                    ) { Icon(Icons.Default.Home, "Re-center") }
                }
            } // End Map Box

            // --- User List Section ---
            if (hasLocationPermission && userLocations.size > 1) {
                // **NEW:** Row for Collapse/Expand Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isUserListExpanded = !isUserListExpanded } // Toggle on row click
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End // Align button to the right
                ) {
                    Text(
                        text = if (isUserListExpanded) "Hide List" else "Show List",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Icon(
                        imageVector = if (isUserListExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isUserListExpanded) "Collapse user list" else "Expand user list",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // **NEW:** AnimatedVisibility for the list content
                AnimatedVisibility(
                    visible = isUserListExpanded,
                    enter = slideInVertically { fullHeight -> fullHeight } + fadeIn(), // Slide in from bottom
                    exit = slideOutVertically { fullHeight -> fullHeight } + fadeOut() // Slide out to bottom
                ) {
                    Column { // Wrap list content in a Column for AnimatedVisibility
                        val currentUserLocation = remember(currentUserId, userLocations) { currentUserId?.let { userLocations[it] } }
                        val otherUsers = remember(userLocations, currentUserId) { userLocations.filterKeys { it != currentUserId } }

                        if (otherUsers.isNotEmpty() && currentUserLocation != null) {
                            Text("Other Users Online:", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 8.dp)) // Adjust padding
                            LazyColumn(modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 180.dp)
                                .padding(bottom = 8.dp)) {
                                items(otherUsers.toList(), key = { (userId, _) -> userId }) { (userId, otherUserLocation) ->
                                    val userName = userNames[userId] ?: "User ${userId.take(6)}..."
                                    val distanceMeters = distanceBetween(currentUserLocation.toLatLng(), otherUserLocation.toLatLng())
                                    val distanceText = formatDistance(distanceMeters)

                                    UserListItem(
                                        name = userName,
                                        distanceText = distanceText,
                                        onClick = {
                                            Log.d("LiveLocationScreen", "Centering map on $userId")
                                            followCurrentUser.value = false
                                            scope.launch { cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(otherUserLocation.toLatLng(), 15f), 1000) }
                                        }
                                    )
                                    Divider(modifier = Modifier.padding(horizontal = 16.dp))
                                }
                            }
                        } else if (otherUsers.isNotEmpty() && currentUserLocation == null) {
                            Text("Calculating distances...", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
                        }
                    } // End Column for AnimatedVisibility
                } // End AnimatedVisibility
            } // End User List Section outer if
        } // End main Column
    } // End Scaffold
}

// UserListItem composable (remains the same)
@Composable
fun UserListItem( name: String, distanceText: String?, onClick: () -> Unit) { /* ... implementation unchanged ... */
    Row( modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 8.dp, horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically ) { Column( modifier = Modifier
        .weight(1f)
        .clickable(onClick = onClick)
        .padding(end = 8.dp) ) { Text(text = name, style = MaterialTheme.typography.bodyLarge); if (distanceText != null) { Text(text = "Distance: $distanceText", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }; }
}

// checkLocationPermissions function (remains the same)
private fun checkLocationPermissions(context: Context): Boolean { /* ... */ val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED; val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED; Log.d("PermissionsCheck", "Fine: $fine, Coarse: $coarse"); return fine || coarse }

// distanceBetween function (remains the same)
private fun distanceBetween(p1: LatLng, p2: LatLng): Float { /* ... */ val results = FloatArray(1); try { Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, results) } catch (e: Exception) { Log.e("DistanceCalc", "Error calculating distance", e); return Float.MAX_VALUE }; return results[0] }

// formatDistance function (remains the same)
private fun formatDistance(meters: Float): String { /* ... */ if (meters == Float.MAX_VALUE) return "N/A"; return if (meters < 1000) { "${meters.toInt()} m" } else { String.format(Locale.US, "%.1f km", meters / 1000f) } }
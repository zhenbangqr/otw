package com.zhenbang.otw.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.TimeUnit

// Simple data class to hold location
data class UserLocation(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timestamp: Long = 0L // Added timestamp
) {
    // Default constructor for Firebase
    constructor() : this(0.0, 0.0, 0L)

    fun toLatLng(): LatLng {
        return LatLng(latitude, longitude)
    }
}


class LocationHelper(context: Context) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    // This Flow will emit location updates
    @SuppressLint("MissingPermission") // Permissions are checked before calling start
    fun trackLocation(): Flow<UserLocation> = callbackFlow {
        Log.d("LocationHelper", "Setting up location tracking flow.")
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, TimeUnit.SECONDS.toMillis(5)) // Update every 5 seconds
            .setWaitForAccurateLocation(true) // Adjusted based on API changes
            .setMinUpdateIntervalMillis(TimeUnit.SECONDS.toMillis(3)) // Minimum interval 3 seconds
            .setMaxUpdateDelayMillis(TimeUnit.SECONDS.toMillis(10)) // Max delay 10 seconds
            .build()


        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    Log.d("LocationHelper", "Location received: ${location.latitude}, ${location.longitude}")
                    val userLocation = UserLocation(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        timestamp = System.currentTimeMillis() // Use current time
                    )
                    // Send the location update to the Flow collector
                    trySend(userLocation).isSuccess // Use trySend for callbackFlow
                } ?: Log.w("LocationHelper", "Last location was null.")
            }

            override fun onLocationAvailability(locationAvailability: LocationAvailability) {
                Log.d("LocationHelper", "Location availability: ${locationAvailability.isLocationAvailable}")
                if (!locationAvailability.isLocationAvailable) {
                    Log.w("LocationHelper", "Location is not available.")
                    // Optionally, you could close the flow or emit an error state here
                    // close(RuntimeException("Location not available"))
                }
            }
        }

        // Request location updates
        Log.d("LocationHelper", "Requesting location updates...")
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper() // Use main looper for callbacks
        ).addOnFailureListener { e ->
            Log.e("LocationHelper", "Failed to request location updates", e)
            close(e) // Close the flow with the exception on failure
        }.addOnSuccessListener {
            Log.d("LocationHelper", "Location updates requested successfully.")
        }


        // When the Flow collector cancels (e.g., screen is closed), remove updates
        awaitClose {
            Log.d("LocationHelper", "Stopping location updates.")
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }
}
package com.example.segundoparcialmovil2026_2.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A single GPS fix. */
data class DeviceLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val capturedAt: Long,
)

/**
 * Thin wrapper around Google Play Services' FusedLocationProviderClient - the
 * Google-recommended location API on Android.
 *
 * Call [hasPermission] before [currentLocation], ideally after requesting the
 * runtime permission from the UI.
 */
class LocationService(private val context: Context) {

    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    fun hasFinePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    fun hasCoarsePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    fun hasPermission(): Boolean = hasFinePermission() || hasCoarsePermission()

    /** True when the user has GPS / location services switched on at the system level. */
    fun isLocationEnabled(): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.isLocationEnabled
        } else {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    /**
     * Asks Play Services for one high-accuracy fix. Throws [SecurityException] without
     * permission, [IOException] when no fix could be produced (e.g. GPS off).
     */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): DeviceLocation {
        check(hasPermission()) { "Location permission not granted" }

        val cancellation = CancellationTokenSource()

        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cancellation.cancel() }

            client.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellation.token,
            ).addOnSuccessListener { location ->
                if (location == null) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(IOException("No location fix available"))
                    }
                    return@addOnSuccessListener
                }
                if (continuation.isActive) {
                    continuation.resume(
                        DeviceLocation(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            accuracyMeters = if (location.hasAccuracy()) location.accuracy else 0f,
                            capturedAt = System.currentTimeMillis(),
                        ),
                    )
                }
            }.addOnFailureListener { error ->
                if (continuation.isActive) {
                    continuation.resumeWithException(error)
                }
            }
        }
    }
}

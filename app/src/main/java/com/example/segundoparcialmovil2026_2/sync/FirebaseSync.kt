package com.example.segundoparcialmovil2026_2.sync

import android.util.Log
import com.example.segundoparcialmovil2026_2.data.LocationPoint
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Uploads unsynced rows to the Firestore collection [COLLECTION].
 *
 * Firebase is configured by the `com.google.gms.google-services` Gradle plugin from
 * `app/google-services.json`, so the SDK is already initialised when the app starts.
 * Everything here is defensive: if Firebase is unavailable the app keeps working
 * locally and [isConfigured] is false instead of crashing.
 *
 * One Firestore document per Room row:
 * ```
 * locations/{localId} = { localId, latitude, longitude, accuracyMeters, capturedAt, deviceTimestamp }
 * ```
 */
class FirebaseSync {

    /** Null when google-services.json is missing or Firebase failed to initialise. */
    private val firestore: FirebaseFirestore? by lazy {
        runCatching { FirebaseFirestore.getInstance() }
            .onFailure { Log.w(TAG, "Firestore unavailable: ${it.message}") }
            .getOrNull()
    }

    /** True when Firebase initialised and the Firestore instance is usable. */
    val isConfigured: Boolean
        get() = firestore != null

    /** Uploads every point in [points] as one atomic Firestore batch. Throws on failure. */
    suspend fun upload(points: List<LocationPoint>) {
        val db = firestore ?: error("Firestore is not configured")
        val batch = db.batch()
        val collection = db.collection(COLLECTION)

        points.forEach { point ->
            batch.set(
                collection.document(point.id.toString()),
                mapOf(
                    "localId" to point.id,
                    "latitude" to point.latitude,
                    "longitude" to point.longitude,
                    "accuracyMeters" to point.accuracyMeters,
                    "capturedAt" to point.capturedAt,
                    "deviceTimestamp" to System.currentTimeMillis(),
                ),
            )
        }

        batch.commit().await()
    }

    private companion object {
        const val TAG = "FirebaseSync"
        const val COLLECTION = "locations"
    }
}

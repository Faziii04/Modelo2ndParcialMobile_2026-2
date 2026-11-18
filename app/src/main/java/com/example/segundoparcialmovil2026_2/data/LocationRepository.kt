package com.example.segundoparcialmovil2026_2.data

import com.example.segundoparcialmovil2026_2.sync.FirebaseSync
import kotlinx.coroutines.flow.Flow

/** Outcome of one sync attempt. */
sealed interface SyncResult {
    data class Success(val syncedCount: Int) : SyncResult
    data object NotConfigured : SyncResult
    data object NothingToSync : SyncResult
    data class Error(val message: String) : SyncResult
}

/**
 * Single source of truth: SQLite (Room) is always written first, Firestore is only
 * a copy. That is what makes the app usable with no connection at all.
 */
class LocationRepository(
    private val dao: LocationPointDao,
    private val cloud: FirebaseSync,
) {
    val allPoints: Flow<List<LocationPoint>> = dao.observeAll()

    suspend fun save(latitude: Double, longitude: Double, accuracyMeters: Float): Long =
        dao.insert(
            LocationPoint(
                latitude = latitude,
                longitude = longitude,
                accuracyMeters = accuracyMeters,
                capturedAt = System.currentTimeMillis(),
                synced = false,
            ),
        )

    /** Firestore is optional: with no project configured this reports NotConfigured and stops. */
    suspend fun syncPending(): SyncResult {
        if (!cloud.isConfigured) return SyncResult.NotConfigured

        val pending = dao.getPendingSync()
        if (pending.isEmpty()) return SyncResult.NothingToSync

        return try {
            cloud.upload(pending)
            pending.forEach { dao.markSynced(it.id) }
            SyncResult.Success(pending.size)
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: e::class.java.simpleName)
        }
    }
}

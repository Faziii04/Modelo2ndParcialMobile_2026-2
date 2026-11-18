package com.example.segundoparcialmovil2026_2.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.segundoparcialmovil2026_2.data.AppDatabase
import com.example.segundoparcialmovil2026_2.data.LocationPoint
import com.example.segundoparcialmovil2026_2.data.LocationRepository
import com.example.segundoparcialmovil2026_2.data.SyncResult
import com.example.segundoparcialmovil2026_2.location.DeviceLocation
import com.example.segundoparcialmovil2026_2.location.LocationService
import com.example.segundoparcialmovil2026_2.sync.FirebaseSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

data class UiMessage(val text: String, val isError: Boolean = false)

data class LocationUiState(
    /** Latest fix from Google Play Services; not saved until the user taps Save. */
    val current: DeviceLocation? = null,
    val saved: List<LocationPoint> = emptyList(),
    /** Row highlighted on the map and in the list. */
    val selectedId: Long? = null,
    val locating: Boolean = false,
    val saving: Boolean = false,
    val syncing: Boolean = false,
    val permissionGranted: Boolean = false,
    val firestoreConfigured: Boolean = false,
    val message: UiMessage? = null,
)

/**
 * Keeps every side effect (location fix, Room write, Firestore push) off the UI thread
 * and exposes one [StateFlow] for the Compose screen to render.
 */
class LocationViewModel(
    application: Application,
    private val locationService: LocationService,
    private val repository: LocationRepository,
    private val cloud: FirebaseSync,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(
        LocationUiState(
            permissionGranted = locationService.hasPermission(),
            firestoreConfigured = cloud.isConfigured,
        ),
    )
    val state: StateFlow<LocationUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.allPoints.collect { points ->
                _state.update { it.copy(saved = points) }
            }
        }
    }

    fun refreshPermission() {
        _state.update { it.copy(permissionGranted = locationService.hasPermission()) }
    }

    fun selectPoint(id: Long?) {
        _state.update { it.copy(selectedId = id) }
    }

    fun captureLocation() {
        if (!locationService.hasPermission()) {
            clearAndShow("Location permission is required to read the GPS.")
            return
        }
        if (!locationService.isLocationEnabled()) {
            clearAndShow("Turn on device location (GPS) first.")
            return
        }
        if (_state.value.locating) return

        _state.update { it.copy(locating = true, message = null) }
        viewModelScope.launch {
            try {
                val fix = locationService.currentLocation()
                _state.update { it.copy(current = fix, locating = false) }
            } catch (e: Exception) {
                val reason = if (e is IOException) {
                    "No location fix yet. Check GPS or try again near a window."
                } else {
                    e.message ?: "Could not read location."
                }
                _state.update { it.copy(locating = false, message = UiMessage(reason, isError = true)) }
            }
        }
    }

    /** Room write first, then (if configured) an immediate best-effort Firestore push. */
    fun saveCurrent() {
        val fix = _state.value.current
        if (fix == null) {
            clearAndShow("Get a location first.")
            return
        }
        if (_state.value.saving) return

        _state.update { it.copy(saving = true, message = null) }
        viewModelScope.launch {
            try {
                repository.save(fix.latitude, fix.longitude, fix.accuracyMeters)
                _state.update { it.copy(saving = false, current = null) }

                if (!cloud.isConfigured) {
                    _state.update {
                        it.copy(message = UiMessage("Saved on the device (offline)."))
                    }
                    return@launch
                }

                when (val result = repository.syncPending()) {
                    is SyncResult.Success ->
                        _state.update { it.copy(message = UiMessage("Saved and synced with Firestore.")) }
                    SyncResult.NothingToSync ->
                        _state.update { it.copy(message = UiMessage("Saved on the device.")) }
                    SyncResult.NotConfigured ->
                        _state.update { it.copy(message = UiMessage("Saved on the device (Firestore not configured).")) }
                    is SyncResult.Error ->
                        _state.update {
                            it.copy(
                                message = UiMessage(
                                    "Saved on the device. Sync failed: ${result.message}",
                                    isError = true,
                                ),
                            )
                        }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        saving = false,
                        message = UiMessage("Could not save: ${e.message}", isError = true),
                    )
                }
            }
        }
    }

    fun syncNow() {
        if (_state.value.syncing) return
        if (!cloud.isConfigured) {
            clearAndShow("Firestore is unavailable. Check app/google-services.json and your connection.")
            return
        }

        _state.update { it.copy(syncing = true, message = null) }
        viewModelScope.launch {
            val message = when (val result = repository.syncPending()) {
                is SyncResult.Success -> UiMessage("Synced ${result.syncedCount} point(s) with Firestore.")
                SyncResult.NothingToSync -> UiMessage("Everything is already synced.")
                SyncResult.NotConfigured -> UiMessage("Firestore is not configured.", isError = true)
                is SyncResult.Error -> UiMessage("Sync failed: ${result.message}", isError = true)
            }
            _state.update { it.copy(syncing = false, message = message) }
        }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun clearAndShow(text: String) {
        _state.update { it.copy(message = UiMessage(text, isError = true)) }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val dao = AppDatabase.get(application).locationPointDao()
                    val cloud = FirebaseSync()
                    return LocationViewModel(
                        application = application,
                        locationService = LocationService(application),
                        repository = LocationRepository(dao, cloud),
                        cloud = cloud,
                    ) as T
                }
            }
    }
}

package com.example.segundoparcialmovil2026_2.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.segundoparcialmovil2026_2.data.LocationPoint
import com.example.segundoparcialmovil2026_2.location.DeviceLocation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationScreen(
    viewModel: LocationViewModel,
    onRequestPermission: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbar.showSnackbar(message.text)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Locations") },
                actions = {
                    TextButton(
                        onClick = viewModel::syncNow,
                        enabled = !state.syncing,
                    ) {
                        Text(if (state.syncing) "Syncing…" else "Sync")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Google Maps: saved rows + the live GPS fix.
            LocationMap(
                current = state.current,
                saved = state.saved,
                selectedId = state.selectedId,
                onSelect = viewModel::selectPoint,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            CurrentLocationCard(
                fix = state.current,
                locating = state.locating,
                saving = state.saving,
                permissionGranted = state.permissionGranted,
                onCapture = {
                    if (state.permissionGranted) viewModel.captureLocation() else onRequestPermission()
                },
                onSave = viewModel::saveCurrent,
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "Saved points (${state.saved.size})",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            if (state.saved.isEmpty()) {
                Text(
                    text = "Nothing saved yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.saved, key = { it.id }) { point ->
                        SavedPointRow(
                            point = point,
                            selected = point.id == state.selectedId,
                            onClick = { viewModel.selectPoint(point.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrentLocationCard(
    fix: DeviceLocation?,
    locating: Boolean,
    saving: Boolean,
    permissionGranted: Boolean,
    onCapture: () -> Unit,
    onSave: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = if (permissionGranted) "Current location" else "Location permission needed",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            if (fix != null) {
                CoordinateText(latitude = fix.latitude, longitude = fix.longitude)
                Text(
                    text = "Accuracy ±${fix.accuracyMeters.toInt()} m",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = if (permissionGranted) {
                        "No fix yet. Ask Google Play Services for one."
                    } else {
                        "Grant location access to read the GPS."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onCapture,
                    enabled = !locating,
                    modifier = Modifier.weight(1f),
                ) {
                    if (locating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Locating…")
                    } else {
                        Text(if (permissionGranted) "Get location" else "Allow location")
                    }
                }
                Button(
                    onClick = onSave,
                    enabled = fix != null && !saving,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (saving) "Saving…" else "Save")
                }
            }
        }
    }
}

@Composable
private fun SavedPointRow(
    point: LocationPoint,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val formatter = remember { SimpleDateFormat("dd MMM yyyy · HH:mm:ss", Locale.getDefault()) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                CoordinateText(latitude = point.latitude, longitude = point.longitude)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = formatter.format(Date(point.capturedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SyncBadge(synced = point.synced)
        }
    }
}

@Composable
private fun CoordinateText(latitude: Double, longitude: Double) {
    Text(
        text = "%.6f, %.6f".format(Locale.US, latitude, longitude),
        style = MaterialTheme.typography.bodyLarge,
        fontFamily = FontFamily.Monospace,
    )
}

@Composable
private fun SyncBadge(synced: Boolean) {
    Text(
        text = if (synced) "SYNCED" else "LOCAL",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = if (synced) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

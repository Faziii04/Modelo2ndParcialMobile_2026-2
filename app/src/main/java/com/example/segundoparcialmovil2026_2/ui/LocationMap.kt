package com.example.segundoparcialmovil2026_2.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.segundoparcialmovil2026_2.data.LocationPoint
import com.example.segundoparcialmovil2026_2.location.DeviceLocation
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Google Maps via the classic [MapView] hosted in an [AndroidView], with markers added
 * imperatively. This avoids maps-compose's subcomposition (MapApplier), which is fragile
 * across Compose/library version pairs and crashed at startup.
 */
@Composable
fun LocationMap(
    current: DeviceLocation?,
    saved: List<LocationPoint>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val onSelectState = rememberUpdatedState(onSelect)

    val mapView = remember {
        MapView(context).apply { onCreate(null) }
    }

    // Google Maps requires the host lifecycle to be forwarded to the MapView.
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    var googleMap by remember { mutableStateOf<GoogleMap?>(null) }

    Column(modifier = modifier) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
        )

        val selected = saved.firstOrNull { it.id == selectedId }

        Text(
            text = selected?.let { point ->
                val coords = "%.5f, %.5f".format(Locale.US, point.latitude, point.longitude)
                val time = SimpleDateFormat("dd MMM · HH:mm:ss", Locale.getDefault())
                    .format(Date(point.capturedAt))
                "Selected: $coords · $time"
            } ?: "Tap a marker to select a saved point.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { map ->
            map.setOnMarkerClickListener { marker ->
                (marker.tag as? Long)?.let { onSelectState.value(it) }
                false // let Maps show the default info window
            }
            googleMap = map
        }
    }

    // One marker per saved row + one for the live fix.
    LaunchedEffect(googleMap, saved, current) {
        val map = googleMap ?: return@LaunchedEffect
        map.clear()

        saved.forEach { point ->
            map.addMarker(
                MarkerOptions()
                    .position(LatLng(point.latitude, point.longitude))
                    .title("%.5f, %.5f".format(Locale.US, point.latitude, point.longitude))
                    .snippet(if (point.synced) "Synced with Firestore" else "On device only"),
            )?.tag = point.id
        }

        current?.let { fix ->
            map.addMarker(
                MarkerOptions()
                    .position(LatLng(fix.latitude, fix.longitude))
                    .title("Current location")
                    .snippet("±${fix.accuracyMeters.toInt()} m"),
            )
        }
    }

    // Re-centre on the selected row, otherwise on the live fix.
    LaunchedEffect(googleMap, current, selectedId) {
        val map = googleMap ?: return@LaunchedEffect
        val target: LatLng = when {
            selectedId != null -> saved.firstOrNull { it.id == selectedId }
                ?.let { LatLng(it.latitude, it.longitude) }

            current != null -> LatLng(current.latitude, current.longitude)
            else -> null
        } ?: return@LaunchedEffect

        map.animateCamera(CameraUpdateFactory.newLatLngZoom(target, 16f), 600, null)
    }
}

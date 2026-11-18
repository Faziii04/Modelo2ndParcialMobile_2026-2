package com.example.segundoparcialmovil2026_2

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.example.segundoparcialmovil2026_2.ui.LocationScreen
import com.example.segundoparcialmovil2026_2.ui.LocationViewModel
import com.example.segundoparcialmovil2026_2.ui.theme.SegundoParcialTheme

class MainActivity : ComponentActivity() {

    private val viewModel: LocationViewModel by viewModels {
        LocationViewModel.factory(application)
    }

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        viewModel.refreshPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SegundoParcialTheme {
                LocationScreen(
                    viewModel = viewModel,
                    onRequestPermission = ::requestLocationPermission,
                )
            }
        }
    }

    private fun requestLocationPermission() {
        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            viewModel.refreshPermission()
            return
        }

        locationPermission.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
        )
    }
}

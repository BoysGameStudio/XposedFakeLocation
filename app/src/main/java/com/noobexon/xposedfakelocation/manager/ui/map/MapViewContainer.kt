package com.noobexon.xposedfakelocation.manager.ui.map

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.DEFAULT_MAP_ZOOM
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@Composable
fun MapViewContainer(
    mapViewModel: MapViewModel
) {
    val context = LocalContext.current
    val uiState by mapViewModel.uiState.collectAsStateWithLifecycle()

    // Extract state from uiState
    val isLoading = uiState.isLoading
    val lastClickedLocation = uiState.lastClickedLocation
    val isPlaying = uiState.isPlaying
    val mapZoom = uiState.mapZoom

    // Remember MapView and overlays
    val mapView = rememberMapView(context)
    val userMarker = rememberUserMarker(mapView)
    val locationOverlay = rememberLocationOverlay(context, mapView)

    // Add the location overlay to the map
    AddLocationOverlayToMap(mapView, locationOverlay)

    // Handle map events and updates
    HandleCenterMapEvent(mapView, locationOverlay, mapViewModel)
    HandleGoToPointEvent(mapView, mapViewModel)
    HandleMarkerUpdates(mapView, userMarker, lastClickedLocation)
    SetupMapClickListener(mapView, mapViewModel, isPlaying)
    CenterMapOnUserLocation(mapView, locationOverlay, mapViewModel, lastClickedLocation, mapZoom)
    ManageMapViewLifecycle(mapView, mapViewModel, locationOverlay)

    // Add MapListener to update zoom level
    DisposableEffect(mapView) {
        val mapListener = object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                // Optional: update map center if needed
                return false
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                // Update zoom state through proper ViewModel methods
                // This will be handled by the ViewModel's state update logic
                mapViewModel.updateMapZoom(mapView.zoomLevelDouble)
                return true
            }
        }
        mapView.addMapListener(mapListener)

        onDispose {
            mapView.removeMapListener(mapListener)
        }
    }

    // Display loading spinner or MapView
    if (isLoading) {
        LoadingSpinner()
    } else {
        DisplayMapView(mapView)
    }
}

@Composable
private fun rememberMapView(context: Context): MapView {
    return remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setBuiltInZoomControls(false)
            setMultiTouchControls(true)
            controller.setZoom(DEFAULT_MAP_ZOOM)
        }
    }
}

@Composable
private fun rememberUserMarker(mapView: MapView): Marker {
    return remember {
        Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
    }
}

@Composable
private fun rememberLocationOverlay(context: Context, mapView: MapView): MyLocationNewOverlay {
    return remember {
        MyLocationNewOverlay(GpsMyLocationProvider(context), mapView).apply {
            enableMyLocation()
        }
    }
}

@Composable
private fun LoadingSpinner() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.map_updating),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun DisplayMapView(mapView: MapView) {
    AndroidView(
        factory = { mapView },
        modifier = Modifier.fillMaxSize()
    )
}

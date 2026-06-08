package com.noobexon.xposedfakelocation.manager.ui.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.DEFAULT_MAP_ZOOM
import com.noobexon.xposedfakelocation.data.LOCATION_DETECTION_DELAY_MS
import com.noobexon.xposedfakelocation.data.LOCATION_DETECTION_MAX_ATTEMPTS
import com.noobexon.xposedfakelocation.data.WORLD_MAP_ZOOM
import kotlinx.coroutines.delay
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@Composable
internal fun AddLocationOverlayToMap(
    mapView: MapView,
    locationOverlay: MyLocationNewOverlay
) {
    LaunchedEffect(Unit) {
        if (!mapView.overlays.contains(locationOverlay)) {
            mapView.overlays.add(locationOverlay)
        }
    }
}

@Composable
internal fun HandleCenterMapEvent(
    mapView: MapView,
    locationOverlay: MyLocationNewOverlay,
    mapViewModel: MapViewModel
) {
    val context = LocalContext.current
    val userLocationNotAvailable = stringResource(R.string.toast_user_location_not_available)
    LaunchedEffect(Unit) {
        mapViewModel.centerMapEvent.collect {
            val userLocation = locationOverlay.myLocation
            if (userLocation != null) {
                mapView.controller.animateTo(userLocation)
            } else {
                Toast.makeText(context, userLocationNotAvailable, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
internal fun HandleGoToPointEvent(
    mapView: MapView,
    mapViewModel: MapViewModel
) {
    LaunchedEffect(Unit) {
        mapViewModel.goToPointEvent.collect { geoPoint ->
            mapView.controller.animateTo(geoPoint)
            mapViewModel.updateClickedLocation(geoPoint)
        }
    }
}

@Composable
internal fun HandleMarkerUpdates(
    mapView: MapView,
    userMarker: Marker,
    lastClickedLocation: GeoPoint?,
) {
    LaunchedEffect(lastClickedLocation) {
        if (lastClickedLocation != null) {
            // Add the marker to the map if not already added
            if (!mapView.overlays.contains(userMarker)) {
                mapView.overlays.add(userMarker)
            }
            userMarker.position = lastClickedLocation
            mapView.controller.animateTo(lastClickedLocation)
            mapView.invalidate()
        } else {
            // Remove the marker from the map if it exists
            if (mapView.overlays.contains(userMarker)) {
                mapView.overlays.remove(userMarker)
                mapView.invalidate()
            }
        }
    }
}

@Composable
internal fun SetupMapClickListener(
    mapView: MapView,
    mapViewModel: MapViewModel,
    isPlaying: Boolean
) {
    DisposableEffect(mapView, isPlaying) {
        val mapEventsReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                if (!isPlaying) {
                    mapViewModel.updateClickedLocation(p)
                }
                return true
            }

            override fun longPressHelper(p: GeoPoint): Boolean {
                return false
            }
        }

        val mapEventsOverlay = MapEventsOverlay(mapEventsReceiver)
        mapView.overlays.add(mapEventsOverlay)

        onDispose {
            mapView.overlays.remove(mapEventsOverlay)
        }
    }
}

@Composable
internal fun CenterMapOnUserLocation(
    mapView: MapView,
    locationOverlay: MyLocationNewOverlay,
    mapViewModel: MapViewModel,
    lastClickedLocation: GeoPoint?,
    mapZoom: Double?
) {
    val context = LocalContext.current
    LaunchedEffect(mapView, lastClickedLocation) {
        if (lastClickedLocation != null) {
            centerOnMarkerLocation(mapView, lastClickedLocation, mapZoom, mapViewModel)
        } else {
            val lastKnown = getLastKnownDeviceLocation(context)
            if (lastKnown != null) {
                centerOnGeoPoint(mapView, lastKnown, mapViewModel)
            } else if (!tryToFindAndCenterUserLocation(mapView, locationOverlay, mapViewModel)) {
                centerOnDefaultLocation(mapView, mapViewModel)
            }
        }
    }
}

/**
 * Centers the map on a specific marker location
 */
private suspend fun centerOnMarkerLocation(
    mapView: MapView,
    markerLocation: GeoPoint,
    mapZoom: Double?,
    mapViewModel: MapViewModel
) {
    val zoom = mapZoom ?: DEFAULT_MAP_ZOOM
    mapView.controller.setZoom(zoom)
    mapView.controller.animateTo(markerLocation)
    mapViewModel.updateMapZoom(zoom)
    mapViewModel.setLoadingFinished()
}

private fun centerOnGeoPoint(
    mapView: MapView,
    point: GeoPoint,
    mapViewModel: MapViewModel
) {
    mapView.controller.setZoom(DEFAULT_MAP_ZOOM)
    mapView.controller.setCenter(point)
    mapViewModel.updateUserLocation(point)
    mapViewModel.updateMapZoom(DEFAULT_MAP_ZOOM)
    mapViewModel.setLoadingFinished()
}

private fun getLastKnownDeviceLocation(context: Context): GeoPoint? {
    val granted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    if (!granted) return null

    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    val providers = try {
        lm.getProviders(true)
    } catch (e: SecurityException) {
        return null
    }
    var best: Location? = null
    for (provider in providers) {
        val loc = try {
            lm.getLastKnownLocation(provider)
        } catch (e: SecurityException) {
            null
        } ?: continue
        if (best == null || loc.time > best.time) best = loc
    }
    return best?.let { GeoPoint(it.latitude, it.longitude) }
}

/**
 * Attempts to find and center on the user's current location
 * @return true if user location was found, false otherwise
 */
private suspend fun tryToFindAndCenterUserLocation(
    mapView: MapView,
    locationOverlay: MyLocationNewOverlay,
    mapViewModel: MapViewModel
): Boolean {
    // Attempt to find user location within a timeout period
    repeat(LOCATION_DETECTION_MAX_ATTEMPTS) {
        val userLocation = locationOverlay.myLocation
        if (userLocation != null) {
            mapViewModel.updateUserLocation(userLocation)
            mapView.controller.setZoom(DEFAULT_MAP_ZOOM)
            mapView.controller.animateTo(userLocation)
            mapViewModel.updateMapZoom(DEFAULT_MAP_ZOOM)
            mapViewModel.setLoadingFinished()
            return true
        }
        delay(LOCATION_DETECTION_DELAY_MS)
    }
    return false
}

/**
 * Centers the map on a default world location when user location can't be found
 */
private fun centerOnDefaultLocation(
    mapView: MapView,
    mapViewModel: MapViewModel
) {
    // If location is not available after timeout, set default location
    mapView.controller.setZoom(WORLD_MAP_ZOOM)
    mapView.controller.setCenter(GeoPoint(0.0, 0.0))
    mapViewModel.updateMapZoom(WORLD_MAP_ZOOM)
    mapViewModel.setLoadingFinished()
}

@Composable
internal fun ManageMapViewLifecycle(
    mapView: MapView,
    mapViewModel: MapViewModel,
    locationOverlay: MyLocationNewOverlay
) {
    DisposableEffect(Unit) {
        mapView.onResume()
        locationOverlay.enableMyLocation()
        onDispose {
            locationOverlay.disableMyLocation()
            mapView.overlays.clear()
            mapView.onPause()
            mapView.onDetach()
            mapViewModel.setLoadingStarted()
        }
    }
}

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.DEFAULT_MAP_ZOOM
import com.noobexon.xposedfakelocation.data.LOCATION_DETECTION_DELAY_MS
import com.noobexon.xposedfakelocation.data.LOCATION_DETECTION_MAX_ATTEMPTS
import com.noobexon.xposedfakelocation.data.WORLD_MAP_ZOOM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
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
    centerMapEvent: Flow<Unit>
) {
    val context = LocalContext.current
    val userLocationNotAvailable = stringResource(R.string.toast_user_location_not_available)
    LaunchedEffect(Unit) {
        centerMapEvent.collect {
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
    goToPointEvent: Flow<GeoPoint>,
    onClickedLocationChange: (GeoPoint?) -> Unit
) {
    LaunchedEffect(Unit) {
        goToPointEvent.collect { geoPoint ->
            mapView.controller.animateTo(geoPoint)
            onClickedLocationChange(geoPoint)
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
    isPlaying: Boolean,
    onClickedLocationChange: (GeoPoint?) -> Unit
) {
    DisposableEffect(mapView, isPlaying) {
        val mapEventsReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                if (!isPlaying) {
                    onClickedLocationChange(p)
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

/**
 * Positions the camera once per [mapView].
 *
 * - **First ever load** ([hasResolvedInitialLocation] == false): resolves an initial target — saved
 *   marker, else last-known device location, else a short GPS poll, else a world-view fallback —
 *   clearing the loading state when done and reporting completion via [onInitialLocationResolved].
 * - **Re-entry** ([hasResolvedInitialLocation] == true): restores the last camera instantly with no
 *   spinner and no detection. The remembered [mapZoom] is re-applied; for the no-marker case the
 *   last [userLocation] is re-centered. Marker centering is left to [HandleMarkerUpdates].
 *
 * Either way, once positioned it never re-centers again for this [mapView], so later marker changes
 * (taps, go-to-point, clear) don't reset the zoom. The effect is keyed on [lastClickedLocation] so
 * an asynchronously-loaded saved marker can supersede an in-flight device-location lookup.
 */
@Composable
internal fun CenterMapOnUserLocation(
    mapView: MapView,
    locationOverlay: MyLocationNewOverlay,
    lastClickedLocation: GeoPoint?,
    userLocation: GeoPoint?,
    mapZoom: Double?,
    hasResolvedInitialLocation: Boolean,
    onUserLocationChange: (GeoPoint) -> Unit,
    onMapZoomChange: (Double) -> Unit,
    onLoadingFinished: () -> Unit,
    onInitialLocationResolved: () -> Unit,
) {
    val context = LocalContext.current
    val centeredOnce = remember(mapView) { mutableStateOf(false) }
    LaunchedEffect(mapView, lastClickedLocation) {
        if (centeredOnce.value) return@LaunchedEffect

        if (hasResolvedInitialLocation) {
            // Re-entry: restore the last camera without re-detecting or showing a spinner.
            mapView.controller.setZoom(mapZoom ?: DEFAULT_MAP_ZOOM)
            if (lastClickedLocation == null && userLocation != null) {
                mapView.controller.setCenter(userLocation)
            }
            centeredOnce.value = true
            return@LaunchedEffect
        }

        if (lastClickedLocation != null) {
            centerOnMarkerLocation(mapView, lastClickedLocation, mapZoom, onMapZoomChange, onLoadingFinished)
        } else {
            val lastKnown = getLastKnownDeviceLocation(context)
            if (lastKnown != null) {
                centerOnGeoPoint(mapView, lastKnown, onUserLocationChange, onMapZoomChange, onLoadingFinished)
            } else {
                val found = tryToFindAndCenterUserLocation(mapView, locationOverlay, onUserLocationChange, onMapZoomChange, onLoadingFinished)
                if (!found) {
                    centerOnDefaultLocation(mapView, onMapZoomChange, onLoadingFinished)
                }
            }
        }
        onInitialLocationResolved()
        centeredOnce.value = true
    }
}

/**
 * Centers the map on a specific marker location
 */
private suspend fun centerOnMarkerLocation(
    mapView: MapView,
    markerLocation: GeoPoint,
    mapZoom: Double?,
    onMapZoomChange: (Double) -> Unit,
    onLoadingFinished: () -> Unit
) {
    val zoom = mapZoom ?: DEFAULT_MAP_ZOOM
    mapView.controller.setZoom(zoom)
    mapView.controller.animateTo(markerLocation)
    onMapZoomChange(zoom)
    onLoadingFinished()
}

private fun centerOnGeoPoint(
    mapView: MapView,
    point: GeoPoint,
    onUserLocationChange: (GeoPoint) -> Unit,
    onMapZoomChange: (Double) -> Unit,
    onLoadingFinished: () -> Unit
) {
    mapView.controller.setZoom(DEFAULT_MAP_ZOOM)
    mapView.controller.setCenter(point)
    onUserLocationChange(point)
    onMapZoomChange(DEFAULT_MAP_ZOOM)
    onLoadingFinished()
}

private suspend fun getLastKnownDeviceLocation(context: Context): GeoPoint? = withContext(Dispatchers.IO) {
    val granted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    if (!granted) return@withContext null

    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: return@withContext null
    val providers = try {
        lm.getProviders(true)
    } catch (e: SecurityException) {
        return@withContext null
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
    best?.let { GeoPoint(it.latitude, it.longitude) }
}

/**
 * Attempts to find and center on the user's current location
 * @return true if user location was found, false otherwise
 */
private suspend fun tryToFindAndCenterUserLocation(
    mapView: MapView,
    locationOverlay: MyLocationNewOverlay,
    onUserLocationChange: (GeoPoint) -> Unit,
    onMapZoomChange: (Double) -> Unit,
    onLoadingFinished: () -> Unit
): Boolean {
    // Attempt to find user location within a timeout period
    repeat(LOCATION_DETECTION_MAX_ATTEMPTS) {
        val userLocation = locationOverlay.myLocation
        if (userLocation != null) {
            onUserLocationChange(userLocation)
            mapView.controller.setZoom(DEFAULT_MAP_ZOOM)
            mapView.controller.animateTo(userLocation)
            onMapZoomChange(DEFAULT_MAP_ZOOM)
            onLoadingFinished()
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
    onMapZoomChange: (Double) -> Unit,
    onLoadingFinished: () -> Unit
) {
    // If location is not available after timeout, set default location
    mapView.controller.setZoom(WORLD_MAP_ZOOM)
    mapView.controller.setCenter(GeoPoint(0.0, 0.0))
    onMapZoomChange(WORLD_MAP_ZOOM)
    onLoadingFinished()
}

@Composable
internal fun ManageMapViewLifecycle(
    mapView: MapView,
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
        }
    }
}

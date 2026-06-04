package com.noobexon.xposedfakelocation.manager.ui.map

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.model.FavoriteLocation
import com.noobexon.xposedfakelocation.data.repository.PreferencesRepository
import com.noobexon.xposedfakelocation.manager.export.LocationBaseInfoExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint

/**
 * Sealed classes to represent different dialog states
 */
sealed class DialogState {
    object Hidden : DialogState()
    object Visible : DialogState()
}

/**
 * Sealed class to represent different loading states
 */
sealed class LoadingState {
    object Loading : LoadingState()
    object Loaded : LoadingState()
}

/**
 * ViewModel for the Map screen that manages map-related state and operations.
 */
class MapViewModel(application: Application) : AndroidViewModel(application) {
    private val preferencesRepository = PreferencesRepository(application)
    private val locationBaseInfoExporter by lazy {
        LocationBaseInfoExporter()
    }

    /**
     * Represents field input state with value and validation error message
     */
    data class InputFieldState(val value: String = "", @StringRes val errorMessageRes: Int? = null)

    /**
     * Represents the UI state for the favorites input dialog
     */
    data class FavoritesInputState(
        val name: InputFieldState = InputFieldState(),
        val latitude: InputFieldState = InputFieldState(),
        val longitude: InputFieldState = InputFieldState()
    )

    /**
     * Represents the complete UI state for the Map screen
     */
    data class MapUiState(
        val isPlaying: Boolean = false,
        val lastClickedLocation: GeoPoint? = null,
        val userLocation: GeoPoint? = null,
        val loadingState: LoadingState = LoadingState.Loading,
        val mapZoom: Double? = null,
        val goToPointDialogState: DialogState = DialogState.Hidden,
        val addToFavoritesState: FavoritesInputState = FavoritesInputState(),
        val addToFavoritesDialogState: DialogState = DialogState.Hidden,
        val goToPointState: Pair<InputFieldState, InputFieldState> = InputFieldState() to InputFieldState(),
    ) {
        val isFabClickable: Boolean
            get() = lastClickedLocation != null
    }

    // Private mutable state
    private val _uiState = MutableStateFlow(MapUiState())
    
    // Public immutable state
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    // Events
    private val _goToPointEvent = MutableSharedFlow<GeoPoint>()
    val goToPointEvent: SharedFlow<GeoPoint> = _goToPointEvent.asSharedFlow()

    private val _centerMapEvent = MutableSharedFlow<Unit>()
    val centerMapEvent: SharedFlow<Unit> = _centerMapEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            // Load initial isPlaying state
            preferencesRepository.getIsPlayingFlow().collectLatest { isPlaying ->
                _uiState.update { it.copy(isPlaying = isPlaying) }
            }
        }
        
        viewModelScope.launch {
            // Load initial lastClickedLocation
            preferencesRepository.getLastClickedLocationFlow().collectLatest { location ->
                val geoPoint = location?.let { GeoPoint(it.latitude, it.longitude) }
                _uiState.update { it.copy(lastClickedLocation = geoPoint) }
            }
        }
    }

    fun togglePlaying() {
        val currentIsPlaying = !_uiState.value.isPlaying
        _uiState.update { it.copy(isPlaying = currentIsPlaying) }
        
        viewModelScope.launch {
            preferencesRepository.saveIsPlaying(currentIsPlaying)
        }
    }

    fun updateUserLocation(location: GeoPoint) {
        _uiState.update { it.copy(userLocation = location) }
    }

    fun updateClickedLocation(geoPoint: GeoPoint?) {
        _uiState.update { it.copy(lastClickedLocation = geoPoint) }
        
        viewModelScope.launch {
            geoPoint?.let {
                preferencesRepository.saveLastClickedLocation(
                    it.latitude,
                    it.longitude
                )
            } ?: preferencesRepository.clearLastClickedLocation()
        }
    }

    suspend fun exportSelectedLocationBaseInfo(): LocationBaseInfoExporter.ExportResult {
        return exportCurrentLocationBaseInfo()
    }

    suspend fun exportCurrentLocationBaseInfo(): LocationBaseInfoExporter.ExportResult = withContext(Dispatchers.IO) {
        val context = getApplication<Application>()
        if (!context.hasLocationPermission()) {
            return@withContext LocationBaseInfoExporter.ExportResult.MissingLocationPermission
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext LocationBaseInfoExporter.ExportResult.NoRealLocation

        val realLocation = try {
            locationManager.newestBestLastKnownLocation()
        } catch (exception: SecurityException) {
            return@withContext LocationBaseInfoExporter.ExportResult.MissingLocationPermission
        } ?: return@withContext LocationBaseInfoExporter.ExportResult.NoRealLocation

        locationBaseInfoExporter.exportToAppSpecificExternalStorage(
            context = context,
            location = realLocation.toRealLocationSnapshot()
        )
    }

    private fun Context.hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun LocationManager.newestBestLastKnownLocation(): Location? {
        return getProviders(true)
            .mapNotNull { provider -> getLastKnownLocation(provider) }
            .reduceOrNull { bestLocation, candidateLocation ->
                if (candidateLocation.isBetterExportLocationThan(bestLocation)) candidateLocation else bestLocation
            }
    }

    private fun Location.isBetterExportLocationThan(currentBest: Location): Boolean {
        if (time != currentBest.time) {
            return time > currentBest.time
        }

        if (hasAccuracy() && currentBest.hasAccuracy()) {
            return accuracy < currentBest.accuracy
        }

        return hasAccuracy() && !currentBest.hasAccuracy()
    }

    private fun Location.toRealLocationSnapshot(): LocationBaseInfoExporter.RealLocationSnapshot {
        val (supportedExtras, unsupportedExtraKeys) = extras.toSupportedExtras()
        return LocationBaseInfoExporter.RealLocationSnapshot(
            provider = provider,
            latitude = latitude,
            longitude = longitude,
            timeMillis = time,
            elapsedRealtimeNanos = elapsedRealtimeNanos,
            hasElapsedRealtimeUncertaintyNanos = hasElapsedRealtimeUncertaintyNanos(),
            elapsedRealtimeUncertaintyNanos = if (hasElapsedRealtimeUncertaintyNanos()) elapsedRealtimeUncertaintyNanos else null,
            hasAltitude = hasAltitude(),
            altitudeMeters = if (hasAltitude()) altitude else null,
            hasAccuracy = hasAccuracy(),
            accuracyMeters = if (hasAccuracy()) accuracy else null,
            hasSpeed = hasSpeed(),
            speedMetersPerSecond = if (hasSpeed()) speed else null,
            hasBearing = hasBearing(),
            bearingDegrees = if (hasBearing()) bearing else null,
            hasVerticalAccuracy = hasVerticalAccuracy(),
            verticalAccuracyMeters = if (hasVerticalAccuracy()) verticalAccuracyMeters else null,
            hasSpeedAccuracy = hasSpeedAccuracy(),
            speedAccuracyMetersPerSecond = if (hasSpeedAccuracy()) speedAccuracyMetersPerSecond else null,
            hasBearingAccuracy = hasBearingAccuracy(),
            bearingAccuracyDegrees = if (hasBearingAccuracy()) bearingAccuracyDegrees else null,
            hasMslAltitude = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && hasMslAltitude(),
            mslAltitudeMeters = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && hasMslAltitude()) mslAltitudeMeters else null,
            hasMslAltitudeAccuracy = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && hasMslAltitudeAccuracy(),
            mslAltitudeAccuracyMeters = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && hasMslAltitudeAccuracy()) mslAltitudeAccuracyMeters else null,
            isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) isMock else isFromMockProvider,
            extras = supportedExtras,
            extrasUnsupportedKeys = unsupportedExtraKeys
        )
    }

    private fun Bundle?.toSupportedExtras(): Pair<Map<String, Any?>, List<String>> {
        if (this == null || isEmpty) {
            return emptyMap<String, Any?>() to emptyList()
        }

        val supportedExtras = linkedMapOf<String, Any?>()
        val unsupportedExtraKeys = mutableListOf<String>()
        keySet().forEach { key ->
            val value = get(key)
            val jsonValue = value.toJsonSupportedExtraValue()
            if (jsonValue != UnsupportedExtraValue) {
                supportedExtras[key] = jsonValue
            } else {
                unsupportedExtraKeys += key
            }
        }
        return supportedExtras to unsupportedExtraKeys
    }

    private fun Any?.toJsonSupportedExtraValue(): Any? {
        return when (this) {
            null,
            is String,
            is Boolean,
            is Byte,
            is Short,
            is Int,
            is Long,
            is Float,
            is Double -> this
            is Char -> toString()
            is BooleanArray -> toList()
            is ByteArray -> toList()
            is ShortArray -> toList()
            is IntArray -> toList()
            is LongArray -> toList()
            is FloatArray -> toList()
            is DoubleArray -> toList()
            is CharArray -> map { it.toString() }
            is Array<*> -> mapSupportedExtraArray() ?: UnsupportedExtraValue
            else -> UnsupportedExtraValue
        }
    }

    private fun Array<*>.mapSupportedExtraArray(): List<Any?>? {
        return map { value ->
            val jsonValue = value.toJsonSupportedExtraValue()
            if (jsonValue == UnsupportedExtraValue) return null
            jsonValue
        }
    }

    private data object UnsupportedExtraValue

    fun addFavoriteLocation(favoriteLocation: FavoriteLocation) {
        viewModelScope.launch {
            preferencesRepository.addFavorite(favoriteLocation)
        }
    }

    // Update specific fields in the FavoritesInputState
    fun updateAddToFavoritesField(fieldName: String, newValue: String) {
        val currentState = _uiState.value.addToFavoritesState
        val errorMessageRes = when (fieldName) {
            "name" -> if (newValue.isBlank()) R.string.validation_name_required else null
            "latitude" -> validateInput(newValue, -90.0..90.0, R.string.validation_latitude_range)
            "longitude" -> validateInput(newValue, -180.0..180.0, R.string.validation_longitude_range)
            else -> null
        }

        val updatedState = when (fieldName) {
            "name" -> currentState.copy(name = currentState.name.copy(value = newValue, errorMessageRes = errorMessageRes))
            "latitude" -> currentState.copy(latitude = currentState.latitude.copy(value = newValue, errorMessageRes = errorMessageRes))
            "longitude" -> currentState.copy(longitude = currentState.longitude.copy(value = newValue, errorMessageRes = errorMessageRes))
            else -> currentState
        }
        
        _uiState.update { it.copy(addToFavoritesState = updatedState) }
    }

    // Go to point logic
    fun goToPoint(latitude: Double, longitude: Double) {
        viewModelScope.launch {
            _goToPointEvent.emit(GeoPoint(latitude, longitude))
        }
    }

    // Update specific fields in the GoToPointDialog state
    fun updateGoToPointField(fieldName: String, newValue: String) {
        val (latitudeField, longitudeField) = _uiState.value.goToPointState
        val updatedGoToPointState = when (fieldName) {
            "latitude" -> latitudeField.copy(value = newValue) to longitudeField
            "longitude" -> latitudeField to longitudeField.copy(value = newValue)
            else -> latitudeField to longitudeField
        }
        
        _uiState.update { it.copy(goToPointState = updatedGoToPointState) }
    }

    // Center map
    fun triggerCenterMapEvent() {
        viewModelScope.launch {
            _centerMapEvent.emit(Unit)
        }
    }

    fun setLoadingStarted() {
        _uiState.update { it.copy(loadingState = LoadingState.Loading) }
    }

    // Set loading finished
    fun setLoadingFinished() {
        _uiState.update { it.copy(loadingState = LoadingState.Loaded) }
    }

    // Dialog show/hide logic
    fun showGoToPointDialog() { 
        _uiState.update { it.copy(goToPointDialogState = DialogState.Visible) }
    }
    
    fun hideGoToPointDialog() {
        _uiState.update { it.copy(goToPointDialogState = DialogState.Hidden) }
        clearGoToPointInputs()
    }

    fun showAddToFavoritesDialog() { 
        _uiState.update { it.copy(addToFavoritesDialogState = DialogState.Visible) }
    }
    
    fun hideAddToFavoritesDialog() {
        _uiState.update { it.copy(addToFavoritesDialogState = DialogState.Hidden) }
        clearAddToFavoritesInputs()
    }

    // Helper for input validation
    private fun validateInput(
        input: String, range: ClosedRange<Double>, @StringRes errorMessageRes: Int
    ): Int? {
        val value = input.toDoubleOrNull()
        return if (value == null || value !in range) errorMessageRes else null
    }

    // Validate GoToPoint inputs
    fun validateAndGo(onSuccess: (latitude: Double, longitude: Double) -> Unit) {
        val (latField, lonField) = _uiState.value.goToPointState
        val latitudeError = validateInput(latField.value, -90.0..90.0, R.string.validation_latitude_range)
        val longitudeError = validateInput(lonField.value, -180.0..180.0, R.string.validation_longitude_range)

        val updatedGoToPointState = latField.copy(errorMessageRes = latitudeError) to lonField.copy(errorMessageRes = longitudeError)
        _uiState.update { it.copy(goToPointState = updatedGoToPointState) }

        if (latitudeError == null && longitudeError == null) {
            onSuccess(latField.value.toDouble(), lonField.value.toDouble())
        }
    }

    // Clear GoToPoint inputs
    fun clearGoToPointInputs() {
        _uiState.update { 
            it.copy(goToPointState = InputFieldState() to InputFieldState())
        }
    }

    // Prefill AddToFavorites latitude/longitude with marker values (if available)
    fun prefillCoordinatesFromMarker(latitude: Double?, longitude: Double?) {
        if (latitude != null && longitude != null) {
            val latField = InputFieldState(value = latitude.toString())
            val lngField = InputFieldState(value = longitude.toString())
            
            _uiState.update { currentState ->
                val favState = currentState.addToFavoritesState
                currentState.copy(
                    addToFavoritesState = favState.copy(
                        latitude = latField,
                        longitude = lngField
                    )
                )
            }
        }
    }

    // Validate and add favorite location
    fun validateAndAddFavorite(onSuccess: (name: String, latitude: Double, longitude: Double) -> Unit) {
        val currentState = _uiState.value.addToFavoritesState

        val latitudeError = validateInput(currentState.latitude.value, -90.0..90.0, R.string.validation_latitude_range)
        val longitudeError = validateInput(currentState.longitude.value, -180.0..180.0, R.string.validation_longitude_range)
        val nameError = if (currentState.name.value.isBlank()) R.string.validation_name_required else null

        val updatedState = currentState.copy(
            name = currentState.name.copy(errorMessageRes = nameError),
            latitude = currentState.latitude.copy(errorMessageRes = latitudeError),
            longitude = currentState.longitude.copy(errorMessageRes = longitudeError)
        )
        
        _uiState.update { it.copy(addToFavoritesState = updatedState) }

        if (nameError == null && latitudeError == null && longitudeError == null) {
            onSuccess(currentState.name.value, currentState.latitude.value.toDouble(), currentState.longitude.value.toDouble())
        }
    }

    // Clear AddToFavorites inputs
    fun clearAddToFavoritesInputs() {
        _uiState.update { it.copy(addToFavoritesState = FavoritesInputState()) }
    }
    
    // Update map zoom level
    fun updateMapZoom(zoom: Double) {
        _uiState.update { it.copy(mapZoom = zoom) }
    }
}

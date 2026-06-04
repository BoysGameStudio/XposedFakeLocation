package com.noobexon.xposedfakelocation.manager.ui.map

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.model.FavoriteLocation
import com.noobexon.xposedfakelocation.data.model.signalbaseline.SavedLocationProfile
import com.noobexon.xposedfakelocation.data.model.signalbaseline.SavedLocationProfileCodec
import com.noobexon.xposedfakelocation.data.model.signalbaseline.SignalBaselineSnapshot
import com.noobexon.xposedfakelocation.data.repository.PreferencesRepository
import com.noobexon.xposedfakelocation.manager.baseline.SignalBaselineCapture
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
    private val signalBaselineMenuActions = SignalBaselineMenuActions(
        hasLocationPermission = { getApplication<Application>().hasLocationPermission() },
        captureBaseline = { SignalBaselineCapture(getApplication<Application>()).capture() },
        baselineStore = PreferencesSignalBaselineStore(preferencesRepository)
    )

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
        val signalBaselineDetailsDialogState: DialogState = DialogState.Hidden,
        val savedLocationProfilesDialogState: DialogState = DialogState.Hidden,
        val signalBaseline: SignalBaselineSnapshot? = null,
        val savedLocationProfiles: List<SavedLocationProfile> = emptyList(),
        val selectedSavedLocationProfile: SavedLocationProfile? = null,
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

        viewModelScope.launch {
            preferencesRepository.signalBaselineFlow().collectLatest { baseline ->
                _uiState.update { it.copy(signalBaseline = baseline) }
            }
        }

        viewModelScope.launch {
            preferencesRepository.getSavedLocationProfilesFlow().collectLatest { profiles ->
                _uiState.update { state ->
                    state.copy(
                        savedLocationProfiles = profiles,
                        selectedSavedLocationProfile = state.selectedSavedLocationProfile
                            ?.let { selected -> profiles.firstOrNull { it.id == selected.id } }
                    )
                }
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

    suspend fun saveRealEnvironmentBaseline(profileLabel: String? = null): SignalBaselineToastMessage = withContext(Dispatchers.IO) {
        signalBaselineMenuActions.saveRealEnvironmentBaseline(profileLabel)
    }

    suspend fun clearRealEnvironmentBaseline(): SignalBaselineToastMessage = withContext(Dispatchers.IO) {
        signalBaselineMenuActions.clearRealEnvironmentBaseline()
    }

    suspend fun exportSavedLocationProfiles(uri: Uri): SignalBaselineToastMessage = withContext(Dispatchers.IO) {
        val json = preferencesRepository.exportSavedLocationProfilesJson()
            ?: return@withContext SignalBaselineToastMessage(R.string.toast_location_profiles_export_failed)
        val profileCount = preferencesRepository.getSavedLocationProfiles().size
        val exported = runCatching {
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(json.toByteArray(Charsets.UTF_8))
            } ?: error("output_stream_unavailable")
        }.isSuccess

        if (exported) {
            SignalBaselineToastMessage(R.string.toast_location_profiles_export_success, listOf(profileCount))
        } else {
            SignalBaselineToastMessage(R.string.toast_location_profiles_export_failed)
        }
    }

    suspend fun importSavedLocationProfiles(uri: Uri): SignalBaselineToastMessage = withContext(Dispatchers.IO) {
        val json = runCatching {
            getApplication<Application>().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull() ?: return@withContext SignalBaselineToastMessage(R.string.toast_location_profiles_import_failed)

        val importedCount = preferencesRepository.importSavedLocationProfilesJson(json)
            ?: return@withContext SignalBaselineToastMessage(R.string.toast_location_profiles_import_failed)

        SignalBaselineToastMessage(R.string.toast_location_profiles_import_success, listOf(importedCount))
    }

    suspend fun useSavedLocationProfile(profile: SavedLocationProfile): SignalBaselineToastMessage {
        val activated = withContext(Dispatchers.IO) {
            preferencesRepository.saveSignalBaseline(profile.baseline)
        }
        if (!activated) return SignalBaselineToastMessage(R.string.toast_location_profile_use_failed)

        val location = profile.baseline.location
        preferencesRepository.saveLastClickedLocation(location.latitude, location.longitude)
        _goToPointEvent.emit(GeoPoint(location.latitude, location.longitude))
        _uiState.update { it.copy(lastClickedLocation = GeoPoint(location.latitude, location.longitude)) }
        return SignalBaselineToastMessage(R.string.toast_location_profile_use_success)
    }

    suspend fun deleteSavedLocationProfile(profile: SavedLocationProfile): SignalBaselineToastMessage = withContext(Dispatchers.IO) {
        if (preferencesRepository.removeSavedLocationProfile(profile.id)) {
            SignalBaselineToastMessage(R.string.toast_location_profile_delete_success)
        } else {
            SignalBaselineToastMessage(R.string.toast_location_profile_delete_failed)
        }
    }

    suspend fun renameSavedLocationProfile(
        profile: SavedLocationProfile,
        label: String
    ): SignalBaselineToastMessage = withContext(Dispatchers.IO) {
        val normalizedLabel = label.trim()
        if (normalizedLabel.isEmpty()) {
            return@withContext SignalBaselineToastMessage(R.string.toast_location_profile_rename_failed)
        }

        if (preferencesRepository.saveLocationProfile(profile.copy(label = normalizedLabel))) {
            SignalBaselineToastMessage(R.string.toast_location_profile_rename_success)
        } else {
            SignalBaselineToastMessage(R.string.toast_location_profile_rename_failed)
        }
    }

    private fun Context.hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

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

    fun showSignalBaselineDetailsDialog() {
        _uiState.update { it.copy(signalBaselineDetailsDialogState = DialogState.Visible) }
    }

    fun hideSignalBaselineDetailsDialog() {
        _uiState.update { it.copy(signalBaselineDetailsDialogState = DialogState.Hidden) }
    }

    fun showSavedLocationProfilesDialog() {
        _uiState.update { it.copy(savedLocationProfilesDialogState = DialogState.Visible) }
    }

    fun hideSavedLocationProfilesDialog() {
        _uiState.update {
            it.copy(
                savedLocationProfilesDialogState = DialogState.Hidden,
                selectedSavedLocationProfile = null
            )
        }
    }

    fun selectSavedLocationProfile(profile: SavedLocationProfile) {
        _uiState.update { it.copy(selectedSavedLocationProfile = profile) }
    }

    fun showSavedLocationProfilesList() {
        _uiState.update { it.copy(selectedSavedLocationProfile = null) }
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

data class SignalBaselineToastMessage(
    @StringRes val messageRes: Int,
    val formatArgs: List<Any> = emptyList()
)

internal class SignalBaselineMenuActions(
    private val hasLocationPermission: () -> Boolean,
    private val captureBaseline: () -> SignalBaselineCapture.CaptureResult,
    private val baselineStore: SignalBaselineStore
) {
    suspend fun saveRealEnvironmentBaseline(profileLabel: String? = null): SignalBaselineToastMessage {
        if (!hasLocationPermission()) {
            return SignalBaselineToastMessage(R.string.toast_signal_baseline_save_missing_permission)
        }

        return when (val result = captureBaseline()) {
            SignalBaselineCapture.CaptureResult.NoRealLocation ->
                SignalBaselineToastMessage(R.string.toast_signal_baseline_save_no_location)
            is SignalBaselineCapture.CaptureResult.Success -> saveBaseline(result.snapshot, profileLabel)
        }
    }

    suspend fun clearRealEnvironmentBaseline(): SignalBaselineToastMessage {
        return if (baselineStore.clearSignalBaseline()) {
            SignalBaselineToastMessage(R.string.toast_signal_baseline_clear_success)
        } else {
            SignalBaselineToastMessage(R.string.toast_signal_baseline_clear_failed)
        }
    }

    private suspend fun saveBaseline(snapshot: SignalBaselineSnapshot, profileLabel: String?): SignalBaselineToastMessage {
        if (!baselineStore.saveSignalBaseline(snapshot)) {
            return SignalBaselineToastMessage(R.string.toast_signal_baseline_save_failed)
        }
        val profile = profileLabel
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { SavedLocationProfileCodec.createProfile(snapshot, label = it) }
            ?: SavedLocationProfileCodec.createProfile(snapshot)
        if (!baselineStore.saveLocationProfile(profile)) {
            return SignalBaselineToastMessage(R.string.toast_signal_baseline_save_failed)
        }

        return SignalBaselineToastMessage(
            messageRes = R.string.toast_signal_baseline_save_success,
            formatArgs = listOf(
                snapshot.cellular.cellInfoCount,
                snapshot.wifi.scanResultCount
            )
        )
    }
}

internal interface SignalBaselineStore {
    suspend fun saveSignalBaseline(snapshot: SignalBaselineSnapshot): Boolean
    suspend fun saveLocationProfile(profile: SavedLocationProfile): Boolean
    suspend fun clearSignalBaseline(): Boolean
}

private class PreferencesSignalBaselineStore(
    private val preferencesRepository: PreferencesRepository
) : SignalBaselineStore {
    override suspend fun saveSignalBaseline(snapshot: SignalBaselineSnapshot): Boolean {
        return preferencesRepository.saveSignalBaseline(snapshot)
    }

    override suspend fun saveLocationProfile(profile: SavedLocationProfile): Boolean {
        return preferencesRepository.saveLocationProfile(profile)
    }

    override suspend fun clearSignalBaseline(): Boolean {
        return preferencesRepository.clearSignalBaseline()
    }
}

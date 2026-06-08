package com.noobexon.xposedfakelocation.manager.ui.map

import androidx.annotation.StringRes
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

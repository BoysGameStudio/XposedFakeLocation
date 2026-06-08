package com.noobexon.xposedfakelocation.manager.ui.map

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noobexon.xposedfakelocation.R

@Composable
fun GoToPointDialog(
    mapViewModel: MapViewModel,
    onDismissRequest: () -> Unit,
    onGoToPoint: (latitude: Double, longitude: Double) -> Unit
) {
    val uiState by mapViewModel.uiState.collectAsStateWithLifecycle()
    val goToPointState = uiState.goToPointState
    val latitudeInput = goToPointState.first.value
    val longitudeInput = goToPointState.second.value
    val latitudeError = goToPointState.first.errorMessageRes
    val longitudeError = goToPointState.second.errorMessageRes

    AlertDialog(
        onDismissRequest = {
            mapViewModel.clearGoToPointInputs()
            onDismissRequest()
        },
        title = { Text(stringResource(R.string.map_go_to_point)) },
        text = {
            Column {
                OutlinedTextField(
                    value = latitudeInput,
                    onValueChange = { mapViewModel.updateGoToPointField("latitude", it) },
                    label = { Text(stringResource(R.string.field_latitude)) },
                    isError = latitudeError != null,
                    modifier = Modifier.fillMaxWidth()
                )
                if (latitudeError != null) {
                    Text(
                        text = stringResource(latitudeError),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = longitudeInput,
                    onValueChange = { mapViewModel.updateGoToPointField("longitude", it) },
                    label = { Text(stringResource(R.string.field_longitude)) },
                    isError = longitudeError != null,
                    modifier = Modifier.fillMaxWidth()
                )
                if (longitudeError != null) {
                    Text(
                        text = stringResource(longitudeError),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    mapViewModel.validateAndGo { latitude, longitude ->
                        onGoToPoint(latitude, longitude)
                    }
                }
            ) {
                Text(stringResource(R.string.action_go))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    mapViewModel.clearGoToPointInputs()
                    onDismissRequest()
                }
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
fun AddToFavoritesDialog(
    mapViewModel: MapViewModel,
    onDismissRequest: () -> Unit,
    onAddFavorite: (name: String, latitude: Double, longitude: Double) -> Unit
) {
    val uiState by mapViewModel.uiState.collectAsStateWithLifecycle()
    val addToFavoritesState = uiState.addToFavoritesState
    val favoriteNameInput = addToFavoritesState.name.value
    val favoriteLatitudeInput = addToFavoritesState.latitude.value
    val favoriteLongitudeInput = addToFavoritesState.longitude.value
    val favoriteNameError = addToFavoritesState.name.errorMessageRes
    val favoriteLatitudeError = addToFavoritesState.latitude.errorMessageRes
    val favoriteLongitudeError = addToFavoritesState.longitude.errorMessageRes

    AlertDialog(
        onDismissRequest = {
            mapViewModel.clearAddToFavoritesInputs()
            onDismissRequest()
        },
        title = { Text(stringResource(R.string.map_add_to_favorites)) },
        text = {
            Column {
                OutlinedTextField(
                    value = favoriteNameInput,
                    onValueChange = { mapViewModel.updateAddToFavoritesField("name", it) },
                    label = { Text(stringResource(R.string.field_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    isError = favoriteNameError != null
                )
                if (favoriteNameError != null) {
                    Text(
                        text = stringResource(favoriteNameError),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = favoriteLatitudeInput,
                    onValueChange = { mapViewModel.updateAddToFavoritesField("latitude", it) },
                    label = { Text(stringResource(R.string.field_latitude)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                    isError = favoriteLatitudeError != null
                )
                if (favoriteLatitudeError != null) {
                    Text(
                        text = stringResource(favoriteLatitudeError),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = favoriteLongitudeInput,
                    onValueChange = { mapViewModel.updateAddToFavoritesField("longitude", it) },
                    label = { Text(stringResource(R.string.field_longitude)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                    isError = favoriteLongitudeError != null
                )
                if (favoriteLongitudeError != null) {
                    Text(
                        text = stringResource(favoriteLongitudeError),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    mapViewModel.validateAndAddFavorite { name, latitude, longitude ->
                        onAddFavorite(name, latitude, longitude)
                    }
                }
            ) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    mapViewModel.clearAddToFavoritesInputs()
                    onDismissRequest()
                }
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

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
    onDismissRequest: () -> Unit
) {
    val uiState by mapViewModel.uiState.collectAsStateWithLifecycle()
    val goToPointState = uiState.goToPointState

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.map_go_to_point)) },
        text = {
            Column {
                OutlinedTextField(
                    value = goToPointState.latitude.value,
                    onValueChange = { mapViewModel.onGoToPointLatitudeChange(it) },
                    label = { Text(stringResource(R.string.field_latitude)) },
                    isError = goToPointState.latitude.errorMessageRes != null,
                    modifier = Modifier.fillMaxWidth()
                )
                goToPointState.latitude.errorMessageRes?.let { errorRes ->
                    Text(
                        text = stringResource(errorRes),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = goToPointState.longitude.value,
                    onValueChange = { mapViewModel.onGoToPointLongitudeChange(it) },
                    label = { Text(stringResource(R.string.field_longitude)) },
                    isError = goToPointState.longitude.errorMessageRes != null,
                    modifier = Modifier.fillMaxWidth()
                )
                goToPointState.longitude.errorMessageRes?.let { errorRes ->
                    Text(
                        text = stringResource(errorRes),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { mapViewModel.confirmGoToPoint() }) {
                Text(stringResource(R.string.action_go))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
fun AddToFavoritesDialog(
    mapViewModel: MapViewModel,
    onDismissRequest: () -> Unit
) {
    val uiState by mapViewModel.uiState.collectAsStateWithLifecycle()
    val addToFavoritesState = uiState.addToFavoritesState

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.map_add_to_favorites)) },
        text = {
            Column {
                OutlinedTextField(
                    value = addToFavoritesState.name.value,
                    onValueChange = { mapViewModel.onFavoriteNameChange(it) },
                    label = { Text(stringResource(R.string.field_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    isError = addToFavoritesState.name.errorMessageRes != null
                )
                addToFavoritesState.name.errorMessageRes?.let { errorRes ->
                    Text(
                        text = stringResource(errorRes),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = addToFavoritesState.latitude.value,
                    onValueChange = { mapViewModel.onFavoriteLatitudeChange(it) },
                    label = { Text(stringResource(R.string.field_latitude)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                    isError = addToFavoritesState.latitude.errorMessageRes != null
                )
                addToFavoritesState.latitude.errorMessageRes?.let { errorRes ->
                    Text(
                        text = stringResource(errorRes),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = addToFavoritesState.longitude.value,
                    onValueChange = { mapViewModel.onFavoriteLongitudeChange(it) },
                    label = { Text(stringResource(R.string.field_longitude)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                    isError = addToFavoritesState.longitude.errorMessageRes != null
                )
                addToFavoritesState.longitude.errorMessageRes?.let { errorRes ->
                    Text(
                        text = stringResource(errorRes),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { mapViewModel.confirmAddFavorite() }) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

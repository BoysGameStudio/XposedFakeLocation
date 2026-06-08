package com.noobexon.xposedfakelocation.manager.ui.map

import androidx.annotation.StringRes
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.noobexon.xposedfakelocation.R

@Composable
fun GoToPointDialog(
    latitude: String,
    longitude: String,
    @StringRes latitudeErrorRes: Int?,
    @StringRes longitudeErrorRes: Int?,
    onLatitudeChange: (String) -> Unit,
    onLongitudeChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.map_go_to_point)) },
        text = {
            Column {
                CoordinateInputField(
                    value = latitude,
                    onValueChange = onLatitudeChange,
                    label = stringResource(R.string.field_latitude),
                    errorRes = latitudeErrorRes,
                )
                Spacer(modifier = Modifier.height(8.dp))
                CoordinateInputField(
                    value = longitude,
                    onValueChange = onLongitudeChange,
                    label = stringResource(R.string.field_longitude),
                    errorRes = longitudeErrorRes,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
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
    name: String,
    latitude: String,
    longitude: String,
    @StringRes nameErrorRes: Int?,
    @StringRes latitudeErrorRes: Int?,
    @StringRes longitudeErrorRes: Int?,
    onNameChange: (String) -> Unit,
    onLatitudeChange: (String) -> Unit,
    onLongitudeChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.map_add_to_favorites)) },
        text = {
            Column {
                CoordinateInputField(
                    value = name,
                    onValueChange = onNameChange,
                    label = stringResource(R.string.field_name),
                    errorRes = nameErrorRes,
                )
                Spacer(modifier = Modifier.height(8.dp))
                CoordinateInputField(
                    value = latitude,
                    onValueChange = onLatitudeChange,
                    label = stringResource(R.string.field_latitude),
                    errorRes = latitudeErrorRes,
                    keyboardType = KeyboardType.Number,
                )
                Spacer(modifier = Modifier.height(8.dp))
                CoordinateInputField(
                    value = longitude,
                    onValueChange = onLongitudeChange,
                    label = stringResource(R.string.field_longitude),
                    errorRes = longitudeErrorRes,
                    keyboardType = KeyboardType.Number,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
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

/**
 * A single labelled text field with an inline validation error message shown beneath it when
 * [errorRes] is non-null. Shared by both map dialogs.
 */
@Composable
private fun CoordinateInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    @StringRes errorRes: Int?,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Unspecified,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = errorRes != null,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth()
    )
    if (errorRes != null) {
        Text(
            text = stringResource(errorRes),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

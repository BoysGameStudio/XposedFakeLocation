package com.noobexon.xposedfakelocation.manager.ui.map.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noobexon.xposedfakelocation.R

private const val MAX_LOCATION_PROFILE_TITLE_LENGTH = 160

@Composable
fun SaveLocationProfileTitleDialog(
    initialTitle: String = "",
    dialogTitleRes: Int = R.string.location_profile_title_dialog_title,
    messageRes: Int = R.string.location_profile_title_dialog_message,
    onDismissRequest: () -> Unit,
    onSaveTitle: (String) -> Unit
) {
    var title by rememberSaveable(initialTitle) { mutableStateOf(initialTitle) }
    val trimmedTitle = title.trim()

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(dialogTitleRes)) },
        text = {
            Column {
                Text(stringResource(messageRes))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { value ->
                        if (value.length <= MAX_LOCATION_PROFILE_TITLE_LENGTH) {
                            title = value
                        }
                    },
                    label = { Text(stringResource(R.string.location_profile_title_field)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSaveTitle(trimmedTitle) },
                enabled = trimmedTitle.isNotEmpty()
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

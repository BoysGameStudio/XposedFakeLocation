package com.noobexon.xposedfakelocation.manager.ui.map.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.model.signalbaseline.SavedLocationProfile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SavedLocationProfilesDialog(
    profiles: List<SavedLocationProfile>,
    selectedProfile: SavedLocationProfile?,
    onSelectProfile: (SavedLocationProfile) -> Unit,
    onBackToList: () -> Unit,
    onUseProfile: (SavedLocationProfile) -> Unit,
    onRenameProfile: (SavedLocationProfile, String) -> Unit,
    onDeleteProfile: (SavedLocationProfile) -> Unit,
    onDismissRequest: () -> Unit
) {
    var profileToRename by remember { mutableStateOf<SavedLocationProfile?>(null) }
    profileToRename?.let { profile ->
        SaveLocationProfileTitleDialog(
            initialTitle = profile.label,
            dialogTitleRes = R.string.location_profile_rename_dialog_title,
            messageRes = R.string.location_profile_rename_dialog_message,
            onDismissRequest = { profileToRename = null },
            onSaveTitle = { title ->
                profileToRename = null
                onRenameProfile(profile, title)
            }
        )
        return
    }

    if (selectedProfile == null) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(stringResource(R.string.location_profiles_title)) },
            text = {
                if (profiles.isEmpty()) {
                    Text(stringResource(R.string.location_profiles_empty))
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 520.dp)) {
                        items(profiles, key = SavedLocationProfile::id) { profile ->
                            SavedLocationProfileRow(
                                profile = profile,
                                onClick = { onSelectProfile(profile) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(stringResource(R.string.signal_baseline_done))
                }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(selectedProfile.label) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.location_profile_saved_at, selectedProfile.savedAtText()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    SignalBaselineDetailsContent(baseline = selectedProfile.baseline)
                }
            },
            confirmButton = {
                TextButton(onClick = { onUseProfile(selectedProfile) }) {
                    Text(stringResource(R.string.location_profile_use))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = onBackToList) {
                        Text(stringResource(R.string.location_profiles_back_to_list))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { profileToRename = selectedProfile }) {
                        Text(stringResource(R.string.location_profile_rename))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { onDeleteProfile(selectedProfile) }) {
                        Text(stringResource(R.string.location_profile_delete))
                    }
                }
            }
        )
    }
}

@Composable
private fun SavedLocationProfileRow(
    profile: SavedLocationProfile,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = profile.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = profile.coordinateText(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.location_profile_saved_at, profile.savedAtText()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(
                    R.string.location_profile_signal_summary,
                    profile.baseline.wifi.scanResultCount,
                    profile.baseline.cellular.cellInfoCount
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun SavedLocationProfile.coordinateText(): String {
    return String.format(
        Locale.US,
        "%.6f, %.6f",
        baseline.location.latitude,
        baseline.location.longitude
    )
}

private fun SavedLocationProfile.savedAtText(): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(savedAtMillis))
}

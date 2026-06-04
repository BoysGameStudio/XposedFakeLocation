package com.noobexon.xposedfakelocation.manager.ui.map.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.model.signalbaseline.SignalBaselineSnapshot
import org.osmdroid.util.GeoPoint
import java.util.Locale

@Composable
fun SimulationDataDialog(
    baseline: SignalBaselineSnapshot?,
    coordinate: GeoPoint?,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.simulation_data_title)) },
        text = {
            when {
                baseline != null -> SignalBaselineDetailsContent(baseline = baseline)
                coordinate != null -> CoordinateOnlySimulationContent(coordinate = coordinate)
                else -> Text(stringResource(R.string.simulation_data_empty))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.signal_baseline_done))
            }
        }
    )
}

@Composable
private fun CoordinateOnlySimulationContent(coordinate: GeoPoint) {
    Column {
        Text(
            text = stringResource(R.string.simulation_data_mode_coordinate_only),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = stringResource(R.string.signal_baseline_location_coordinates),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )
        Text(
            text = String.format(Locale.US, "%.6f, %.6f", coordinate.latitude, coordinate.longitude),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

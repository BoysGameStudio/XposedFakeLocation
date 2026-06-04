package com.noobexon.xposedfakelocation.manager.ui.map.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.model.signalbaseline.CdmaCellIdentitySnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.CdmaCellSignalStrengthSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.CellIdentitySnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.CellInfoSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.CellSignalStrengthSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.GenericCellRecordSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.GenericCellSignalStrengthSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.GsmCellIdentitySnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.GsmCellSignalStrengthSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.LteCellIdentitySnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.LteCellSignalStrengthSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.NrCellIdentitySnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.NrCellSignalStrengthSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.RADIO_TYPE_CDMA
import com.noobexon.xposedfakelocation.data.model.signalbaseline.RADIO_TYPE_GSM
import com.noobexon.xposedfakelocation.data.model.signalbaseline.RADIO_TYPE_LTE
import com.noobexon.xposedfakelocation.data.model.signalbaseline.RADIO_TYPE_NR
import com.noobexon.xposedfakelocation.data.model.signalbaseline.RADIO_TYPE_TDSCDMA
import com.noobexon.xposedfakelocation.data.model.signalbaseline.RADIO_TYPE_WCDMA
import com.noobexon.xposedfakelocation.data.model.signalbaseline.ScanResultSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.SignalBaselineSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.TdscdmaCellIdentitySnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.TdscdmaCellSignalStrengthSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.WcdmaCellIdentitySnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.WcdmaCellSignalStrengthSnapshot
import java.util.Locale

@Composable
fun SignalBaselineDetailsDialog(
    baseline: SignalBaselineSnapshot?,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.signal_baseline_details_title)) },
        text = {
            if (baseline == null) {
                Text(stringResource(R.string.signal_baseline_empty))
            } else {
                SignalBaselineDetailsContent(baseline = baseline)
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
fun SignalBaselineDetailsContent(
    baseline: SignalBaselineSnapshot,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .heightIn(max = 520.dp)
            .verticalScroll(rememberScrollState())
    ) {
        BaselineSectionTitle(stringResource(R.string.signal_baseline_location_section))
        BaselineDetailRow(
            title = stringResource(R.string.signal_baseline_location_coordinates),
            detail = baseline.location.coordinateDetail()
        )
        BaselineDetailRow(
            title = stringResource(R.string.signal_baseline_location_provider),
            detail = baseline.location.provider.orEmpty()
        )
        baseline.location.accuracyMeters?.let { accuracy ->
            BaselineDetailRow(
                title = stringResource(R.string.signal_baseline_location_accuracy),
                detail = stringResource(R.string.signal_baseline_location_accuracy_detail, accuracy)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))

        BaselineSectionTitle(stringResource(R.string.signal_baseline_wifi_section))
        Text(
            text = stringResource(R.string.signal_baseline_wifi_summary, baseline.wifi.scanResults.size),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        baseline.wifi.scanResults.forEachIndexed { index, scan ->
            BaselineDetailRow(
                title = scan.wifiTitle(index),
                detail = scan.wifiDetail()
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))

        BaselineSectionTitle(stringResource(R.string.signal_baseline_cellular_section))
        Text(
            text = stringResource(R.string.signal_baseline_cellular_summary, baseline.cellular.cellInfo.size),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        baseline.cellular.cellInfo.forEachIndexed { index, cellInfo ->
            BaselineDetailRow(
                title = cellInfo.cellTitle(index),
                detail = cellInfo.cellDetail()
            )
        }
    }
}

@Composable
private fun BaselineSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun BaselineDetailRow(
    title: String,
    detail: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (detail.isNotBlank()) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun com.noobexon.xposedfakelocation.data.model.signalbaseline.LocationBaselineSnapshot.coordinateDetail(): String {
    return String.format(Locale.US, "%.6f, %.6f", latitude, longitude)
}

private fun ScanResultSnapshot.wifiTitle(index: Int): String {
    return ssid?.takeIf { it.isNotBlank() } ?: "Hidden Wi-Fi ${index + 1}"
}

private fun ScanResultSnapshot.wifiDetail(): String {
    return listOf(
        "BSSID ${bssid ?: "unknown"}",
        level?.let { "RSSI $it dBm" },
        frequencyMhz?.let { "$it MHz" },
        capabilities
    ).filterNotNull().joinToString(separator = " · ")
}

private fun CellInfoSnapshot.cellTitle(index: Int): String {
    val registration = if (registered) "registered" else "neighbor"
    return "${radioType.uppercase()} ${index + 1} · $registration"
}

private fun CellInfoSnapshot.cellDetail(): String {
    return listOf(identity.identityDetail(), signalStrength.signalDetail())
        .filter { it.isNotBlank() }
        .joinToString(separator = " · ")
}

private fun CellIdentitySnapshot.identityDetail(): String {
    return when (radioType) {
        RADIO_TYPE_GSM -> gsm?.detail()
        RADIO_TYPE_LTE -> lte?.detail()
        RADIO_TYPE_WCDMA -> wcdma?.detail()
        RADIO_TYPE_NR -> nr?.detail()
        RADIO_TYPE_TDSCDMA -> tdscdma?.detail()
        RADIO_TYPE_CDMA -> cdma?.detail()
        else -> generic?.detail()
    }.orEmpty()
}

private fun CellSignalStrengthSnapshot.signalDetail(): String {
    return when (radioType) {
        RADIO_TYPE_GSM -> gsm?.detail()
        RADIO_TYPE_LTE -> lte?.detail()
        RADIO_TYPE_WCDMA -> wcdma?.detail()
        RADIO_TYPE_NR -> nr?.detail()
        RADIO_TYPE_TDSCDMA -> tdscdma?.detail()
        RADIO_TYPE_CDMA -> cdma?.detail()
        else -> generic?.detail()
    }.orEmpty()
}

private fun GsmCellIdentitySnapshot.detail(): String = listOf("MCC $mccString", "MNC $mncString", "LAC $lac", "CID $cid")
    .joinNonNullDetails()

private fun LteCellIdentitySnapshot.detail(): String = listOf("MCC $mccString", "MNC $mncString", "CI $ci", "PCI $pci", "TAC $tac")
    .joinNonNullDetails()

private fun WcdmaCellIdentitySnapshot.detail(): String = listOf("MCC $mccString", "MNC $mncString", "LAC $lac", "CID $cid", "PSC $psc")
    .joinNonNullDetails()

private fun NrCellIdentitySnapshot.detail(): String = listOf("MCC $mccString", "MNC $mncString", "NCI $nci", "PCI $pci", "TAC $tac")
    .joinNonNullDetails()

private fun TdscdmaCellIdentitySnapshot.detail(): String = listOf("MCC $mccString", "MNC $mncString", "LAC $lac", "CID $cid", "CPID $cpid")
    .joinNonNullDetails()

private fun CdmaCellIdentitySnapshot.detail(): String = listOf("BID $baseStationId", "SID $systemId", "NID $networkId")
    .joinNonNullDetails()

private fun GenericCellRecordSnapshot.detail(): String = className.orEmpty()

private fun GsmCellSignalStrengthSnapshot.detail(): String = signalParts(dbm, level)

private fun LteCellSignalStrengthSnapshot.detail(): String = signalParts(dbm, level)

private fun WcdmaCellSignalStrengthSnapshot.detail(): String = signalParts(dbm, level)

private fun NrCellSignalStrengthSnapshot.detail(): String = signalParts(dbm, level)

private fun TdscdmaCellSignalStrengthSnapshot.detail(): String = signalParts(dbm, level)

private fun CdmaCellSignalStrengthSnapshot.detail(): String = signalParts(dbm, level)

private fun GenericCellSignalStrengthSnapshot.detail(): String = signalParts(dbm, level)

private fun signalParts(dbm: Int?, level: Int?): String {
    return listOf(dbm?.let { "Signal $it dBm" }, level?.let { "Level $it" }).filterNotNull().joinToString(separator = ", ")
}

private fun List<String>.joinNonNullDetails(): String {
    return filterNot { it.endsWith(" null") || it.isBlank() }.joinToString(separator = ", ")
}

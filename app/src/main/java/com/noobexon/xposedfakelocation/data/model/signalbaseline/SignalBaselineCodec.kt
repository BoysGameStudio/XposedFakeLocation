package com.noobexon.xposedfakelocation.data.model.signalbaseline

import com.google.gson.Gson
import com.google.gson.JsonParser
import java.util.Base64

data class SignalBaselineValidationResult(
    val isValid: Boolean,
    val reason: String? = null
)

data class SignalBaselineParseResult(
    val snapshot: SignalBaselineSnapshot?,
    val validation: SignalBaselineValidationResult
) {
    val isValid: Boolean get() = validation.isValid
    val reason: String? get() = validation.reason
}

object SignalBaselineCodec {
    const val SCHEMA_VERSION = 1
    const val MAX_BASELINE_CELL_INFO = 64
    const val MAX_BASELINE_NEIGHBORING_CELL_INFO = 64
    const val MAX_BASELINE_WIFI_SCANS = 128
    const val MAX_BASELINE_PARCEL_BLOB_BYTES = 65_536
    const val MAX_CELL_INFO = MAX_BASELINE_CELL_INFO
    const val MAX_NEIGHBORING_CELL_INFO = MAX_BASELINE_NEIGHBORING_CELL_INFO
    const val MAX_WIFI_SCANS = MAX_BASELINE_WIFI_SCANS
    const val MAX_PARCEL_BLOB_BYTES = MAX_BASELINE_PARCEL_BLOB_BYTES

    private const val MAX_BUILD_FINGERPRINT_LENGTH = 512
    private const val MAX_PROVIDER_LENGTH = 128
    private const val MAX_LOCATION_EXTRAS = 32
    private const val MAX_STRING_LENGTH = 1_024
    private const val MAX_GENERIC_FIELDS = 32
    private const val MAX_GENERIC_FIELD_VALUE_LENGTH = 512
    private const val MAX_NR_BANDS = 32
    private const val MAX_WIFI_SSID_BYTES = 32
    private const val MAX_WIFI_CAPABILITIES_LENGTH = 512

    private val gson = Gson()

    val allowedCellInfoParcelClassNames: Set<String> = setOf(
        "android.telephony.CellInfoGsm",
        "android.telephony.CellInfoLte",
        "android.telephony.CellInfoWcdma",
        "android.telephony.CellInfoNr",
        "android.telephony.CellInfoTdscdma",
        "android.telephony.CellInfoCdma"
    )

    val allowedNeighboringCellInfoParcelClassNames: Set<String> = setOf(
        "android.telephony.NeighboringCellInfo"
    )

    val allowedWifiInfoParcelClassNames: Set<String> = setOf(
        "android.net.wifi.WifiInfo"
    )

    val allowedWifiScanResultParcelClassNames: Set<String> = setOf(
        "android.net.wifi.ScanResult"
    )

    private val allowedRadioTypes = setOf(
        RADIO_TYPE_GSM,
        RADIO_TYPE_LTE,
        RADIO_TYPE_WCDMA,
        RADIO_TYPE_NR,
        RADIO_TYPE_TDSCDMA,
        RADIO_TYPE_CDMA,
        RADIO_TYPE_UNKNOWN
    )

    fun encodeToJson(snapshot: SignalBaselineSnapshot): String? {
        val validation = validate(
            snapshot = snapshot,
            currentSdkInt = snapshot.captureSdkInt,
            currentBuildFingerprint = snapshot.captureBuildFingerprint
        )
        if (!validation.isValid) return null
        return runCatching { gson.toJson(snapshot) }.getOrNull()
    }

    fun parse(
        json: String?,
        currentSdkInt: Int,
        currentBuildFingerprint: String
    ): SignalBaselineParseResult {
        if (json.isNullOrBlank()) return invalidParseResult("missing_json")

        return runCatching {
            val element = JsonParser.parseString(json)
            if (!element.isJsonObject) return invalidParseResult("json_not_object")

            val snapshot = gson.fromJson(element, SignalBaselineSnapshot::class.java)
                ?: return invalidParseResult("missing_snapshot")
            val validation = validate(snapshot, currentSdkInt, currentBuildFingerprint)
            if (validation.isValid) {
                SignalBaselineParseResult(snapshot, validation)
            } else {
                SignalBaselineParseResult(null, validation)
            }
        }.getOrElse {
            invalidParseResult("malformed_json")
        }
    }

    fun parseOrNull(
        json: String?,
        currentSdkInt: Int,
        currentBuildFingerprint: String
    ): SignalBaselineSnapshot? = parse(json, currentSdkInt, currentBuildFingerprint).snapshot

    fun validate(
        snapshot: SignalBaselineSnapshot,
        currentSdkInt: Int,
        currentBuildFingerprint: String
    ): SignalBaselineValidationResult {
        return runCatching {
            validateInternal(
                snapshot = snapshot,
                currentSdkInt = currentSdkInt,
                currentBuildFingerprint = currentBuildFingerprint,
                requireCurrentDeviceCompatibility = true
            )
        }.getOrElse {
            invalid("malformed_snapshot")
        }
    }

    fun validateForArchive(snapshot: SignalBaselineSnapshot): SignalBaselineValidationResult {
        return runCatching {
            validateInternal(
                snapshot = snapshot,
                currentSdkInt = null,
                currentBuildFingerprint = null,
                requireCurrentDeviceCompatibility = false
            )
        }.getOrElse {
            invalid("malformed_snapshot")
        }
    }

    private fun validateInternal(
        snapshot: SignalBaselineSnapshot,
        currentSdkInt: Int?,
        currentBuildFingerprint: String?,
        requireCurrentDeviceCompatibility: Boolean
    ): SignalBaselineValidationResult {
        if (snapshot.schemaVersion != SCHEMA_VERSION) return invalid("unsupported_schema")
        if (snapshot.capturedAtMillis <= 0L) return invalid("captured_at_invalid")
        if (snapshot.captureSdkInt <= 0) return invalid("sdk_invalid")
        if (requireCurrentDeviceCompatibility) {
            if (currentSdkInt == null || currentSdkInt <= 0) return invalid("sdk_invalid")
            if (snapshot.captureSdkInt != currentSdkInt) return invalid("sdk_mismatch")
        }
        if (!isValidBuildFingerprint(snapshot.captureBuildFingerprint)) return invalid("capture_build_invalid")
        if (requireCurrentDeviceCompatibility) {
            if (currentBuildFingerprint == null || !isValidBuildFingerprint(currentBuildFingerprint)) {
                return invalid("current_build_invalid")
            }
            if (snapshot.captureBuildFingerprint != currentBuildFingerprint) return invalid("build_mismatch")
        }

        validateLocation(snapshot.location)?.let { return invalid(it) }
        validateCellular(snapshot.cellular, snapshot.captureSdkInt, snapshot.captureBuildFingerprint)
            ?.let { return invalid(it) }
        validateWifi(snapshot.wifi, snapshot.captureSdkInt, snapshot.captureBuildFingerprint)
            ?.let { return invalid(it) }

        return SignalBaselineValidationResult(isValid = true)
    }

    private fun validateLocation(location: LocationBaselineSnapshot): String? {
        if (location.provider != null && !isReasonableString(location.provider, MAX_PROVIDER_LENGTH)) {
            return "location_provider_invalid"
        }
        if (!location.latitude.isFinite() || location.latitude !in -90.0..90.0) return "latitude_invalid"
        if (!location.longitude.isFinite() || location.longitude !in -180.0..180.0) return "longitude_invalid"
        if (location.timeMillis <= 0L) return "location_time_invalid"
        if (location.elapsedRealtimeNanos < 0L) return "elapsed_realtime_invalid"
        validateFlaggedDouble(
            hasValue = location.hasElapsedRealtimeUncertaintyNanos,
            value = location.elapsedRealtimeUncertaintyNanos,
            allowNegative = false,
            reason = "elapsed_realtime_uncertainty_invalid"
        )?.let { return it }
        validateFlaggedDouble(
            hasValue = location.hasAltitude,
            value = location.altitudeMeters,
            allowNegative = true,
            reason = "altitude_invalid"
        )?.let { return it }
        validateFlaggedFloat(
            hasValue = location.hasAccuracy,
            value = location.accuracyMeters,
            allowNegative = false,
            reason = "accuracy_invalid"
        )?.let { return it }
        validateFlaggedFloat(
            hasValue = location.hasSpeed,
            value = location.speedMetersPerSecond,
            allowNegative = false,
            reason = "speed_invalid"
        )?.let { return it }
        validateFlaggedFloat(
            hasValue = location.hasBearing,
            value = location.bearingDegrees,
            allowNegative = false,
            reason = "bearing_invalid"
        )?.let { return it }
        if (location.hasBearing && location.bearingDegrees != null && location.bearingDegrees !in 0f..360f) {
            return "bearing_invalid"
        }
        validateFlaggedFloat(
            hasValue = location.hasVerticalAccuracy,
            value = location.verticalAccuracyMeters,
            allowNegative = false,
            reason = "vertical_accuracy_invalid"
        )?.let { return it }
        validateFlaggedFloat(
            hasValue = location.hasSpeedAccuracy,
            value = location.speedAccuracyMetersPerSecond,
            allowNegative = false,
            reason = "speed_accuracy_invalid"
        )?.let { return it }
        validateFlaggedFloat(
            hasValue = location.hasBearingAccuracy,
            value = location.bearingAccuracyDegrees,
            allowNegative = false,
            reason = "bearing_accuracy_invalid"
        )?.let { return it }
        validateFlaggedDouble(
            hasValue = location.hasMslAltitude,
            value = location.mslAltitudeMeters,
            allowNegative = true,
            reason = "msl_altitude_invalid"
        )?.let { return it }
        validateFlaggedFloat(
            hasValue = location.hasMslAltitudeAccuracy,
            value = location.mslAltitudeAccuracyMeters,
            allowNegative = false,
            reason = "msl_altitude_accuracy_invalid"
        )?.let { return it }
        if (location.extras.size > MAX_LOCATION_EXTRAS) return "location_extras_oversized"
        for ((key, value) in location.extras) {
            if (!isReasonableString(key, MAX_STRING_LENGTH)) return "location_extra_key_invalid"
            if (value != null && value !is String && value !is Number && value !is Boolean) {
                return "location_extra_value_invalid"
            }
        }
        if (location.extrasUnsupportedKeys.size > MAX_LOCATION_EXTRAS) {
            return "location_unsupported_extras_oversized"
        }
        if (location.extrasUnsupportedKeys.any { !isReasonableString(it, MAX_STRING_LENGTH) }) {
            return "location_unsupported_extra_key_invalid"
        }
        return null
    }

    private fun validateCellular(
        cellular: CellularBaselineSnapshot,
        captureSdkInt: Int,
        captureBuildFingerprint: String
    ): String? {
        cellular.cellLocation?.let { validateCellLocation(it)?.let { reason -> return reason } }
        if (cellular.cellInfoCount != cellular.cellInfo.size) return "cell_info_count_mismatch"
        if (cellular.cellInfoCount > MAX_CELL_INFO) return "cell_info_oversized"
        if (cellular.neighboringCellInfoCount != cellular.neighboringCellInfo.size) {
            return "neighboring_cell_info_count_mismatch"
        }
        if (cellular.neighboringCellInfoCount > MAX_NEIGHBORING_CELL_INFO) {
            return "neighboring_cell_info_oversized"
        }
        cellular.cellInfo.forEach { cellInfo ->
            validateCellInfo(cellInfo, captureSdkInt, captureBuildFingerprint)?.let { return it }
        }
        cellular.neighboringCellInfo.forEach { neighboringCellInfo ->
            validateNeighboringCellInfo(neighboringCellInfo, captureSdkInt, captureBuildFingerprint)
                ?.let { return it }
        }
        return null
    }

    private fun validateCellLocation(cellLocation: CellLocationSnapshot): String? {
        if (cellLocation.type !in setOf(RADIO_TYPE_GSM, RADIO_TYPE_CDMA)) return "cell_location_type_invalid"
        return when (cellLocation.type) {
            RADIO_TYPE_GSM -> {
                if (cellLocation.gsm == null || cellLocation.cdma != null) "cell_location_gsm_invalid" else null
            }
            RADIO_TYPE_CDMA -> {
                if (cellLocation.cdma == null || cellLocation.gsm != null) "cell_location_cdma_invalid" else null
            }
            else -> "cell_location_type_invalid"
        }
    }

    private fun validateCellInfo(
        cellInfo: CellInfoSnapshot,
        captureSdkInt: Int,
        captureBuildFingerprint: String
    ): String? {
        validateRadioType(cellInfo.radioType)?.let { return it }
        if (cellInfo.timestampMillis != null && cellInfo.timestampMillis < 0L) return "cell_timestamp_invalid"
        if (cellInfo.identity.radioType != cellInfo.radioType) return "cell_identity_type_mismatch"
        if (cellInfo.signalStrength.radioType != cellInfo.radioType) return "cell_signal_type_mismatch"
        validateCellIdentity(cellInfo.identity)?.let { return it }
        validateCellSignalStrength(cellInfo.signalStrength)?.let { return it }
        return validateParcelMetadata(
            parcelBase64 = cellInfo.parcelBase64,
            parcelClassName = cellInfo.parcelClassName,
            parcelByteCount = cellInfo.parcelByteCount,
            parcelSdkInt = cellInfo.parcelSdkInt,
            parcelBuildFingerprint = cellInfo.parcelBuildFingerprint,
            captureSdkInt = captureSdkInt,
            captureBuildFingerprint = captureBuildFingerprint,
            allowedClassNames = allowedCellInfoParcelClassNames,
            reasonPrefix = "cell_info_parcel"
        )
    }

    private fun validateNeighboringCellInfo(
        neighboringCellInfo: NeighboringCellInfoSnapshot,
        captureSdkInt: Int,
        captureBuildFingerprint: String
    ): String? {
        validateRadioType(neighboringCellInfo.radioType)?.let { return it }
        return validateParcelMetadata(
            parcelBase64 = neighboringCellInfo.parcelBase64,
            parcelClassName = neighboringCellInfo.parcelClassName,
            parcelByteCount = neighboringCellInfo.parcelByteCount,
            parcelSdkInt = neighboringCellInfo.parcelSdkInt,
            parcelBuildFingerprint = neighboringCellInfo.parcelBuildFingerprint,
            captureSdkInt = captureSdkInt,
            captureBuildFingerprint = captureBuildFingerprint,
            allowedClassNames = allowedNeighboringCellInfoParcelClassNames,
            reasonPrefix = "neighboring_cell_info_parcel"
        )
    }

    private fun validateCellIdentity(identity: CellIdentitySnapshot): String? {
        validateRadioType(identity.radioType)?.let { return it }
        if (identity.branchCount() != 1) return "cell_identity_branch_invalid"
        return when (identity.radioType) {
            RADIO_TYPE_GSM -> if (identity.gsm == null) "cell_identity_gsm_missing" else null
            RADIO_TYPE_LTE -> if (identity.lte == null) "cell_identity_lte_missing" else null
            RADIO_TYPE_WCDMA -> if (identity.wcdma == null) "cell_identity_wcdma_missing" else null
            RADIO_TYPE_NR -> {
                val nr = identity.nr ?: return "cell_identity_nr_missing"
                if (nr.bands.size > MAX_NR_BANDS) "cell_identity_nr_bands_oversized" else null
            }
            RADIO_TYPE_TDSCDMA -> if (identity.tdscdma == null) "cell_identity_tdscdma_missing" else null
            RADIO_TYPE_CDMA -> if (identity.cdma == null) "cell_identity_cdma_missing" else null
            RADIO_TYPE_UNKNOWN -> validateGenericCellRecord(identity.generic)
            else -> "cell_identity_type_invalid"
        }
    }

    private fun validateCellSignalStrength(signalStrength: CellSignalStrengthSnapshot): String? {
        validateRadioType(signalStrength.radioType)?.let { return it }
        if (signalStrength.branchCount() != 1) return "cell_signal_branch_invalid"
        return when (signalStrength.radioType) {
            RADIO_TYPE_GSM -> if (signalStrength.gsm == null) "cell_signal_gsm_missing" else null
            RADIO_TYPE_LTE -> if (signalStrength.lte == null) "cell_signal_lte_missing" else null
            RADIO_TYPE_WCDMA -> if (signalStrength.wcdma == null) "cell_signal_wcdma_missing" else null
            RADIO_TYPE_NR -> if (signalStrength.nr == null) "cell_signal_nr_missing" else null
            RADIO_TYPE_TDSCDMA -> if (signalStrength.tdscdma == null) "cell_signal_tdscdma_missing" else null
            RADIO_TYPE_CDMA -> if (signalStrength.cdma == null) "cell_signal_cdma_missing" else null
            RADIO_TYPE_UNKNOWN -> validateGenericCellSignal(signalStrength.generic)
            else -> "cell_signal_type_invalid"
        }
    }

    private fun validateGenericCellRecord(record: GenericCellRecordSnapshot?): String? {
        if (record == null) return "generic_cell_record_missing"
        if (record.className != null && !isReasonableString(record.className, MAX_STRING_LENGTH)) {
            return "generic_cell_record_class_invalid"
        }
        if (record.fields.size > MAX_GENERIC_FIELDS) return "generic_cell_record_fields_oversized"
        for ((key, value) in record.fields) {
            if (!isReasonableString(key, MAX_STRING_LENGTH)) return "generic_cell_record_key_invalid"
            if (value != null && !isReasonableString(value, MAX_GENERIC_FIELD_VALUE_LENGTH)) {
                return "generic_cell_record_value_invalid"
            }
        }
        return null
    }

    private fun validateGenericCellSignal(signal: GenericCellSignalStrengthSnapshot?): String? {
        if (signal == null) return "generic_cell_signal_missing"
        if (signal.className != null && !isReasonableString(signal.className, MAX_STRING_LENGTH)) {
            return "generic_cell_signal_class_invalid"
        }
        if (signal.fields.size > MAX_GENERIC_FIELDS) return "generic_cell_signal_fields_oversized"
        for ((key, value) in signal.fields) {
            if (!isReasonableString(key, MAX_STRING_LENGTH)) return "generic_cell_signal_key_invalid"
            if (value != null && !isReasonableString(value, MAX_GENERIC_FIELD_VALUE_LENGTH)) {
                return "generic_cell_signal_value_invalid"
            }
        }
        return null
    }

    private fun validateWifi(
        wifi: WifiBaselineSnapshot,
        captureSdkInt: Int,
        captureBuildFingerprint: String
    ): String? {
        if (wifi.scanResultCount != wifi.scanResults.size) return "wifi_scan_count_mismatch"
        if (wifi.scanResultCount > MAX_WIFI_SCANS) return "wifi_scan_oversized"
        wifi.connectionInfo?.let {
            validateWifiInfo(it, captureSdkInt, captureBuildFingerprint)?.let { reason -> return reason }
        }
        wifi.scanResults.forEach { scanResult ->
            validateScanResult(scanResult, captureSdkInt, captureBuildFingerprint)?.let { return it }
        }
        return null
    }

    private fun validateWifiInfo(
        wifiInfo: WifiInfoSnapshot,
        captureSdkInt: Int,
        captureBuildFingerprint: String
    ): String? {
        validateWifiSsid(wifiInfo.ssid, wifiInfo.ssidBytesBase64)?.let { return it }
        validateBssid(wifiInfo.bssid)?.let { return it }
        if (wifiInfo.frequencyMhz != null && wifiInfo.frequencyMhz < 0) return "wifi_frequency_invalid"
        return validateParcelMetadata(
            parcelBase64 = wifiInfo.parcelBase64,
            parcelClassName = wifiInfo.parcelClassName,
            parcelByteCount = wifiInfo.parcelByteCount,
            parcelSdkInt = wifiInfo.parcelSdkInt,
            parcelBuildFingerprint = wifiInfo.parcelBuildFingerprint,
            captureSdkInt = captureSdkInt,
            captureBuildFingerprint = captureBuildFingerprint,
            allowedClassNames = allowedWifiInfoParcelClassNames,
            reasonPrefix = "wifi_info_parcel"
        )
    }

    private fun validateScanResult(
        scanResult: ScanResultSnapshot,
        captureSdkInt: Int,
        captureBuildFingerprint: String
    ): String? {
        validateWifiSsid(scanResult.ssid, scanResult.ssidBytesBase64)?.let { return it }
        validateBssid(scanResult.bssid)?.let { return it }
        if (scanResult.capabilities != null && !isReasonableString(scanResult.capabilities, MAX_WIFI_CAPABILITIES_LENGTH)) {
            return "wifi_capabilities_invalid"
        }
        if (scanResult.frequencyMhz != null && scanResult.frequencyMhz < 0) return "wifi_scan_frequency_invalid"
        if (scanResult.centerFreq0Mhz != null && scanResult.centerFreq0Mhz < 0) return "wifi_center_freq0_invalid"
        if (scanResult.centerFreq1Mhz != null && scanResult.centerFreq1Mhz < 0) return "wifi_center_freq1_invalid"
        if (scanResult.timestampMicros != null && scanResult.timestampMicros < 0L) return "wifi_timestamp_invalid"
        return validateParcelMetadata(
            parcelBase64 = scanResult.parcelBase64,
            parcelClassName = scanResult.parcelClassName,
            parcelByteCount = scanResult.parcelByteCount,
            parcelSdkInt = scanResult.parcelSdkInt,
            parcelBuildFingerprint = scanResult.parcelBuildFingerprint,
            captureSdkInt = captureSdkInt,
            captureBuildFingerprint = captureBuildFingerprint,
            allowedClassNames = allowedWifiScanResultParcelClassNames,
            reasonPrefix = "wifi_scan_parcel"
        )
    }

    private fun validateWifiSsid(ssid: String?, ssidBytesBase64: String?): String? {
        if (ssid != null && !isReasonableString(ssid, MAX_STRING_LENGTH)) return "wifi_ssid_invalid"
        if (ssidBytesBase64 == null) return null
        val decoded = decodeBase64(ssidBytesBase64, MAX_WIFI_SSID_BYTES) ?: return "wifi_ssid_bytes_invalid"
        if (decoded.size > MAX_WIFI_SSID_BYTES) return "wifi_ssid_bytes_oversized"
        return null
    }

    private fun validateBssid(bssid: String?): String? {
        if (bssid != null && !isReasonableString(bssid, MAX_STRING_LENGTH)) return "wifi_bssid_invalid"
        return null
    }

    private fun validateParcelMetadata(
        parcelBase64: String?,
        parcelClassName: String?,
        parcelByteCount: Int?,
        parcelSdkInt: Int?,
        parcelBuildFingerprint: String?,
        captureSdkInt: Int,
        captureBuildFingerprint: String,
        allowedClassNames: Set<String>,
        reasonPrefix: String
    ): String? {
        val metadataValues = listOf(
            parcelBase64,
            parcelClassName,
            parcelByteCount,
            parcelSdkInt,
            parcelBuildFingerprint
        )
        if (metadataValues.all { it == null }) return null
        if (metadataValues.any { it == null }) return "${reasonPrefix}_metadata_incomplete"

        val className = parcelClassName ?: return "${reasonPrefix}_class_missing"
        val byteCount = parcelByteCount ?: return "${reasonPrefix}_size_missing"
        val sdkInt = parcelSdkInt ?: return "${reasonPrefix}_sdk_missing"
        val buildFingerprint = parcelBuildFingerprint ?: return "${reasonPrefix}_build_missing"
        val base64 = parcelBase64 ?: return "${reasonPrefix}_blob_missing"

        if (className !in allowedClassNames) return "${reasonPrefix}_class_unsupported"
        if (byteCount <= 0 || byteCount > MAX_PARCEL_BLOB_BYTES) return "${reasonPrefix}_size_invalid"
        if (sdkInt != captureSdkInt) return "${reasonPrefix}_sdk_mismatch"
        if (buildFingerprint != captureBuildFingerprint) return "${reasonPrefix}_build_mismatch"
        val decoded = decodeBase64(base64, MAX_PARCEL_BLOB_BYTES) ?: return "${reasonPrefix}_blob_invalid"
        if (decoded.size != byteCount) return "${reasonPrefix}_size_mismatch"
        return null
    }

    private fun validateFlaggedDouble(
        hasValue: Boolean,
        value: Double?,
        allowNegative: Boolean,
        reason: String
    ): String? {
        if (!hasValue) return null
        if (value == null || !value.isFinite()) return reason
        if (!allowNegative && value < 0.0) return reason
        return null
    }

    private fun validateFlaggedFloat(
        hasValue: Boolean,
        value: Float?,
        allowNegative: Boolean,
        reason: String
    ): String? {
        if (!hasValue) return null
        if (value == null || !value.isFinite()) return reason
        if (!allowNegative && value < 0f) return reason
        return null
    }

    private fun validateRadioType(radioType: String): String? {
        if (radioType !in allowedRadioTypes) return "radio_type_invalid"
        return null
    }

    private fun CellIdentitySnapshot.branchCount(): Int {
        return listOfNotNull(gsm, lte, wcdma, nr, tdscdma, cdma, generic).size
    }

    private fun CellSignalStrengthSnapshot.branchCount(): Int {
        return listOfNotNull(gsm, lte, wcdma, nr, tdscdma, cdma, generic).size
    }

    private fun decodeBase64(base64: String, maxBytes: Int): ByteArray? {
        val maxBase64Chars = ((maxBytes + 2) / 3) * 4
        if (base64.length > maxBase64Chars) return null
        return runCatching { Base64.getDecoder().decode(base64) }.getOrNull()
    }

    private fun isValidBuildFingerprint(value: String): Boolean {
        return value.isNotBlank() && isReasonableString(value, MAX_BUILD_FINGERPRINT_LENGTH)
    }

    private fun isReasonableString(value: String, maxLength: Int): Boolean {
        return value.length <= maxLength
    }

    private fun invalidParseResult(reason: String): SignalBaselineParseResult {
        return SignalBaselineParseResult(snapshot = null, validation = invalid(reason))
    }

    private fun invalid(reason: String): SignalBaselineValidationResult {
        return SignalBaselineValidationResult(isValid = false, reason = reason)
    }
}

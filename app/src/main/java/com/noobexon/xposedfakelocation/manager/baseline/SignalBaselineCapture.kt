package com.noobexon.xposedfakelocation.manager.baseline

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.telephony.CellIdentityCdma
import android.telephony.CellIdentityGsm
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellIdentityTdscdma
import android.telephony.CellIdentityWcdma
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoTdscdma
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrengthCdma
import android.telephony.CellSignalStrengthGsm
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.CellSignalStrengthTdscdma
import android.telephony.CellSignalStrengthWcdma
import android.telephony.NeighboringCellInfo
import android.telephony.TelephonyManager
import android.telephony.cdma.CdmaCellLocation
import android.telephony.gsm.GsmCellLocation
import com.noobexon.xposedfakelocation.data.model.signalbaseline.CdmaCellIdentitySnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.CdmaCellLocationSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.CdmaCellSignalStrengthSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.CellIdentitySnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.CellInfoSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.CellLocationSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.CellSignalStrengthSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.CellularBaselineSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.GenericCellRecordSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.GenericCellSignalStrengthSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.GsmCellIdentitySnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.GsmCellLocationSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.GsmCellSignalStrengthSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.LocationBaselineSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.LteCellIdentitySnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.LteCellSignalStrengthSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.NeighboringCellInfoSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.NrCellIdentitySnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.NrCellSignalStrengthSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.RADIO_TYPE_CDMA
import com.noobexon.xposedfakelocation.data.model.signalbaseline.RADIO_TYPE_GSM
import com.noobexon.xposedfakelocation.data.model.signalbaseline.RADIO_TYPE_LTE
import com.noobexon.xposedfakelocation.data.model.signalbaseline.RADIO_TYPE_NR
import com.noobexon.xposedfakelocation.data.model.signalbaseline.RADIO_TYPE_TDSCDMA
import com.noobexon.xposedfakelocation.data.model.signalbaseline.RADIO_TYPE_UNKNOWN
import com.noobexon.xposedfakelocation.data.model.signalbaseline.RADIO_TYPE_WCDMA
import com.noobexon.xposedfakelocation.data.model.signalbaseline.ScanResultSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.SignalBaselineCodec
import com.noobexon.xposedfakelocation.data.model.signalbaseline.SignalBaselineSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.TdscdmaCellIdentitySnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.TdscdmaCellSignalStrengthSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.WcdmaCellIdentitySnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.WcdmaCellSignalStrengthSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.WifiBaselineSnapshot
import com.noobexon.xposedfakelocation.data.model.signalbaseline.WifiInfoSnapshot
import java.time.Clock
import java.util.Base64

class SignalBaselineCapture(
    private val source: SignalBaselineSource,
    private val clock: Clock = Clock.systemUTC(),
    private val sdkIntProvider: () -> Int = { Build.VERSION.SDK_INT },
    private val buildFingerprintProvider: () -> String = { Build.FINGERPRINT }
) {
    constructor(
        context: Context,
        clock: Clock = Clock.systemUTC()
    ) : this(
        source = AndroidSignalBaselineSource(context),
        clock = clock
    )

    sealed interface CaptureResult {
        data class Success(val snapshot: SignalBaselineSnapshot) : CaptureResult
        data object NoRealLocation : CaptureResult
    }

    fun capture(): CaptureResult {
        val realLocation = captureRealLocation() ?: return CaptureResult.NoRealLocation
        val sdkInt = sdkIntProvider()
        val buildFingerprint = buildFingerprintProvider()

        return CaptureResult.Success(
            SignalBaselineSnapshot(
                schemaVersion = SignalBaselineCodec.SCHEMA_VERSION,
                capturedAtMillis = clock.millis(),
                captureSdkInt = sdkInt,
                captureBuildFingerprint = buildFingerprint,
                location = realLocation,
                cellular = captureCellular(),
                wifi = captureWifi()
            )
        )
    }

    private fun captureRealLocation(): LocationBaselineSnapshot? {
        val providers = readSource(emptyList()) { source.getEnabledLocationProviders() }
        return providers
            .mapNotNull { provider -> readSource(null) { source.getLastKnownLocation(provider) } }
            .reduceOrNull { bestLocation, candidateLocation ->
                if (candidateLocation.isBetterCaptureLocationThan(bestLocation)) candidateLocation else bestLocation
            }
    }

    private fun captureCellular(): CellularBaselineSnapshot {
        val cellInfo = readSource(emptyList()) { source.getAllCellInfoSnapshots() }
            .take(SignalBaselineCodec.MAX_CELL_INFO)
        val neighboringCellInfo = readSource(emptyList()) { source.getNeighboringCellInfoSnapshots() }
            .take(SignalBaselineCodec.MAX_NEIGHBORING_CELL_INFO)

        return CellularBaselineSnapshot(
            cellLocation = readSource(null) { source.getCellLocationSnapshot() },
            cellInfo = cellInfo,
            cellInfoCount = cellInfo.size,
            neighboringCellInfo = neighboringCellInfo,
            neighboringCellInfoCount = neighboringCellInfo.size
        )
    }

    private fun captureWifi(): WifiBaselineSnapshot {
        val scanResults = readSource(emptyList()) { source.getWifiScanResultSnapshots() }
            .take(SignalBaselineCodec.MAX_WIFI_SCANS)

        return WifiBaselineSnapshot(
            connectionInfo = readSource(null) { source.getWifiConnectionInfoSnapshot() },
            scanResults = scanResults,
            scanResultCount = scanResults.size
        )
    }

    private inline fun <T> readSource(defaultValue: T, block: () -> T): T {
        return try {
            block()
        } catch (exception: SecurityException) {
            defaultValue
        } catch (throwable: Throwable) {
            defaultValue
        }
    }

    private fun LocationBaselineSnapshot.isBetterCaptureLocationThan(currentBest: LocationBaselineSnapshot): Boolean {
        if (timeMillis != currentBest.timeMillis) {
            return timeMillis > currentBest.timeMillis
        }

        if (hasAccuracy && currentBest.hasAccuracy) {
            val candidateAccuracy = accuracyMeters ?: Float.MAX_VALUE
            val currentAccuracy = currentBest.accuracyMeters ?: Float.MAX_VALUE
            return candidateAccuracy < currentAccuracy
        }

        return hasAccuracy && !currentBest.hasAccuracy
    }
}

interface SignalBaselineSource {
    fun getEnabledLocationProviders(): List<String>
    fun getLastKnownLocation(provider: String): LocationBaselineSnapshot?
    fun getAllCellInfoSnapshots(): List<CellInfoSnapshot>
    fun getCellLocationSnapshot(): CellLocationSnapshot?
    fun getNeighboringCellInfoSnapshots(): List<NeighboringCellInfoSnapshot>
    fun getWifiConnectionInfoSnapshot(): WifiInfoSnapshot?
    fun getWifiScanResultSnapshots(): List<ScanResultSnapshot>
}

private class AndroidSignalBaselineSource(context: Context) : SignalBaselineSource {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    override fun getEnabledLocationProviders(): List<String> {
        return locationManager?.getProviders(true).orEmpty()
    }

    override fun getLastKnownLocation(provider: String): LocationBaselineSnapshot? {
        return locationManager?.getLastKnownLocation(provider)?.toBaselineSnapshot()
    }

    override fun getAllCellInfoSnapshots(): List<CellInfoSnapshot> {
        return telephonyManager
            ?.getAllCellInfo()
            .orEmpty()
            .mapNotNull { it.toSnapshotOrNull() }
    }

    @Suppress("DEPRECATION")
    override fun getCellLocationSnapshot(): CellLocationSnapshot? {
        return when (val cellLocation = telephonyManager?.cellLocation) {
            is GsmCellLocation -> CellLocationSnapshot(
                type = RADIO_TYPE_GSM,
                gsm = GsmCellLocationSnapshot(
                    lac = cellLocation.lac.cellFieldOrNull(),
                    cid = cellLocation.cid.cellFieldOrNull(),
                    psc = cellLocation.psc.cellFieldOrNull()
                )
            )
            is CdmaCellLocation -> CellLocationSnapshot(
                type = RADIO_TYPE_CDMA,
                cdma = CdmaCellLocationSnapshot(
                    baseStationId = cellLocation.baseStationId.cellFieldOrNull(),
                    baseStationLatitude = cellLocation.baseStationLatitude.cellFieldOrNull(),
                    baseStationLongitude = cellLocation.baseStationLongitude.cellFieldOrNull(),
                    systemId = cellLocation.systemId.cellFieldOrNull(),
                    networkId = cellLocation.networkId.cellFieldOrNull()
                )
            )
            else -> null
        }
    }

    @Suppress("DEPRECATION")
    override fun getNeighboringCellInfoSnapshots(): List<NeighboringCellInfoSnapshot> {
        return telephonyManager
            ?.legacyNeighboringCellInfo()
            .orEmpty()
            .mapNotNull { it.toSnapshotOrNull() }
    }

    override fun getWifiConnectionInfoSnapshot(): WifiInfoSnapshot? {
        return wifiManager?.connectionInfo?.toSnapshot()
    }

    override fun getWifiScanResultSnapshots(): List<ScanResultSnapshot> {
        return wifiManager
            ?.scanResults
            .orEmpty()
            .map { it.toSnapshot() }
    }
}

private fun Location.toBaselineSnapshot(): LocationBaselineSnapshot {
    val (supportedExtras, unsupportedExtraKeys) = extras.toSupportedExtras()
    return LocationBaselineSnapshot(
        provider = provider,
        latitude = latitude,
        longitude = longitude,
        timeMillis = time,
        elapsedRealtimeNanos = elapsedRealtimeNanos,
        hasElapsedRealtimeUncertaintyNanos = hasElapsedRealtimeUncertaintyNanos(),
        elapsedRealtimeUncertaintyNanos = if (hasElapsedRealtimeUncertaintyNanos()) elapsedRealtimeUncertaintyNanos else null,
        hasAltitude = hasAltitude(),
        altitudeMeters = if (hasAltitude()) altitude else null,
        hasAccuracy = hasAccuracy(),
        accuracyMeters = if (hasAccuracy()) accuracy else null,
        hasSpeed = hasSpeed(),
        speedMetersPerSecond = if (hasSpeed()) speed else null,
        hasBearing = hasBearing(),
        bearingDegrees = if (hasBearing()) bearing else null,
        hasVerticalAccuracy = hasVerticalAccuracy(),
        verticalAccuracyMeters = if (hasVerticalAccuracy()) verticalAccuracyMeters else null,
        hasSpeedAccuracy = hasSpeedAccuracy(),
        speedAccuracyMetersPerSecond = if (hasSpeedAccuracy()) speedAccuracyMetersPerSecond else null,
        hasBearingAccuracy = hasBearingAccuracy(),
        bearingAccuracyDegrees = if (hasBearingAccuracy()) bearingAccuracyDegrees else null,
        hasMslAltitude = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && hasMslAltitude(),
        mslAltitudeMeters = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && hasMslAltitude()) mslAltitudeMeters else null,
        hasMslAltitudeAccuracy = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && hasMslAltitudeAccuracy(),
        mslAltitudeAccuracyMeters = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && hasMslAltitudeAccuracy()) mslAltitudeAccuracyMeters else null,
        isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) isMock else isFromMockProvider,
        extras = supportedExtras,
        extrasUnsupportedKeys = unsupportedExtraKeys
    )
}

private fun Bundle?.toSupportedExtras(): Pair<Map<String, Any?>, List<String>> {
    if (this == null || isEmpty) {
        return emptyMap<String, Any?>() to emptyList()
    }

    val supportedExtras = linkedMapOf<String, Any?>()
    val unsupportedExtraKeys = mutableListOf<String>()
    keySet().toList().sorted().forEach { key ->
        val value = get(key)
        val supportedValue = value.toSupportedExtraValue()
        if (supportedValue != UnsupportedExtraValue && supportedExtras.size < MAX_LOCATION_EXTRAS) {
            supportedExtras[key] = supportedValue
        } else if (unsupportedExtraKeys.size < MAX_LOCATION_EXTRAS) {
            unsupportedExtraKeys += key
        }
    }
    return supportedExtras to unsupportedExtraKeys
}

private fun Any?.toSupportedExtraValue(): Any? {
    return when (this) {
        null,
        is String,
        is Boolean,
        is Byte,
        is Short,
        is Int,
        is Long,
        is Float,
        is Double -> this
        is Char -> toString()
        else -> UnsupportedExtraValue
    }
}

private data object UnsupportedExtraValue

private fun CellInfo.toSnapshotOrNull(): CellInfoSnapshot? {
    val snapshot = runCatching {
        when (this) {
            is CellInfoGsm -> toSnapshot()
            is CellInfoLte -> toSnapshot()
            is CellInfoWcdma -> toSnapshot()
            is CellInfoNr -> toSnapshot()
            is CellInfoTdscdma -> toSnapshot()
            is CellInfoCdma -> toSnapshot()
            else -> toGenericSnapshot()
        }
    }.getOrElse {
        runCatching { toGenericSnapshot() }.getOrNull()
    } ?: return null

    val parcelMetadata = toParcelMetadata(SignalBaselineCodec.allowedCellInfoParcelClassNames)
    return snapshot.withParcelMetadata(parcelMetadata)
}

private fun CellInfoGsm.toSnapshot(): CellInfoSnapshot {
    return CellInfoSnapshot(
        radioType = RADIO_TYPE_GSM,
        registered = isRegistered,
        cellConnectionStatus = cellConnectionStatus,
        timestampMillis = timestampMillis.nonNegativeOrNull(),
        identity = CellIdentitySnapshot(
            radioType = RADIO_TYPE_GSM,
            gsm = cellIdentity.toSnapshot()
        ),
        signalStrength = CellSignalStrengthSnapshot(
            radioType = RADIO_TYPE_GSM,
            gsm = cellSignalStrength.toSnapshot()
        )
    )
}

private fun CellIdentityGsm.toSnapshot(): GsmCellIdentitySnapshot {
    return GsmCellIdentitySnapshot(
        mccString = mccString,
        mncString = mncString,
        lac = lac.cellFieldOrNull(),
        cid = cid.cellFieldOrNull(),
        arfcn = arfcn.cellFieldOrNull(),
        bsic = bsic.cellFieldOrNull(),
        operatorAlphaLong = operatorAlphaLong?.toString(),
        operatorAlphaShort = operatorAlphaShort?.toString()
    )
}

private fun CellSignalStrengthGsm.toSnapshot(): GsmCellSignalStrengthSnapshot {
    return GsmCellSignalStrengthSnapshot(
        dbm = dbm.signalFieldOrNull(),
        asuLevel = asuLevel.signalFieldOrNull(),
        level = level.signalFieldOrNull(),
        bitErrorRate = bitErrorRate.signalFieldOrNull(),
        timingAdvance = timingAdvance.signalFieldOrNull()
    )
}

private fun CellInfoLte.toSnapshot(): CellInfoSnapshot {
    return CellInfoSnapshot(
        radioType = RADIO_TYPE_LTE,
        registered = isRegistered,
        cellConnectionStatus = cellConnectionStatus,
        timestampMillis = timestampMillis.nonNegativeOrNull(),
        identity = CellIdentitySnapshot(
            radioType = RADIO_TYPE_LTE,
            lte = cellIdentity.toSnapshot()
        ),
        signalStrength = CellSignalStrengthSnapshot(
            radioType = RADIO_TYPE_LTE,
            lte = cellSignalStrength.toSnapshot()
        )
    )
}

private fun CellIdentityLte.toSnapshot(): LteCellIdentitySnapshot {
    return LteCellIdentitySnapshot(
        mccString = mccString,
        mncString = mncString,
        ci = ci.cellFieldOrNull(),
        pci = pci.cellFieldOrNull(),
        tac = tac.cellFieldOrNull(),
        earfcn = earfcn.cellFieldOrNull(),
        bandwidth = bandwidth.cellFieldOrNull(),
        operatorAlphaLong = operatorAlphaLong?.toString(),
        operatorAlphaShort = operatorAlphaShort?.toString()
    )
}

private fun CellSignalStrengthLte.toSnapshot(): LteCellSignalStrengthSnapshot {
    return LteCellSignalStrengthSnapshot(
        dbm = dbm.signalFieldOrNull(),
        asuLevel = asuLevel.signalFieldOrNull(),
        level = level.signalFieldOrNull(),
        cqi = cqi.signalFieldOrNull(),
        cqiTableIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) cqiTableIndex.signalFieldOrNull() else null,
        rsrp = rsrp.signalFieldOrNull(),
        rsrq = rsrq.signalFieldOrNull(),
        rssnr = rssnr.signalFieldOrNull(),
        timingAdvance = timingAdvance.signalFieldOrNull(),
        rssi = rssi.signalFieldOrNull()
    )
}

private fun CellInfoWcdma.toSnapshot(): CellInfoSnapshot {
    return CellInfoSnapshot(
        radioType = RADIO_TYPE_WCDMA,
        registered = isRegistered,
        cellConnectionStatus = cellConnectionStatus,
        timestampMillis = timestampMillis.nonNegativeOrNull(),
        identity = CellIdentitySnapshot(
            radioType = RADIO_TYPE_WCDMA,
            wcdma = cellIdentity.toSnapshot()
        ),
        signalStrength = CellSignalStrengthSnapshot(
            radioType = RADIO_TYPE_WCDMA,
            wcdma = cellSignalStrength.toSnapshot()
        )
    )
}

private fun CellIdentityWcdma.toSnapshot(): WcdmaCellIdentitySnapshot {
    return WcdmaCellIdentitySnapshot(
        mccString = mccString,
        mncString = mncString,
        lac = lac.cellFieldOrNull(),
        cid = cid.cellFieldOrNull(),
        psc = psc.cellFieldOrNull(),
        uarfcn = uarfcn.cellFieldOrNull(),
        operatorAlphaLong = operatorAlphaLong?.toString(),
        operatorAlphaShort = operatorAlphaShort?.toString()
    )
}

private fun CellSignalStrengthWcdma.toSnapshot(): WcdmaCellSignalStrengthSnapshot {
    return WcdmaCellSignalStrengthSnapshot(
        dbm = dbm.signalFieldOrNull(),
        asuLevel = asuLevel.signalFieldOrNull(),
        level = level.signalFieldOrNull(),
        ecNo = ecNo.signalFieldOrNull()
    )
}

private fun CellInfoNr.toSnapshot(): CellInfoSnapshot {
    val identity = cellIdentity as? CellIdentityNr ?: return toGenericSnapshot()
    val signalStrength = cellSignalStrength as? CellSignalStrengthNr ?: return toGenericSnapshot()
    return CellInfoSnapshot(
        radioType = RADIO_TYPE_NR,
        registered = isRegistered,
        cellConnectionStatus = cellConnectionStatus,
        timestampMillis = timestampMillis.nonNegativeOrNull(),
        identity = CellIdentitySnapshot(
            radioType = RADIO_TYPE_NR,
            nr = identity.toSnapshot()
        ),
        signalStrength = CellSignalStrengthSnapshot(
            radioType = RADIO_TYPE_NR,
            nr = signalStrength.toSnapshot()
        )
    )
}

private fun CellIdentityNr.toSnapshot(): NrCellIdentitySnapshot {
    return NrCellIdentitySnapshot(
        mccString = mccString,
        mncString = mncString,
        nci = nci.takeUnless { it == Long.MAX_VALUE },
        pci = pci.cellFieldOrNull(),
        tac = tac.cellFieldOrNull(),
        nrarfcn = nrarfcn.cellFieldOrNull(),
        bands = bands.take(MAX_NR_BANDS),
        operatorAlphaLong = operatorAlphaLong?.toString(),
        operatorAlphaShort = operatorAlphaShort?.toString()
    )
}

private fun CellSignalStrengthNr.toSnapshot(): NrCellSignalStrengthSnapshot {
    return NrCellSignalStrengthSnapshot(
        dbm = dbm.signalFieldOrNull(),
        asuLevel = asuLevel.signalFieldOrNull(),
        level = level.signalFieldOrNull(),
        csiRsrp = csiRsrp.signalFieldOrNull(),
        csiRsrq = csiRsrq.signalFieldOrNull(),
        csiSinr = csiSinr.signalFieldOrNull(),
        ssRsrp = ssRsrp.signalFieldOrNull(),
        ssRsrq = ssRsrq.signalFieldOrNull(),
        ssSinr = ssSinr.signalFieldOrNull()
    )
}

private fun CellInfoTdscdma.toSnapshot(): CellInfoSnapshot {
    return CellInfoSnapshot(
        radioType = RADIO_TYPE_TDSCDMA,
        registered = isRegistered,
        cellConnectionStatus = cellConnectionStatus,
        timestampMillis = timestampMillis.nonNegativeOrNull(),
        identity = CellIdentitySnapshot(
            radioType = RADIO_TYPE_TDSCDMA,
            tdscdma = cellIdentity.toSnapshot()
        ),
        signalStrength = CellSignalStrengthSnapshot(
            radioType = RADIO_TYPE_TDSCDMA,
            tdscdma = cellSignalStrength.toSnapshot()
        )
    )
}

private fun CellIdentityTdscdma.toSnapshot(): TdscdmaCellIdentitySnapshot {
    return TdscdmaCellIdentitySnapshot(
        mccString = mccString,
        mncString = mncString,
        lac = lac.cellFieldOrNull(),
        cid = cid.cellFieldOrNull(),
        cpid = cpid.cellFieldOrNull(),
        uarfcn = uarfcn.cellFieldOrNull(),
        operatorAlphaLong = operatorAlphaLong?.toString(),
        operatorAlphaShort = operatorAlphaShort?.toString()
    )
}

private fun CellSignalStrengthTdscdma.toSnapshot(): TdscdmaCellSignalStrengthSnapshot {
    return TdscdmaCellSignalStrengthSnapshot(
        dbm = dbm.signalFieldOrNull(),
        asuLevel = asuLevel.signalFieldOrNull(),
        level = level.signalFieldOrNull(),
        rscp = rscp.signalFieldOrNull()
    )
}

private fun CellInfoCdma.toSnapshot(): CellInfoSnapshot {
    return CellInfoSnapshot(
        radioType = RADIO_TYPE_CDMA,
        registered = isRegistered,
        cellConnectionStatus = cellConnectionStatus,
        timestampMillis = timestampMillis.nonNegativeOrNull(),
        identity = CellIdentitySnapshot(
            radioType = RADIO_TYPE_CDMA,
            cdma = cellIdentity.toSnapshot()
        ),
        signalStrength = CellSignalStrengthSnapshot(
            radioType = RADIO_TYPE_CDMA,
            cdma = cellSignalStrength.toSnapshot()
        )
    )
}

private fun CellIdentityCdma.toSnapshot(): CdmaCellIdentitySnapshot {
    return CdmaCellIdentitySnapshot(
        baseStationId = basestationId.cellFieldOrNull(),
        baseStationLatitude = latitude.cellFieldOrNull(),
        baseStationLongitude = longitude.cellFieldOrNull(),
        systemId = systemId.cellFieldOrNull(),
        networkId = networkId.cellFieldOrNull(),
        operatorAlphaLong = operatorAlphaLong?.toString(),
        operatorAlphaShort = operatorAlphaShort?.toString()
    )
}

private fun CellSignalStrengthCdma.toSnapshot(): CdmaCellSignalStrengthSnapshot {
    return CdmaCellSignalStrengthSnapshot(
        dbm = dbm.signalFieldOrNull(),
        asuLevel = asuLevel.signalFieldOrNull(),
        level = level.signalFieldOrNull(),
        cdmaDbm = cdmaDbm.signalFieldOrNull(),
        cdmaEcio = cdmaEcio.signalFieldOrNull(),
        evdoDbm = evdoDbm.signalFieldOrNull(),
        evdoEcio = evdoEcio.signalFieldOrNull(),
        evdoSnr = evdoSnr.signalFieldOrNull()
    )
}

private fun CellInfo.toGenericSnapshot(): CellInfoSnapshot {
    val fields = linkedMapOf(
        "registered" to runCatching { isRegistered.toString() }.getOrNull(),
        "cellConnectionStatus" to runCatching { cellConnectionStatus.toString() }.getOrNull(),
        "timestampMillis" to runCatching { timestampMillis.toString() }.getOrNull()
    ).filterValues { it != null }

    return CellInfoSnapshot(
        radioType = RADIO_TYPE_UNKNOWN,
        registered = runCatching { isRegistered }.getOrDefault(false),
        cellConnectionStatus = runCatching { cellConnectionStatus }.getOrNull(),
        timestampMillis = runCatching { timestampMillis.nonNegativeOrNull() }.getOrNull(),
        identity = CellIdentitySnapshot(
            radioType = RADIO_TYPE_UNKNOWN,
            generic = GenericCellRecordSnapshot(
                className = javaClass.name,
                fields = fields
            )
        ),
        signalStrength = CellSignalStrengthSnapshot(
            radioType = RADIO_TYPE_UNKNOWN,
            generic = GenericCellSignalStrengthSnapshot(
                className = javaClass.name,
                dbm = null,
                asuLevel = null,
                level = null,
                fields = emptyMap()
            )
        )
    )
}

@Suppress("DEPRECATION")
private fun NeighboringCellInfo.toSnapshotOrNull(): NeighboringCellInfoSnapshot? {
    return runCatching {
        val snapshot = NeighboringCellInfoSnapshot(
            radioType = networkType.toRadioType(),
            networkType = networkType.cellFieldOrNull(),
            cid = cid.cellFieldOrNull(),
            lac = lac.cellFieldOrNull(),
            psc = psc.cellFieldOrNull(),
            rssi = rssi.signalFieldOrNull()
        )
        snapshot.withParcelMetadata(toParcelMetadata(SignalBaselineCodec.allowedNeighboringCellInfoParcelClassNames))
    }.getOrNull()
}

private fun WifiInfo.toSnapshot(): WifiInfoSnapshot {
    val normalizedSsid = ssid.normalizedWifiSsid()
    val snapshot = WifiInfoSnapshot(
        ssid = normalizedSsid,
        ssidBytesBase64 = normalizedSsid?.ssidBytesBase64(),
        bssid = bssid.nullIfBlank(),
        rssi = rssi.signalFieldOrNull(),
        networkId = networkId.nonNegativeOrNull(),
        frequencyMhz = frequency.nonNegativeOrNull(),
        linkSpeedMbps = linkSpeed.nonNegativeOrNull(),
        rxLinkSpeedMbps = rxLinkSpeedMbps.nonNegativeOrNull(),
        txLinkSpeedMbps = txLinkSpeedMbps.nonNegativeOrNull(),
        wifiStandard = wifiStandard.nonNegativeOrNull(),
        currentSecurityType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) currentSecurityType.nonNegativeOrNull() else null,
        subscriptionId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) subscriptionId.nonNegativeOrNull() else null
    )
    return snapshot.withParcelMetadata(toParcelMetadata(SignalBaselineCodec.allowedWifiInfoParcelClassNames))
}

@Suppress("UNCHECKED_CAST", "DEPRECATION")
private fun TelephonyManager.legacyNeighboringCellInfo(): List<NeighboringCellInfo> {
    val result = runCatching {
        javaClass.getMethod("getNeighboringCellInfo").invoke(this)
    }.getOrNull() as? List<*>
    return result?.filterIsInstance<NeighboringCellInfo>().orEmpty()
}

@Suppress("DEPRECATION")
private fun ScanResult.toSnapshot(): ScanResultSnapshot {
    val normalizedSsid = SSID.normalizedWifiSsid()
    val snapshot = ScanResultSnapshot(
        ssid = normalizedSsid,
        ssidBytesBase64 = normalizedSsid?.ssidBytesBase64(),
        bssid = BSSID.nullIfBlank(),
        capabilities = capabilities.nullIfBlank(),
        level = level.signalFieldOrNull(),
        frequencyMhz = frequency.nonNegativeOrNull(),
        channelWidth = channelWidth.nonNegativeOrNull(),
        centerFreq0Mhz = centerFreq0.nonNegativeOrNull(),
        centerFreq1Mhz = centerFreq1.nonNegativeOrNull(),
        timestampMicros = timestamp.nonNegativeOrNull(),
        wifiStandard = wifiStandard.nonNegativeOrNull(),
        is80211mcResponder = is80211mcResponder
    )
    return snapshot.withParcelMetadata(toParcelMetadata(SignalBaselineCodec.allowedWifiScanResultParcelClassNames))
}

private fun CellInfoSnapshot.withParcelMetadata(parcelMetadata: ParcelMetadata?): CellInfoSnapshot {
    return copy(
        parcelBase64 = parcelMetadata?.base64,
        parcelClassName = parcelMetadata?.className,
        parcelByteCount = parcelMetadata?.byteCount,
        parcelSdkInt = parcelMetadata?.sdkInt,
        parcelBuildFingerprint = parcelMetadata?.buildFingerprint
    )
}

private fun NeighboringCellInfoSnapshot.withParcelMetadata(parcelMetadata: ParcelMetadata?): NeighboringCellInfoSnapshot {
    return copy(
        parcelBase64 = parcelMetadata?.base64,
        parcelClassName = parcelMetadata?.className,
        parcelByteCount = parcelMetadata?.byteCount,
        parcelSdkInt = parcelMetadata?.sdkInt,
        parcelBuildFingerprint = parcelMetadata?.buildFingerprint
    )
}

private fun WifiInfoSnapshot.withParcelMetadata(parcelMetadata: ParcelMetadata?): WifiInfoSnapshot {
    return copy(
        parcelBase64 = parcelMetadata?.base64,
        parcelClassName = parcelMetadata?.className,
        parcelByteCount = parcelMetadata?.byteCount,
        parcelSdkInt = parcelMetadata?.sdkInt,
        parcelBuildFingerprint = parcelMetadata?.buildFingerprint
    )
}

private fun ScanResultSnapshot.withParcelMetadata(parcelMetadata: ParcelMetadata?): ScanResultSnapshot {
    return copy(
        parcelBase64 = parcelMetadata?.base64,
        parcelClassName = parcelMetadata?.className,
        parcelByteCount = parcelMetadata?.byteCount,
        parcelSdkInt = parcelMetadata?.sdkInt,
        parcelBuildFingerprint = parcelMetadata?.buildFingerprint
    )
}

private fun Parcelable.toParcelMetadata(allowedClassNames: Set<String>): ParcelMetadata? {
    val className = javaClass.name
    if (className !in allowedClassNames) return null

    val parcel = Parcel.obtain()
    return try {
        writeToParcel(parcel, 0)
        val bytes = parcel.marshall()
        if (bytes.isEmpty() || bytes.size > SignalBaselineCodec.MAX_PARCEL_BLOB_BYTES) {
            null
        } else {
            ParcelMetadata(
                base64 = Base64.getEncoder().encodeToString(bytes),
                className = className,
                byteCount = bytes.size,
                sdkInt = Build.VERSION.SDK_INT,
                buildFingerprint = Build.FINGERPRINT
            )
        }
    } catch (throwable: Throwable) {
        null
    } finally {
        parcel.recycle()
    }
}

private fun Int.toRadioType(): String {
    return when (this) {
        TelephonyManager.NETWORK_TYPE_GPRS,
        TelephonyManager.NETWORK_TYPE_EDGE,
        TelephonyManager.NETWORK_TYPE_GSM,
        TelephonyManager.NETWORK_TYPE_IDEN -> RADIO_TYPE_GSM
        TelephonyManager.NETWORK_TYPE_UMTS,
        TelephonyManager.NETWORK_TYPE_HSDPA,
        TelephonyManager.NETWORK_TYPE_HSUPA,
        TelephonyManager.NETWORK_TYPE_HSPA,
        TelephonyManager.NETWORK_TYPE_HSPAP -> RADIO_TYPE_WCDMA
        TelephonyManager.NETWORK_TYPE_LTE,
        TelephonyManager.NETWORK_TYPE_IWLAN -> RADIO_TYPE_LTE
        TelephonyManager.NETWORK_TYPE_TD_SCDMA -> RADIO_TYPE_TDSCDMA
        TelephonyManager.NETWORK_TYPE_NR -> RADIO_TYPE_NR
        TelephonyManager.NETWORK_TYPE_CDMA,
        TelephonyManager.NETWORK_TYPE_1xRTT,
        TelephonyManager.NETWORK_TYPE_EVDO_0,
        TelephonyManager.NETWORK_TYPE_EVDO_A,
        TelephonyManager.NETWORK_TYPE_EVDO_B,
        TelephonyManager.NETWORK_TYPE_EHRPD -> RADIO_TYPE_CDMA
        else -> RADIO_TYPE_UNKNOWN
    }
}

private fun Int.cellFieldOrNull(): Int? {
    return takeUnless { it == CellInfo.UNAVAILABLE || it == Int.MAX_VALUE || it == -1 }
}

private fun Int.signalFieldOrNull(): Int? {
    return takeUnless { it == CellInfo.UNAVAILABLE || it == Int.MAX_VALUE }
}

private fun Int.nonNegativeOrNull(): Int? {
    return takeUnless { it < 0 || it == Int.MAX_VALUE }
}

private fun Long.nonNegativeOrNull(): Long? {
    return takeUnless { it < 0L || it == Long.MAX_VALUE }
}

private fun String?.normalizedWifiSsid(): String? {
    val value = nullIfBlank()?.takeUnless { it == UNKNOWN_WIFI_SSID } ?: return null
    return if (value.length >= 2 && value.first() == '"' && value.last() == '"') {
        value.substring(1, value.lastIndex)
    } else {
        value
    }
}

private fun String?.nullIfBlank(): String? {
    return this?.takeIf { it.isNotBlank() }
}

private fun String.ssidBytesBase64(): String? {
    val bytes = toByteArray(Charsets.UTF_8)
    return if (bytes.size <= MAX_WIFI_SSID_BYTES) Base64.getEncoder().encodeToString(bytes) else null
}

private data class ParcelMetadata(
    val base64: String,
    val className: String,
    val byteCount: Int,
    val sdkInt: Int,
    val buildFingerprint: String
)

private const val MAX_LOCATION_EXTRAS = 32
private const val MAX_NR_BANDS = 32
private const val MAX_WIFI_SSID_BYTES = 32
private const val UNKNOWN_WIFI_SSID = "<unknown ssid>"
